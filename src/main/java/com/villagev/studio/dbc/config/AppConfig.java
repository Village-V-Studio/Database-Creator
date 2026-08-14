package com.villagev.studio.dbc.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;

public class AppConfig {
    @JsonProperty("time-zone")
    private String timeZone = "UTC";

    @JsonProperty("log-level")
    private String logLevel = "info";

    @JsonProperty("backup-type")
    private String backupType = "local";

    @JsonProperty("password")
    private char[] password = new char[0];

    @JsonProperty("google-drive")
    private GoogleDrive googleDrive = new GoogleDrive();

    @JsonProperty("server")
    private ServerConfig server = new ServerConfig();

    @JsonProperty("database")
    private Map<String, DatabaseConfig> databases = new HashMap<>();

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    public String getBackupType() {
        return backupType;
    }

    public void setBackupType(String backupType) {
        this.backupType = backupType;
    }

    public char[] getPassword() {
        return password;
    }

    public void setPassword(char[] password) {
        this.password = password;
    }

    public GoogleDrive getGoogleDrive() {
        return googleDrive;
    }

    public void setGoogleDrive(GoogleDrive googleDrive) {
        this.googleDrive = googleDrive;
    }

    public ServerConfig getServer() {
        return server;
    }

    public void setServer(ServerConfig server) {
        this.server = server;
    }

    public Map<String, DatabaseConfig> getDatabases() {
        return databases;
    }

    public void setDatabases(Map<String, DatabaseConfig> databases) {
        this.databases = databases;
    }
}
