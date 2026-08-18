package com.villagev.studio.dbc.config;

import java.io.File;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.toml.TomlMapper;

public class Manager {
    private static final String CONFIG_FILE_NAME = "config.toml";
    private final ObjectMapper tomlMapper;
    private volatile AppConfig currentConfig;

    public Manager() {
        this.tomlMapper = new TomlMapper();
    }

    public void loadConfig() {
        File configFile = new File(CONFIG_FILE_NAME);
        if (!configFile.exists()) {
            createDefaultConfig(configFile);
            secureFile(configFile);
        }

        try {
            this.currentConfig = tomlMapper.readValue(configFile, AppConfig.class);
            System.out.println("Configuration loaded successfully.");
        } catch (Exception e) {
            System.err.println("Failed to load configuration file: " + e.getMessage());
            if (this.currentConfig == null) {
                System.err.println("Critical error during initial startup. Shutting down.");
                System.exit(1);
            } else {
                System.err.println("Keeping the previous configuration.");
            }
        }
    }

    private void createDefaultConfig(File configFile) {
        String defaultToml = "time-zone = \"UTC\"\n" +
                "log-level = \"warn\"\n" +
                "logs = true\n" +
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

    private void secureFile(File file) {
        try {
            java.nio.file.Path path = file.toPath();
            if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                java.util.Set<java.nio.file.attribute.PosixFilePermission> perms = new java.util.HashSet<>();
                perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_READ);
                perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
                java.nio.file.Files.setPosixFilePermissions(path, perms);
            } else if (path.getFileSystem().supportedFileAttributeViews().contains("acl")) {
                java.nio.file.attribute.AclFileAttributeView aclView = java.nio.file.Files.getFileAttributeView(path,
                        java.nio.file.attribute.AclFileAttributeView.class);
                java.nio.file.attribute.UserPrincipal owner = java.nio.file.Files.getOwner(path);
                java.nio.file.attribute.AclEntry entry = java.nio.file.attribute.AclEntry.newBuilder()
                        .setType(java.nio.file.attribute.AclEntryType.ALLOW)
                        .setPrincipal(owner)
                        .setPermissions(
                                java.nio.file.attribute.AclEntryPermission.READ_DATA,
                                java.nio.file.attribute.AclEntryPermission.WRITE_DATA,
                                java.nio.file.attribute.AclEntryPermission.APPEND_DATA,
                                java.nio.file.attribute.AclEntryPermission.READ_NAMED_ATTRS,
                                java.nio.file.attribute.AclEntryPermission.WRITE_NAMED_ATTRS,
                                java.nio.file.attribute.AclEntryPermission.EXECUTE,
                                java.nio.file.attribute.AclEntryPermission.READ_ATTRIBUTES,
                                java.nio.file.attribute.AclEntryPermission.WRITE_ATTRIBUTES,
                                java.nio.file.attribute.AclEntryPermission.DELETE,
                                java.nio.file.attribute.AclEntryPermission.READ_ACL,
                                java.nio.file.attribute.AclEntryPermission.SYNCHRONIZE)
                        .build();
                aclView.setAcl(java.util.Collections.singletonList(entry));
            } else {
                file.setReadable(false, false);
                file.setReadable(true, true);
                file.setWritable(false, false);
                file.setWritable(true, true);
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to set strict file permissions for " + file.getName());
        }
    }
}
