package com.villagev.studio.dbc.core;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import ch.vorburger.mariadb4j.DB;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;

import com.villagev.studio.dbc.config.DatabaseConfig;

public class DatabaseManager {
    private final Map<String, DB> activeDatabases = new HashMap<>();
    private final Map<String, DatabaseConfig> activeConfigs = new HashMap<>();
    private final File databasesDir = new File("databases");
    private final File binariesDir = new File("mariaDB_binaries");

    public DatabaseManager() {
        if (!databasesDir.exists()) {
            databasesDir.mkdirs();
        }
    }

    public void startDatabase(String name, DatabaseConfig config) {
        if (activeDatabases.containsKey(name)) {
            System.out.println("Database " + name + " is already running.");
            return;
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

            if (!config.getUsername().equals("root") || !config.getPassword().isEmpty()) {
                String sql = "CREATE USER IF NOT EXISTS '" + config.getUsername() + "'@'%' IDENTIFIED BY '"
                        + config.getPassword() + "'; " +
                        "GRANT ALL PRIVILEGES ON *.* TO '" + config.getUsername() + "'@'%'; " +
                        "CREATE USER IF NOT EXISTS '" + config.getUsername() + "'@'localhost' IDENTIFIED BY '"
                        + config.getPassword() + "'; " +
                        "GRANT ALL PRIVILEGES ON *.* TO '" + config.getUsername() + "'@'localhost'; " +
                        "ALTER USER IF EXISTS '" + config.getUsername() + "'@'localhost' IDENTIFIED BY '"
                        + config.getPassword() + "'; " +
                        "FLUSH PRIVILEGES;";
                try {
                    String connectionPassword = isNewDb ? null : config.getPassword();
                    db.run(sql, "root", connectionPassword, null);
                } catch (Exception e) {
                    System.err.println("Warning: Failed to update user privileges. If you changed the password in config, you might need to update it manually in the database.");
                }
            }

            activeDatabases.put(name, db);
            activeConfigs.put(name, config);
            System.out.println("Database " + name + " successfully started!");

        } catch (Exception e) {
            System.err.println("Failed to start database " + name + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stopDatabase(String name) {
        DB db = activeDatabases.remove(name);
        DatabaseConfig config = activeConfigs.remove(name);

        if (db != null) {
            try {
                System.out.println("Stopping database: " + name + "...");

                try {
                    String user = config != null ? config.getUsername() : "root";
                    String pass = config != null ? config.getPassword() : null;
                    db.run("SHUTDOWN;", user, pass, null);
                    Thread.sleep(1000);
                } catch (Exception ignore) {
                }

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

    public void deleteDatabase(String name) {
        stopDatabase(name);
        File dataDir = new File(databasesDir, name);
        if (dataDir.exists()) {
            File oldDir = new File(databasesDir, name + ".old");
            if (dataDir.renameTo(oldDir)) {
                System.out.println("Database " + name + " has been renamed to .old");
            } else {
                System.err.println("Failed to rename database " + name + " to .old");
            }
        }
    }
}
