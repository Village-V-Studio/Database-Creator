package com.villagev.studio.dbc.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GoogleDrive {
    @JsonProperty("rclone-path")
    private String rclonePath = "rclone";

    @JsonProperty("drive-token")
    private String driveToken = "";

    @JsonProperty("client-id")
    private String clientId = "";

    @JsonProperty("client-secret")
    private String clientSecret = "";

    @JsonProperty("drive-folder")
    private String driveFolder = "DBC_Backups/";

    public String getRclonePath() {
        return rclonePath;
    }

    public void setRclonePath(String rclonePath) {
        this.rclonePath = rclonePath;
    }

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
}
