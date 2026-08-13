package com.villagev.studio.dbc.config;

import java.io.File;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.toml.TomlMapper;

public class Manager {
    private static final String CONFIG_FILE_NAME = "config.toml";
    private final ObjectMapper tomlMapper;
    private AppConfig currentConfig;

    public Manager() {
        this.tomlMapper = new TomlMapper();
    }

    public void loadConfig() {
        File configFile = new File(CONFIG_FILE_NAME);
        if (!configFile.exists()) {
            createDefaultConfig(configFile);
        }

        try {
            this.currentConfig = tomlMapper.readValue(configFile, AppConfig.class);
            System.out.println("Configuration loaded successfully.");
        } catch (Exception e) {
            System.err.println("Failed to load configuration file: " + e.getMessage());
            this.currentConfig = new AppConfig();
        }
    }

    private void createDefaultConfig(File configFile) {
        String defaultToml = "time-zone = \"UTC\"\n" +
                "log-level = \"info\"\n" +
                "backup-type = \"local\"\n" +
                "password = \"\"\n\n" +
                "[server]\n" +
                "ip = \"\"\n" +
                "port = 22\n" +
                "username = \"\"\n" +
                "password = \"\"\n" +
                "remote-folder = \"/backups\"\n\n" +
                "[google-drive]\n" +
                "drive-token = \"\"\n" +
                "client-id = \"\"\n" +
                "client-secret = \"\"\n" +
                "drive-folder = \"DBC_Backups/\"\n\n" +
                "[database.example]\n" +
                "ip = \"127.0.0.1\"\n" +
                "port = 3000\n" +
                "username = \"root\"\n" +
                "password = \"password\"\n" +
                "auto-start = false\n" +
                "auto-backup = false\n" +
                "backup-interval = 24\n";

        try {
            java.nio.file.Files.writeString(configFile.toPath(), defaultToml, java.nio.charset.StandardCharsets.UTF_8);
            System.out.println("Generated default configuration file: " + CONFIG_FILE_NAME);
        } catch (Exception e) {
            System.err.println("Failed to generate default configuration: " + e.getMessage());
        }
    }

    public AppConfig getConfig() {
        return currentConfig;
    }
}
