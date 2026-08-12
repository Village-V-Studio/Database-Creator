package com.villagev.studio.dbc.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AppConfig {
    private String driveToken = "";
    private String clientId = "";
    @JsonProperty("client-secret")
    private String clientSecret = "";

    @JsonProperty("drive-folder")
    private String driveFolder = "DBC_Backups/";

    @JsonProperty("time-zone")
    private String timeZone = "UTC";

    @JsonProperty("log-level")
    private String logLevel = "info";

    private String password = "";
    @JsonProperty("database")
    private java.util.Map<String, DatabaseConfig> databases = new java.util.HashMap<>();

    public String getDriveToken() {
        return driveToken;
    }

    public void setDriveToken(String driveToken) {
        this.driveToken = driveToken;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getDriveFolder() {
        return driveFolder;
    }

    public void setDriveFolder(String driveFolder) {
        this.driveFolder = driveFolder;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public java.util.Map<String, DatabaseConfig> getDatabases() {
        return databases;
    }

    public void setDatabases(java.util.Map<String, DatabaseConfig> databases) {
        this.databases = databases;
    }

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
}
