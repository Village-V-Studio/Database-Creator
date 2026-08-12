package com.villagev.studio.dbc.core;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import com.villagev.studio.dbc.config.AppConfig;
import com.villagev.studio.dbc.config.DatabaseConfig;

public class BackupManager {
    private final DatabaseManager dbManager;
    private final File backupsDir = new File("backups");

    public BackupManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        if (!backupsDir.exists()) {
            backupsDir.mkdirs();
        }
    }

    public void runBackup(String name, DatabaseConfig dbConfig, AppConfig appConfig) {
        if (!dbConfig.isAutoBackup()) {
            System.out.println("Skipping backup for " + name + " (auto-backup is disabled).");
            return;
        }

        System.out.println("Starting backup for database: " + name);

        dbManager.stopDatabase(name);

        try {
            File sourceFolder = new File("databases", name);
            if (!sourceFolder.exists()) {
                System.out.println("No data found for " + name + ", skipping backup.");
                return;
            }

            String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            File zipArchive = new File(backupsDir, name + "_" + dateStr + ".zip");

            ZipParameters zipParameters = new ZipParameters();
            zipParameters.setEncryptFiles(true);
            zipParameters.setEncryptionMethod(EncryptionMethod.AES);
            zipParameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);

            try (ZipFile zipFile = new ZipFile(zipArchive, appConfig.getPassword().toCharArray())) {
                zipFile.addFolder(sourceFolder, zipParameters);
            }

            System.out.println("Successfully created encrypted backup: " + zipArchive.getName());

            uploadToGoogleDrive(zipArchive, appConfig);

        } catch (Exception e) {
            System.err.println("Failed to create backup for " + name + ": " + e.getMessage());
        } finally {
            dbManager.startDatabase(name, dbConfig);
        }
    }

    public void backupAllActive(AppConfig appConfig) {
        System.out.println("Running global backup process...");
        for (Map.Entry<String, DatabaseConfig> entry : appConfig.getDatabases().entrySet()) {
            runBackup(entry.getKey(), entry.getValue(), appConfig);
        }
    }

    private void uploadToGoogleDrive(File zipArchive, AppConfig appConfig) {
        if (appConfig.getDriveToken().isEmpty() || appConfig.getClientId().isEmpty()) {
            System.out.println("Google Drive (Rclone) settings are incomplete. Skipping upload.");
            return;
        }

        System.out.println("Uploading " + zipArchive.getName() + " to Google Drive...");
        try {
            File rcloneConf = File.createTempFile("rclone_", ".conf");
            try (FileWriter writer = new FileWriter(rcloneConf)) {
                writer.write("[gdrive]\n");
                writer.write("type = drive\n");
                writer.write("client_id = " + appConfig.getClientId() + "\n");
                writer.write("client_secret = " + appConfig.getClientSecret() + "\n");
                writer.write("token = " + appConfig.getDriveToken() + "\n");
            }

            ProcessBuilder pb = new ProcessBuilder(
                    "rclone", "copy", zipArchive.getAbsolutePath(), "gdrive:" + appConfig.getDriveFolder(), "--config",
                    rcloneConf.getAbsolutePath());
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                System.out.println("Successfully uploaded to Google Drive!");
            } else {
                System.err.println("Rclone failed with exit code: " + exitCode);
            }

            rcloneConf.delete();

        } catch (Exception e) {
            System.err.println("Failed to upload to Google Drive: " + e.getMessage());
        }
    }
}
