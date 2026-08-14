package com.villagev.studio.dbc.core;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.villagev.studio.dbc.config.AppConfig;
import com.villagev.studio.dbc.config.Manager;
import com.villagev.studio.dbc.config.DatabaseConfig;

public class Scheduler {
    private final Manager configManager;
    private final BackupManager backupManager;
    private final ScheduledExecutorService executorService;
    private final java.util.concurrent.ExecutorService backupExecutor;
    private final Map<String, LocalDateTime> lastBackupTimes = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> activeBackups = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public Scheduler(Manager configManager, BackupManager backupManager) {
        this.configManager = configManager;
        this.backupManager = backupManager;
        this.executorService = Executors.newSingleThreadScheduledExecutor();
        this.backupExecutor = Executors.newFixedThreadPool(2);
    }

    public void start() {
        executorService.scheduleAtFixedRate(this::checkBackups, 1, 1, TimeUnit.MINUTES);
    }

    public void stop() {
        executorService.shutdown();
        backupExecutor.shutdown();
    }

    private void checkBackups() {
        try {
            AppConfig config = configManager.getConfig();
            if (config == null)
                return;

            LocalDateTime now = LocalDateTime.now();

            for (Map.Entry<String, DatabaseConfig> entry : config.getDatabases().entrySet()) {
                String dbName = entry.getKey();
                DatabaseConfig dbConfig = entry.getValue();

                if (dbConfig == null || !dbConfig.isAutoBackup())
                    continue;

                LocalDateTime lastBackup = lastBackupTimes.get(dbName);
                if (lastBackup == null || ChronoUnit.MINUTES.between(lastBackup, now) >= dbConfig.getBackupInterval() * 60L) {
                    if (activeBackups.add(dbName)) {
                        backupExecutor.submit(() -> {
                            try {
                                backupManager.runBackup(dbName, dbConfig, config, false);
                                lastBackupTimes.put(dbName, LocalDateTime.now());
                            } finally {
                                activeBackups.remove(dbName);
                            }
                        });
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error during scheduled backup check: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
