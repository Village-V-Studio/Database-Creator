package com.villagev.studio.dbc;

import java.util.TimeZone;

public class Main {
    public static void main(String[] args) {
        com.villagev.studio.dbc.config.Manager configManager = new com.villagev.studio.dbc.config.Manager();
        configManager.loadConfig();
        com.villagev.studio.dbc.config.AppConfig config = configManager.getConfig();

        com.villagev.studio.dbc.core.LogManager.init(config.isLogs());

        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", config.getLogLevel().toLowerCase());
        try {
            java.time.ZoneId zoneId = java.time.ZoneId.of(config.getTimeZone());
            TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
        } catch (java.time.DateTimeException e) {
            System.err.println("[CRITICAL] Invalid time-zone in config.toml: '" + config.getTimeZone() + "'");
            System.err.println("Please specify a valid IANA time zone (e.g. 'Europe/Kyiv' or 'UTC').");
            System.exit(1);
        }

        System.out.println("Starting Database Creator...");
        System.out.println("Current timezone set to: " + TimeZone.getDefault().getID());
        System.out.println("Log level set to: " + config.getLogLevel().toUpperCase());

        checkRclone(config);

        com.villagev.studio.dbc.core.DatabaseManager dbManager = new com.villagev.studio.dbc.core.DatabaseManager();
        com.villagev.studio.dbc.core.BackupManager backupManager = new com.villagev.studio.dbc.core.BackupManager(
                dbManager);
        com.villagev.studio.dbc.core.Scheduler scheduler = new com.villagev.studio.dbc.core.Scheduler(configManager,
                backupManager);

        for (java.util.Map.Entry<String, com.villagev.studio.dbc.config.DatabaseConfig> entry : config.getDatabases()
                .entrySet()) {
            if (entry.getValue().isAutoStart()) {
                dbManager.startDatabase(entry.getKey(), entry.getValue());
            }
        }

        scheduler.start();

        com.villagev.studio.dbc.console.Manager consoleManager = new com.villagev.studio.dbc.console.Manager(
                configManager, dbManager, backupManager);
        consoleManager.start();

        scheduler.stop();
        System.out.println("Goodbye!");
    }

    private static void checkRclone(com.villagev.studio.dbc.config.AppConfig config) {
        String backupType = config.getBackupType();
        if (!"google-drive".equalsIgnoreCase(backupType) && !"gdrive".equalsIgnoreCase(backupType)) {
            return;
        }

        String rclonePath = config.getGoogleDrive().getRclonePath();
        java.io.File rcloneFile = new java.io.File(rclonePath);
        boolean isNameValid = rcloneFile.getName().equals("rclone") || rcloneFile.getName().equals("rclone.exe");
        boolean isPathValid = rclonePath.equals("rclone") || rclonePath.equals("rclone.exe") || (rcloneFile.isAbsolute() && rcloneFile.exists());
        
        if (!isNameValid || !isPathValid) {
            System.out.println("\n[WARNING] Security: rclone-path must be 'rclone', 'rclone.exe', or a valid absolute path to the rclone executable.");
            System.out.println("[WARNING] Cloud backups to Google Drive will be disabled.\n");
            return;
        }

        try {
            Process process = new ProcessBuilder(rclonePath, "version")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (finished) {
                System.out.println("Rclone detected! Cloud backups are available.");
            } else {
                process.destroyForcibly();
                throw new RuntimeException("rclone check timed out");
            }
        } catch (Exception e) {
            System.out.println("\n[WARNING] 'rclone' is not installed or not found in PATH!");
            System.out.println("[WARNING] Cloud backups to Google Drive will not work.");
            System.out.println("[WARNING] Local encrypted .zip backups will still be created.\n");
        }
    }
}
