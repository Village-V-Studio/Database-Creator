package com.villagev.studio.dbc.config;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.toml.TomlMapper;

import java.io.File;

public class ConfigManager {
    private static final String CONFIG_FILE_NAME = "config.toml";
    private final ObjectMapper tomlMapper;
    private AppConfig currentConfig;

    public ConfigManager() {
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
        AppConfig defaultConfig = new AppConfig();
        defaultConfig.setDriveToken("");
        defaultConfig.setClientId("");
        defaultConfig.setClientSecret("");
        defaultConfig.setDriveFolder("DBC_Backups/");
        defaultConfig.setPassword("");

        DatabaseConfig exampleDb = new DatabaseConfig();
        exampleDb.setIp("127.0.0.1");
        exampleDb.setPort(3000);
        exampleDb.setUsername("root");
        exampleDb.setPassword("password");
        exampleDb.setAutoStart(false);
        exampleDb.setAutoBackup(false);
        exampleDb.setBackupInterval(24);

        defaultConfig.getDatabases().put("example", exampleDb);

        try {
            tomlMapper.writeValue(configFile, defaultConfig);
            System.out.println("Generated default configuration file: " + CONFIG_FILE_NAME);
        } catch (Exception e) {
            System.err.println("Failed to generate default configuration: " + e.getMessage());
        }
    }

    public AppConfig getConfig() {
        return currentConfig;
    }
}
