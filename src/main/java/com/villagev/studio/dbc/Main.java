package com.villagev.studio.dbc;

import java.util.TimeZone;

public class Main {
    public static void main(String[] args) {
        com.villagev.studio.dbc.config.Manager configManager = new com.villagev.studio.dbc.config.Manager();
        configManager.loadConfig();
        com.villagev.studio.dbc.config.AppConfig config = configManager.getConfig();

        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", config.getLogLevel().toLowerCase());
        TimeZone.setDefault(TimeZone.getTimeZone(config.getTimeZone()));

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

        try {
            Process process = new ProcessBuilder("rclone", "version").start();
            process.waitFor();
            System.out.println("Rclone detected! Cloud backups are available.");
        } catch (Exception e) {
            System.out.println("\n[WARNING] 'rclone' is not installed or not found in PATH!");
            System.out.println("[WARNING] Cloud backups to Google Drive will not work.");
            System.out.println("[WARNING] Local encrypted .zip backups will still be created.\n");
        }
    }
}
