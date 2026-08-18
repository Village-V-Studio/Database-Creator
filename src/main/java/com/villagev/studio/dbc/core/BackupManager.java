package com.villagev.studio.dbc.core;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import com.villagev.studio.dbc.config.AppConfig;
import com.villagev.studio.dbc.config.DatabaseConfig;

public class BackupManager {
    private final DatabaseManager dbManager;
    private final File backupsDir = new File("backups");
    private final java.util.concurrent.ConcurrentHashMap<String, Object> backupLocks = new java.util.concurrent.ConcurrentHashMap<>();

    public BackupManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        if (!backupsDir.exists()) {
            backupsDir.mkdirs();
        }
    }

    public void runBackup(String name, DatabaseConfig dbConfig, AppConfig appConfig, boolean force) {
        if (!force && !dbConfig.isAutoBackup()) {
            System.out.println("Skipping backup for " + name + " (auto-backup is disabled).");
            return;
        }

        System.out.println("Starting backup for database: " + name);

        File zipArchive = null;
        Object lock = new Object();
        Object existingLock = backupLocks.putIfAbsent(name, lock);
        if (existingLock != null) {
            lock = existingLock;
        }

        synchronized (lock) {
            try {
                boolean wasRunning = dbManager.isRunning(name);
                if (!wasRunning) {
                    System.out.println(
                            "Database " + name + " is not running. Logical backup requires a running database.");
                    return;
                }

                try {
                    String os = System.getProperty("os.name").toLowerCase();
                    String mysqldumpName = os.contains("win") ? "mysqldump.exe" : "mysqldump";
                    File binariesDir = dbManager.getBinariesDir();
                    File mysqldumpExe = new File(new File(binariesDir, "bin"), mysqldumpName);

                    if (!mysqldumpExe.exists()) {
                        mysqldumpExe = new File(binariesDir, mysqldumpName);
                    }

                    if (!mysqldumpExe.exists()) {
                        System.err.println("Could not find " + mysqldumpName + " in " + binariesDir.getAbsolutePath()
                                + ". Cannot perform logical backup.");
                        return;
                    }

                    String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
                    File sqlDump = new File(backupsDir, name + "_" + dateStr + ".sql");

                    String bindIp = dbConfig.getIp() != null && !dbConfig.getIp().isEmpty() ? dbConfig.getIp()
                            : "127.0.0.1";

                    File myCnf = File.createTempFile("my", ".cnf");
                    myCnf.setReadable(false, false);
                    myCnf.setReadable(true, true);
                    myCnf.deleteOnExit();

                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(myCnf)) {
                        fos.write("[client]\npassword=".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        java.nio.ByteBuffer pwBuf = java.nio.charset.StandardCharsets.UTF_8
                                .encode(java.nio.CharBuffer.wrap(dbConfig.getPassword()));
                        byte[] pwBytes = new byte[pwBuf.remaining()];
                        pwBuf.get(pwBytes);
                        fos.write(pwBytes);
                        java.util.Arrays.fill(pwBytes, (byte) 0);

                        ProcessBuilder pb = new ProcessBuilder(
                                mysqldumpExe.getAbsolutePath(),
                                "--defaults-file=" + myCnf.getAbsolutePath(),
                                "-h", bindIp,
                                "-P", String.valueOf(dbConfig.getPort()),
                                "-u", dbConfig.getUsername().isEmpty() ? "root" : dbConfig.getUsername(),
                                name);

                        pb.redirectOutput(sqlDump);
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

                        Process process = pb.start();
                        boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.MINUTES);

                        if (!finished) {
                            process.destroyForcibly();
                            throw new RuntimeException("mysqldump timed out after 5 minutes.");
                        }

                        if (process.exitValue() != 0) {
                            throw new RuntimeException("mysqldump failed with exit code " + process.exitValue());
                        }
                    } finally {
                        myCnf.delete();
                    }

                    boolean hasPassword = appConfig.getPassword() != null && appConfig.getPassword().length > 0;
                    try {
                        zipArchive = new File(backupsDir, name + "_" + dateStr + ".zip");

                        ZipParameters zipParameters = new ZipParameters();

                        if (hasPassword) {
                            zipParameters.setEncryptFiles(true);
                            zipParameters.setEncryptionMethod(EncryptionMethod.AES);
                            zipParameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
                        }

                        try (ZipFile zipFile = hasPassword ? new ZipFile(zipArchive, appConfig.getPassword())
                                : new ZipFile(zipArchive)) {
                            zipFile.addFile(sqlDump, zipParameters);
                        }
                    } finally {
                        sqlDump.delete();
                    }

                    System.out.println(
                            "Successfully created " + (hasPassword ? "encrypted " : "") + "logical backup: "
                                    + zipArchive.getName());

                } catch (Exception e) {
                    System.err.println("Failed to create backup for " + name + ": " + e.getMessage());
                }

                if (zipArchive != null && zipArchive.exists()) {
                    rotateLocalBackups(name, dbConfig.getMaxLocalBackups());
                    uploadToGoogleDrive(zipArchive, appConfig);
                    uploadToServer(zipArchive, appConfig);
                }
            } finally {
                backupLocks.remove(name, lock);
            }
        }
    }

    private void rotateLocalBackups(String dbName, int maxBackups) {
        if (maxBackups <= 0)
            return;

        File[] allBackups = backupsDir
                .listFiles((dir, filename) -> filename.startsWith(dbName + "_") && filename.endsWith(".zip"));
        if (allBackups == null || allBackups.length <= maxBackups)
            return;

        java.util.Arrays.sort(allBackups, java.util.Comparator.comparingLong(File::lastModified));

        int filesToDelete = allBackups.length - maxBackups;
        for (int i = 0; i < filesToDelete; i++) {
            if (allBackups[i].delete()) {
                System.out.println("Rotated (deleted) old backup: " + allBackups[i].getName());
            }
        }
    }

    public void backupAllActive(AppConfig appConfig, boolean force) {
        System.out.println("Running global backup process...");
        for (Map.Entry<String, DatabaseConfig> entry : appConfig.getDatabases().entrySet()) {
            runBackup(entry.getKey(), entry.getValue(), appConfig, force);
        }
    }

    private void uploadToGoogleDrive(File zipArchive, AppConfig appConfig) {
        if (!"google-drive".equalsIgnoreCase(appConfig.getBackupType())
                && !"gdrive".equalsIgnoreCase(appConfig.getBackupType())) {
            return;
        }

        if (appConfig.getGoogleDrive().getDriveToken().isEmpty()
                || appConfig.getGoogleDrive().getClientId().isEmpty()) {
            System.out.println("Google Drive (Rclone) settings are incomplete. Skipping upload.");
            return;
        }

        String rclonePath = appConfig.getGoogleDrive().getRclonePath();
        java.io.File rcloneFile = new java.io.File(rclonePath);
        boolean isNameValid = rcloneFile.getName().equals("rclone") || rcloneFile.getName().equals("rclone.exe");
        boolean isPathValid = rclonePath.equals("rclone") || rclonePath.equals("rclone.exe")
                || (rcloneFile.isAbsolute() && rcloneFile.exists());

        if (!isNameValid || !isPathValid) {
            System.err.println(
                    "Security Error: rclone path must be 'rclone', 'rclone.exe', or a valid absolute path. Upload aborted.");
            return;
        }

        System.out.println("Uploading " + zipArchive.getName() + " to Google Drive...");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    rclonePath, "copy", zipArchive.getAbsolutePath(),
                    "google-drive:" + appConfig.getGoogleDrive().getDriveFolder());

            Map<String, String> env = pb.environment();
            env.put("RCLONE_CONFIG_GOOGLE_DRIVE_TYPE", "drive");
            env.put("RCLONE_CONFIG_GOOGLE_DRIVE_CLIENT_ID", appConfig.getGoogleDrive().getClientId());
            env.put("RCLONE_CONFIG_GOOGLE_DRIVE_CLIENT_SECRET", appConfig.getGoogleDrive().getClientSecret());
            env.put("RCLONE_CONFIG_GOOGLE_DRIVE_TOKEN", appConfig.getGoogleDrive().getDriveToken());

            pb.inheritIO();
            Process process = pb.start();
            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Rclone upload timed out after 10 minutes.");
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                System.out.println("Successfully uploaded to Google Drive!");
            } else {
                System.err.println("Rclone failed with exit code: " + exitCode);
            }

        } catch (Exception e) {
            System.err.println("Failed to upload to Google Drive: " + e.getMessage());
        }
    }

    private void uploadToServer(File zipArchive, AppConfig appConfig) {
        if (!"server".equalsIgnoreCase(appConfig.getBackupType())) {
            return;
        }

        if (appConfig.getServer().getIp().isEmpty() || appConfig.getServer().getUsername().isEmpty()) {
            System.out.println("Server settings (IP or Username) are incomplete. Skipping SFTP upload.");
            return;
        }

        System.out.println("Uploading " + zipArchive.getName() + " to remote server via SFTP...");

        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp sftpChannel = null;

        try {
            session = jsch.getSession(appConfig.getServer().getUsername(), appConfig.getServer().getIp(),
                    appConfig.getServer().getPort());

            java.nio.ByteBuffer byteBuffer = java.nio.charset.StandardCharsets.UTF_8
                    .encode(java.nio.CharBuffer.wrap(appConfig.getServer().getPassword()));
            byte[] passwordBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(passwordBytes);
            session.setPassword(passwordBytes);
            java.util.Arrays.fill(passwordBytes, (byte) 0);

            String knownHostsPath = System.getProperty("user.home") + File.separator + ".ssh" + File.separator
                    + "known_hosts";
            File knownHostsFile = new File(knownHostsPath);
            if (!knownHostsFile.exists()) {
                if (knownHostsFile.getParentFile() != null) {
                    knownHostsFile.getParentFile().mkdirs();
                }
                knownHostsFile.createNewFile();
            }
            jsch.setKnownHosts(knownHostsPath);

            System.out.println("Connecting to server " + appConfig.getServer().getIp() + "...");
            session.connect(10000);

            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();

            String remoteFolder = appConfig.getServer().getRemoteFolder();
            if (!remoteFolder.endsWith("/")) {
                remoteFolder += "/";
            }

            try {
                sftpChannel.stat(remoteFolder);
            } catch (SftpException e) {
                if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                    System.out.println("Remote folder does not exist, creating: " + remoteFolder);
                    sftpChannel.mkdir(remoteFolder);
                } else {
                    throw e;
                }
            }

            String remoteFilePath = remoteFolder + zipArchive.getName();
            sftpChannel.put(zipArchive.getAbsolutePath(), remoteFilePath);

            System.out.println("Successfully uploaded to remote server: " + remoteFilePath);

        } catch (com.jcraft.jsch.JSchException e) {
            if (e.getMessage() != null && e.getMessage().contains("reject HostKey")) {
                System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                System.err.println("[SECURITY] SFTP Connection Rejected: Unknown Host Key!");
                System.err.println("To prevent MITM attacks, you must manually trust the server.");
                System.err.println("Please run this command on your machine:");
                System.err.println("ssh-keyscan " + appConfig.getServer().getIp() + " >> ~/.ssh/known_hosts");
                System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            } else {
                System.err.println("Failed to upload to remote server: " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("Failed to upload to remote server: " + e.getMessage());
        } finally {
            if (sftpChannel != null) {
                sftpChannel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }
}
