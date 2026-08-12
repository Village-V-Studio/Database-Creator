package com.villagev.studio.dbc.core;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.villagev.studio.dbc.config.AppConfig;
import com.villagev.studio.dbc.config.ConfigManager;
import com.villagev.studio.dbc.config.DatabaseConfig;

public class Scheduler {
    private final ConfigManager configManager;
    private final BackupManager backupManager;
    private final ScheduledExecutorService executorService;
    private final Map<String, LocalDateTime> lastBackupTimes = new HashMap<>();

    public Scheduler(ConfigManager configManager, BackupManager backupManager) {
        this.configManager = configManager;
        this.backupManager = backupManager;
        this.executorService = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        executorService.scheduleAtFixedRate(this::checkBackups, 1, 1, TimeUnit.MINUTES);
    }

    public void stop() {
        executorService.shutdown();
    }

    private void checkBackups() {
        AppConfig config = configManager.getConfig();
        if (config == null)
            return;

        LocalDateTime now = LocalDateTime.now();

        for (Map.Entry<String, DatabaseConfig> entry : config.getDatabases().entrySet()) {
            String dbName = entry.getKey();
            DatabaseConfig dbConfig = entry.getValue();

            if (!dbConfig.isAutoBackup())
                continue;

            LocalDateTime lastBackup = lastBackupTimes.get(dbName);
            if (lastBackup == null) {
                backupManager.runBackup(dbName, dbConfig, config);
                lastBackupTimes.put(dbName, LocalDateTime.now());
            } else {
                long hoursSinceLastBackup = ChronoUnit.HOURS.between(lastBackup, now);
                if (hoursSinceLastBackup >= dbConfig.getBackupInterval()) {
                    backupManager.runBackup(dbName, dbConfig, config);
                    lastBackupTimes.put(dbName, LocalDateTime.now());
                }
            }
        }
    }
}
