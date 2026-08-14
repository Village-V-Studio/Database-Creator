package com.villagev.studio.dbc.core;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import ch.vorburger.mariadb4j.DB;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;

import com.villagev.studio.dbc.config.DatabaseConfig;

public class DatabaseManager {
    private final Map<String, DB> activeDatabases = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, DatabaseConfig> activeConfigs = new java.util.concurrent.ConcurrentHashMap<>();
    private final File databasesDir = new File("databases");
    private final File binariesDir = new File("mariaDB_binaries");

    public DatabaseManager() {
        if (!databasesDir.exists()) {
            databasesDir.mkdirs();
        }
    }

    public boolean isRunning(String name) {
        return activeDatabases.containsKey(name);
    }

    public File getBinariesDir() {
        return binariesDir.getAbsoluteFile();
    }

    public synchronized boolean startDatabase(String name, DatabaseConfig config) {
        if (activeDatabases.containsKey(name)) {
            System.out.println("Database " + name + " is already running.");
            return true;
        }
        if (!name.matches("^[a-zA-Z0-9_-]+$")) {
            System.err.println("Invalid database name: '" + name + "'. Only alphanumeric characters, hyphens, and underscores are allowed.");
            return false;
        }

        try {
            System.out.println("Starting database: " + name + " on port " + config.getPort() + "...");

            DBConfigurationBuilder configBuilder = DBConfigurationBuilder.newBuilder();
            configBuilder.setPort(config.getPort());

            File dataDir = new File(databasesDir, name);

            boolean isNewDb = !dataDir.exists() || (dataDir.isDirectory() && dataDir.list() != null && dataDir.list().length == 0);

            configBuilder.setDataDir(dataDir);
            configBuilder.setBaseDir(binariesDir);
            configBuilder.setSecurityDisabled(false);

            DB db = DB.newEmbeddedDB(configBuilder.build());
            db.start();

            if (!config.getUsername().equals("root") || config.getPassword().length > 0) {
                if (!config.getUsername().matches("^[a-zA-Z0-9_]+$")) {
                    throw new IllegalArgumentException("Username must only contain alphanumeric characters and underscores.");
                }
                
                String safeUser = config.getUsername();
                String safePass = new String(config.getPassword()).replace("\\", "\\\\").replace("'", "''");
                
                String bindIp = config.getIp() != null && !config.getIp().isEmpty() ? config.getIp() : "127.0.0.1";
                if (!bindIp.matches("^[a-zA-Z0-9.-]+$") && !bindIp.equals("%")) {
                    throw new IllegalArgumentException("Invalid IP address or hostname in config.");
                }
                
                String sql = "CREATE DATABASE IF NOT EXISTS `" + name + "`; " +
                        "CREATE USER IF NOT EXISTS '" + safeUser + "'@'" + bindIp + "' IDENTIFIED BY '" + safePass + "'; " +
                        "GRANT ALL PRIVILEGES ON `" + name + "`.* TO '" + safeUser + "'@'" + bindIp + "'; " +
                        "ALTER USER IF EXISTS '" + safeUser + "'@'" + bindIp + "' IDENTIFIED BY '" + safePass + "'; " +
                        "CREATE USER IF NOT EXISTS '" + safeUser + "'@'localhost' IDENTIFIED BY '" + safePass + "'; " +
                        "GRANT ALL PRIVILEGES ON `" + name + "`.* TO '" + safeUser + "'@'localhost'; " +
                        "ALTER USER IF EXISTS '" + safeUser + "'@'localhost' IDENTIFIED BY '" + safePass + "'; " +
                        "FLUSH PRIVILEGES;";
                try {
                    String connectionPassword = (!isNewDb && config.getUsername().equals("root")) ? new String(config.getPassword()) : null;
                    db.run(sql, "root", connectionPassword, null);
                } catch (Exception e) {
                    System.err.println("Warning: Failed to update user privileges. If you changed the password in config, you might need to update it manually in the database.");
                }
            }

            activeDatabases.put(name, db);
            activeConfigs.put(name, config);
            System.out.println("Database " + name + " successfully started!");
            return true;

        } catch (Exception e) {
            System.err.println("Failed to start database " + name + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public synchronized void stopDatabase(String name) {
        if (!activeDatabases.containsKey(name)) {
            System.out.println("Database " + name + " is not running (already stopped).");
            return;
        }

        DB db = activeDatabases.remove(name);
        activeConfigs.remove(name);

        if (db != null) {
            try {
                System.out.println("Stopping database: " + name + "...");
                db.stop();
                System.out.println("Database " + name + " stopped.");
            } catch (Exception e) {
                System.err.println("Failed to stop database " + name + ": " + e.getMessage());
            }
        }
    }

    public void stopAll() {
        for (String name : new HashMap<>(activeDatabases).keySet()) {
            stopDatabase(name);
        }
    }
}
