package com.villagev.studio.dbc.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DatabaseConfig {
    private String ip = "127.0.0.1";
    private int port;
    private String username = "root";
    private char[] password = "password".toCharArray();

    @JsonProperty("auto-start")
    private boolean autoStart = false;

    @JsonProperty("auto-backup")
    private boolean autoBackup = false;

    @JsonProperty("backup-interval")
    private int backupInterval = 24;

    @JsonProperty("max-local-backups")
    private int maxLocalBackups = 7;

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public char[] getPassword() {
        return password;
    }

    public void setPassword(char[] password) {
        this.password = password;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public boolean isAutoBackup() {
        return autoBackup;
    }

    public void setAutoBackup(boolean autoBackup) {
        this.autoBackup = autoBackup;
    }

    public int getBackupInterval() {
        return backupInterval;
    }

    public void setBackupInterval(int backupInterval) {
        this.backupInterval = backupInterval;
    }

    public int getMaxLocalBackups() {
        return maxLocalBackups;
    }

    public void setMaxLocalBackups(int maxLocalBackups) {
        this.maxLocalBackups = maxLocalBackups;
    }
}
