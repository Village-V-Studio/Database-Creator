package com.villagev.studio.dbc.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ServerConfig {
    private String ip = "";
    private int port = 22;
    private String username = "";
    private char[] password = new char[0];

    @JsonProperty("remote-folder")
    private String remoteFolder = "/backups";

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

    public String getRemoteFolder() {
        return remoteFolder;
    }

    public void setRemoteFolder(String remoteFolder) {
        this.remoteFolder = remoteFolder;
    }
}
