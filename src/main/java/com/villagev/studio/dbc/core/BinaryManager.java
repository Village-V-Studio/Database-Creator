package com.villagev.studio.dbc.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class BinaryManager {
    private static volatile boolean customBinariesMode = false;

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    public static boolean isLinux() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("linux") || os.contains("nix") || os.contains("nux");
    }

    public static boolean isArm64() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        return arch.matches("^(aarch64|arm64|armv8.*)$");
    }

    public static boolean isX86_64() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        return arch.matches("^(x86_64|amd64|x64)$");
    }

    public static boolean isCustomBinariesNeeded() {
        return isLinux() && isArm64();
    }

    public static boolean isCustomBinariesMode() {
        return customBinariesMode || isCustomBinariesNeeded();
    }

    public static File resolveBinariesDir(File preferredDir) {
        if (preferredDir == null) {
            preferredDir = new File("mariaDB_binaries");
        }

        if (canExecuteInDirectory(preferredDir)) {
            return preferredDir;
        }

        File userHomeDir = new File(System.getProperty("user.home", "."));
        File fallbackDir = new File(userHomeDir, ".dbc/mariaDB_binaries");
        System.out.println("\n[BinaryManager] NOTICE: Directory '" + preferredDir.getAbsolutePath()
                + "' does not permit binary execution (filesystem is mounted with noexec or NTFS permissions).");
        System.out.println("[BinaryManager] Relocating MariaDB engine binaries to: " + fallbackDir.getAbsolutePath());
        System.out.println("[BinaryManager] Database tables will still be safely stored in the project's 'databases/' folder.\n");

        fallbackDir.mkdirs();
        return fallbackDir;
    }

    public static boolean canExecuteInDirectory(File dir) {
        if (isWindows()) {
            return true;
        }
        File testDir = dir;
        if (!testDir.exists()) {
            testDir = testDir.getParentFile() != null ? testDir.getParentFile() : new File(".");
        }
        if (!testDir.exists()) {
            return true;
        }

        File testScript = new File(testDir, ".dbc_exec_check_" + System.currentTimeMillis() + ".sh");
        try {
            Files.writeString(testScript.toPath(), "#!/bin/sh\nexit 0\n");
            testScript.setExecutable(true, false);

            try {
                Set<PosixFilePermission> perms = Files.getPosixFilePermissions(testScript.toPath());
                Set<PosixFilePermission> newPerms = new HashSet<>(perms);
                newPerms.add(PosixFilePermission.OWNER_EXECUTE);
                Files.setPosixFilePermissions(testScript.toPath(), newPerms);
            } catch (Exception ignored) {
            }

            if (!testScript.canExecute()) {
                return false;
            }

            Process p = new ProcessBuilder(testScript.getAbsolutePath())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean finished = p.waitFor(2, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            try {
                if (testScript.exists()) {
                    testScript.delete();
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static boolean validateBinaries(File binariesDir) {
        if (binariesDir == null || !binariesDir.exists() || !binariesDir.isDirectory()) {
            return false;
        }

        File binDir = new File(binariesDir, "bin");
        if (!binDir.exists() || !binDir.isDirectory()) {
            return false;
        }

        String ext = isWindows() ? ".exe" : "";
        String[] candidates = { "my_print_defaults" + ext, "mariadbd" + ext, "mysqld" + ext };
        File testExe = null;
        for (String name : candidates) {
            File f = new File(binDir, name);
            if (f.exists()) {
                testExe = f;
                break;
            }
        }

        if (testExe == null) {
            return false;
        }

        ensureExecutablePermissions(binariesDir);

        try {
            ProcessBuilder pb = new ProcessBuilder(testExe.getAbsolutePath(), "--help");
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
            }
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        if (isLinux()) {
            File shareDir = new File(binariesDir, "share");
            if (!shareDir.exists()) {
                return false;
            }
            boolean hasHelpTables = new File(shareDir, "fill_help_tables.sql").exists()
                    || new File(shareDir, "mysql/fill_help_tables.sql").exists()
                    || new File(shareDir, "mariadb/fill_help_tables.sql").exists();
            if (!hasHelpTables) {
                return false;
            }
            if (isCustomBinariesNeeded()) {
                boolean hasMysqlSubdir = new File(shareDir, "mysql/fill_help_tables.sql").exists();
                boolean hasSbinDaemon = new File(binariesDir, "sbin/mariadbd").exists() || new File(binariesDir, "sbin/mysqld").exists();
                if (!hasMysqlSubdir || !hasSbinDaemon) {
                    return false;
                }
            }
        }

        return true;
    }

    public static synchronized boolean ensureBinaries(File binariesDir) {
        if (binariesDir == null) {
            return false;
        }

        if (validateBinaries(binariesDir)) {
            ensureExecutablePermissions(binariesDir);
            customBinariesMode = true;
            return true;
        }

        if (binariesDir.exists() && binariesDir.list() != null && binariesDir.list().length > 0) {
            System.out.println("[BinaryManager] Existing binaries in " + binariesDir.getName()
                    + " are invalid or incompatible. Cleaning up binaries folder...");
            cleanBinariesDir(binariesDir);
        }

        if (!isCustomBinariesNeeded()) {
            String osDir = isWindows() ? "winx64" : (isMac() ? "osx" : "linux");
            String classpathLocation = "ch/vorburger/mariadb4j/mariadb-11.4.5/" + osDir;
            try {
                System.out.println("[BinaryManager] Preparing bundled MariaDB engine...");
                binariesDir.mkdirs();
                ch.vorburger.mariadb4j.Util.extractFromClasspathToFile(classpathLocation, binariesDir);
                ensureExecutablePermissions(binariesDir);
                customBinariesMode = true;
                return validateBinaries(binariesDir);
            } catch (Exception e) {
                System.err.println("[BinaryManager] Warning: failed to extract bundled binaries: " + e.getMessage());
                return false;
            }
        }

        System.out.println("[BinaryManager] Detected Linux ARM64 (aarch64) environment.");
        System.out.println("[BinaryManager] Checking for system-installed MariaDB engine...");

        if (linkSystemMariaDB(binariesDir)) {
            ensureExecutablePermissions(binariesDir);
            customBinariesMode = true;
            System.out.println("[BinaryManager] Successfully configured native MariaDB from system!");
            return true;
        }

        if (tryInstallSystemMariaDB(binariesDir)) {
            ensureExecutablePermissions(binariesDir);
            customBinariesMode = true;
            System.out.println("[BinaryManager] Successfully installed and configured MariaDB!");
            return true;
        }

        System.err.println("\n[BinaryManager] MariaDB engine was not found on this Linux ARM64 system.");
        System.err.println("[BinaryManager] To run Database Creator on Linux ARM64, please install MariaDB:");
        System.err.println("       sudo apt update && sudo apt install -y mariadb-server mariadb-client");
        System.err.println("[BinaryManager] Once installed, restart DBC. It will automatically detect and link the engine!\n");

        return false;
    }

    public static void ensureExecutablePermissions(File binariesDir) {
        if (isWindows() || binariesDir == null || !binariesDir.exists()) {
            return;
        }

        File binDir = new File(binariesDir, "bin");
        File sbinDir = new File(binariesDir, "sbin");
        File libexecDir = new File(binariesDir, "libexec");
        File scriptsDir = new File(binariesDir, "scripts");

        File[] dirs = { binDir, sbinDir, libexecDir, scriptsDir };
        for (File dir : dirs) {
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile()) {
                            f.setExecutable(true, false);
                            f.setReadable(true, false);
                            try {
                                Set<PosixFilePermission> perms = Files.getPosixFilePermissions(f.toPath());
                                Set<PosixFilePermission> newPerms = new HashSet<>(perms);
                                newPerms.add(PosixFilePermission.OWNER_EXECUTE);
                                newPerms.add(PosixFilePermission.GROUP_EXECUTE);
                                newPerms.add(PosixFilePermission.OTHERS_EXECUTE);
                                Files.setPosixFilePermissions(f.toPath(), newPerms);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            }
        }

        File scriptInstall = new File(scriptsDir, "mariadb-install-db");
        File binInstall = new File(binDir, "mariadb-install-db");
        File sbinInstall = new File(sbinDir, "mariadb-install-db");
        if (scriptInstall.exists() && !binInstall.exists()) {
            safeLinkOrCopy(scriptInstall, binInstall);
        } else if (binInstall.exists() && !scriptInstall.exists()) {
            safeLinkOrCopy(binInstall, scriptInstall);
        }
        if (binInstall.exists() && !sbinInstall.exists()) {
            safeLinkOrCopy(binInstall, sbinInstall);
        }
    }

    public static boolean linkSystemMariaDB(File binariesDir) {
        String[] serverLocations = {
            "/usr/bin/mariadbd", "/usr/sbin/mariadbd",
            "/usr/libexec/mariadbd", "/usr/libexec/mysqld",
            "/usr/bin/mysqld", "/usr/sbin/mysqld",
            "/usr/local/bin/mariadbd", "/usr/local/sbin/mariadbd",
            "/usr/local/bin/mysqld", "/usr/local/sbin/mysqld"
        };

        File serverExe = null;
        for (String loc : serverLocations) {
            File f = new File(loc);
            if (f.exists() && f.canExecute()) {
                serverExe = f;
                break;
            }
        }

        if (serverExe == null) {
            serverExe = findExecutableInPath("mariadbd");
            if (serverExe == null) {
                serverExe = findExecutableInPath("mysqld");
            }
        }

        if (serverExe == null) {
            return false;
        }

        File binDir = new File(binariesDir, "bin");
        File sbinDir = new File(binariesDir, "sbin");
        File libexecDir = new File(binariesDir, "libexec");
        File scriptsDir = new File(binariesDir, "scripts");
        File shareDir = new File(binariesDir, "share");
        File libDir = new File(binariesDir, "lib");

        binDir.mkdirs();
        sbinDir.mkdirs();
        libexecDir.mkdirs();
        scriptsDir.mkdirs();

        if (Files.isSymbolicLink(shareDir.toPath())) {
            try {
                Files.delete(shareDir.toPath());
            } catch (Exception ignored) {
            }
        }
        shareDir.mkdirs();

        safeLinkOrCopy(serverExe, new File(binDir, "mariadbd"));
        safeLinkOrCopy(serverExe, new File(binDir, "mysqld"));
        safeLinkOrCopy(serverExe, new File(sbinDir, "mariadbd"));
        safeLinkOrCopy(serverExe, new File(sbinDir, "mysqld"));
        safeLinkOrCopy(serverExe, new File(libexecDir, "mariadbd"));
        safeLinkOrCopy(serverExe, new File(libexecDir, "mysqld"));

        File printDefaults = findExecutable("my_print_defaults", "/usr/bin/my_print_defaults", "/usr/sbin/my_print_defaults", "/usr/libexec/my_print_defaults");
        if (printDefaults != null) {
            safeLinkOrCopy(printDefaults, new File(binDir, "my_print_defaults"));
            safeLinkOrCopy(printDefaults, new File(sbinDir, "my_print_defaults"));
            safeLinkOrCopy(printDefaults, new File(libexecDir, "my_print_defaults"));
        }

        File admin = findExecutable("mysqladmin", "/usr/bin/mysqladmin", "/usr/bin/mariadb-admin");
        if (admin != null) {
            safeLinkOrCopy(admin, new File(binDir, "mysqladmin"));
            safeLinkOrCopy(admin, new File(binDir, "mariadb-admin"));
            safeLinkOrCopy(admin, new File(sbinDir, "mysqladmin"));
            safeLinkOrCopy(admin, new File(sbinDir, "mariadb-admin"));
        }

        File dump = findExecutable("mariadb-dump", "/usr/bin/mariadb-dump", "/usr/bin/mysqldump");
        if (dump != null) {
            safeLinkOrCopy(dump, new File(binDir, "mysqldump"));
            safeLinkOrCopy(dump, new File(binDir, "mariadb-dump"));
        }

        File client = findExecutable("mariadb", "/usr/bin/mariadb", "/usr/bin/mysql");
        if (client != null) {
            safeLinkOrCopy(client, new File(binDir, "mariadb"));
            safeLinkOrCopy(client, new File(binDir, "mysql"));
        }

        File installDb = findExecutable("mariadb-install-db", "/usr/bin/mariadb-install-db", "/usr/bin/mysql_install_db", "/usr/scripts/mariadb-install-db");
        if (installDb != null) {
            safeLinkOrCopy(installDb, new File(scriptsDir, "mariadb-install-db"));
            safeLinkOrCopy(installDb, new File(scriptsDir, "mysql_install_db"));
            safeLinkOrCopy(installDb, new File(binDir, "mariadb-install-db"));
            safeLinkOrCopy(installDb, new File(binDir, "mysql_install_db"));
            safeLinkOrCopy(installDb, new File(sbinDir, "mariadb-install-db"));
            safeLinkOrCopy(installDb, new File(sbinDir, "mysql_install_db"));
        }

        File resolveip = findExecutable("resolveip", "/usr/bin/resolveip", "/usr/sbin/resolveip", "/usr/libexec/resolveip");
        if (resolveip != null) {
            safeLinkOrCopy(resolveip, new File(binDir, "resolveip"));
            safeLinkOrCopy(resolveip, new File(sbinDir, "resolveip"));
        }

        File helpTablesSource = null;
        for (String scPath : new String[]{ "/usr/share/mysql", "/usr/share/mariadb", "/usr/local/share/mysql", "/usr/local/share/mariadb" }) {
            File candidate = new File(scPath);
            if (candidate.exists() && candidate.isDirectory()) {
                if (new File(candidate, "fill_help_tables.sql").exists()) {
                    helpTablesSource = candidate;
                    break;
                } else if (helpTablesSource == null) {
                    helpTablesSource = candidate;
                }
            }
        }

        if (helpTablesSource != null) {
            File sysMysql = new File("/usr/share/mysql");
            File sysMariadb = new File("/usr/share/mariadb");

            safeLinkOrCopy(sysMysql.exists() ? sysMysql : helpTablesSource, new File(shareDir, "mysql"));
            safeLinkOrCopy(sysMariadb.exists() ? sysMariadb : helpTablesSource, new File(shareDir, "mariadb"));

            File[] files = helpTablesSource.listFiles();
            if (files != null) {
                for (File f : files) {
                    File target = new File(shareDir, f.getName());
                    if (!target.exists()) {
                        safeLinkOrCopy(f, target);
                    }
                }
            }
        }

        libDir.mkdirs();
        String[] pluginCandidates = {
            "/usr/lib/mysql/plugin",
            "/usr/lib/mariadb/plugin",
            "/usr/lib/aarch64-linux-gnu/mariadb19/plugin",
            "/usr/lib/aarch64-linux-gnu/mariadb20/plugin",
            "/usr/lib/aarch64-linux-gnu/mysql/plugin",
            "/usr/lib/x86_64-linux-gnu/mariadb19/plugin",
            "/usr/lib/x86_64-linux-gnu/mysql/plugin",
            "/usr/lib64/mysql/plugin",
            "/usr/lib64/mariadb/plugin"
        };
        for (String pc : pluginCandidates) {
            File pcf = new File(pc);
            if (pcf.exists() && pcf.isDirectory()) {
                safeLinkOrCopy(pcf, new File(libDir, "plugin"));
                File libMysql = new File(libDir, "mysql");
                libMysql.mkdirs();
                safeLinkOrCopy(pcf, new File(libMysql, "plugin"));
                break;
            }
        }

        ensureExecutablePermissions(binariesDir);
        return validateBinaries(binariesDir);
    }

    private static boolean tryInstallSystemMariaDB(File binariesDir) {
        if (!isLinux()) {
            return false;
        }

        File aptGet = findExecutableInPath("apt-get");
        if (aptGet == null) {
            return false;
        }

        try {
            Process whoami = new ProcessBuilder("id", "-u").start();
            String uid;
            try (InputStream in = whoami.getInputStream()) {
                uid = new String(in.readAllBytes()).trim();
            }
            whoami.waitFor();
            if (!"0".equals(uid)) {
                return false;
            }

            System.out.println("[BinaryManager] Running apt-get to install mariadb-server and mariadb-client...");
            Process update = new ProcessBuilder("apt-get", "update", "-qq").inheritIO().start();
            update.waitFor(2, TimeUnit.MINUTES);

            Process install = new ProcessBuilder("apt-get", "install", "-y", "-qq", "mariadb-server", "mariadb-client").inheritIO().start();
            install.waitFor(5, TimeUnit.MINUTES);

            try {
                new ProcessBuilder("systemctl", "stop", "mariadb").start().waitFor();
                new ProcessBuilder("systemctl", "disable", "mariadb").start().waitFor();
            } catch (Exception ignored) {
            }

            return linkSystemMariaDB(binariesDir);

        } catch (Exception ignored) {
            return false;
        }
    }

    public static void cleanBinariesDir(File binariesDir) {
        if (binariesDir == null || !binariesDir.exists()) {
            return;
        }
        if (binariesDir.getName().equals("databases") || binariesDir.getAbsolutePath().contains("databases")) {
            System.err.println("[BinaryManager] CRITICAL SAFETY: Refusing to delete databases directory!");
            return;
        }
        deleteRecursively(binariesDir);
        binariesDir.mkdirs();
    }

    private static void deleteRecursively(File file) {
        if (Files.isSymbolicLink(file.toPath())) {
            try {
                Files.delete(file.toPath());
            } catch (Exception ignored) {
                file.delete();
            }
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private static void safeLinkOrCopy(File source, File target) {
        try {
            if (target.getParentFile() != null && !target.getParentFile().exists()) {
                target.getParentFile().mkdirs();
            }
            if (target.exists() || Files.isSymbolicLink(target.toPath())) {
                Files.delete(target.toPath());
            }
            try {
                Files.createSymbolicLink(target.toPath(), source.toPath());
            } catch (Exception e) {
                if (source.isDirectory()) {
                    copyDirectory(source.toPath(), target.toPath());
                } else {
                    Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            target.setExecutable(true, false);
            target.setReadable(true, false);
        } catch (Exception e) {
            System.err.println("[BinaryManager] Warning: failed to link/copy " + source + " to " + target + ": " + e.getMessage());
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (var stream = Files.walk(source)) {
            stream.forEach(src -> {
                try {
                    Path dest = target.resolve(source.relativize(src));
                    if (Files.isDirectory(src)) {
                        if (!Files.exists(dest)) {
                            Files.createDirectories(dest);
                        }
                    } else {
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException ignored) {
                }
            });
        }
    }

    private static File findExecutable(String exeName, String... standardPaths) {
        for (String path : standardPaths) {
            File f = new File(path);
            if (f.exists() && f.canExecute()) {
                return f;
            }
        }
        return findExecutableInPath(exeName);
    }

    private static File findExecutableInPath(String exeName) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] paths = pathEnv.split(File.pathSeparator);
            for (String p : paths) {
                File file = new File(p, exeName);
                if (file.exists() && file.canExecute()) {
                    return file;
                }
            }
        }
        return null;
    }
}
