package com.villagev.studio.dbc.core;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import ch.vorburger.mariadb4j.DB;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;

import com.villagev.studio.dbc.config.DatabaseConfig;

public class DatabaseManager {
    private final Map<String, DB> activeDatabases = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, DatabaseConfig> activeConfigs = new java.util.concurrent.ConcurrentHashMap<>();
    private final File databasesDir = new File("databases");
    private final File binariesDir = new File("mariaDB_binaries");
    private final ScheduledExecutorService watchdogExecutor = Executors.newSingleThreadScheduledExecutor();

    public DatabaseManager() {
        if (!databasesDir.exists()) {
            databasesDir.mkdirs();
        }
        cleanupZombies();
        startWatchdog();
    }

    private void cleanupZombies() {
        ProcessHandle.allProcesses().forEach(p -> {
            p.info().command().ifPresent(cmd -> {
                if (cmd.contains(binariesDir.getName()) && (cmd.endsWith("mysqld") || cmd.endsWith("mysqld.exe"))) {
                    System.out.println("Cleaning up orphaned database process from previous run: " + p.pid());
                    p.destroyForcibly();
                }
            });
        });
    }

    private void startWatchdog() {
        watchdogExecutor.scheduleAtFixedRate(() -> {
            for (Map.Entry<String, DatabaseConfig> entry : activeConfigs.entrySet()) {
                String name = entry.getKey();
                DatabaseConfig config = entry.getValue();

                try (Socket socket = new Socket("127.0.0.1", config.getPort())) {
                    socket.getOutputStream().write(new byte[] { 0x01, 0x00, 0x00, 0x00, 0x01 });
                    socket.getOutputStream().flush();
                } catch (IOException e) {
                    System.err.println("\n[WATCHDOG] WARNING: Database '" + name + "' on port " + config.getPort()
                            + " has crashed or hung!");
                    System.err.println("[WATCHDOG] Attempting to restart database '" + name + "'...");

                    stopDatabase(name);

                    boolean success = startDatabase(name, config);
                    if (success) {
                        System.out.println("[WATCHDOG] SUCCESS: Database '" + name + "' was successfully recovered!");
                    } else {
                        System.err.println("[WATCHDOG] ERROR: FAILED to recover database '" + name
                                + "'. It will remain disabled.");
                    }
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
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
            System.err.println("Invalid database name: '" + name
                    + "'. Only alphanumeric characters, hyphens, and underscores are allowed.");
            return false;
        }

        try {
            System.out.println("Starting database: " + name + " on port " + config.getPort() + "...");

            DBConfigurationBuilder configBuilder = DBConfigurationBuilder.newBuilder();
            configBuilder.setPort(config.getPort());

            File dataDir = new File(databasesDir, name);

            boolean isNewDb = !dataDir.exists()
                    || (dataDir.isDirectory() && dataDir.list() != null && dataDir.list().length == 0);

            configBuilder.setDataDir(dataDir);
            configBuilder.setBaseDir(binariesDir);
            configBuilder.setSecurityDisabled(false);

            DB db = DB.newEmbeddedDB(configBuilder.build());
            db.start();

            if (!config.getUsername().equals("root") || config.getPassword().length > 0) {
                if (!config.getUsername().matches("^[a-zA-Z0-9_]+$")) {
                    throw new IllegalArgumentException(
                            "Username must only contain alphanumeric characters and underscores.");
                }

                String safeUser = config.getUsername();
                String safePass = new String(config.getPassword()).replace("\\", "\\\\").replace("'", "''");

                String bindIp = config.getIp() != null && !config.getIp().isEmpty() ? config.getIp() : "127.0.0.1";
                if (!bindIp.matches("^[a-zA-Z0-9.-]+$") && !bindIp.equals("%")) {
                    throw new IllegalArgumentException("Invalid IP address or hostname in config.");
                }

                String sql = "CREATE DATABASE IF NOT EXISTS `" + name + "`; " +
                        "CREATE USER IF NOT EXISTS '" + safeUser + "'@'" + bindIp + "' IDENTIFIED BY '" + safePass
                        + "'; " +
                        "GRANT ALL PRIVILEGES ON `" + name + "`.* TO '" + safeUser + "'@'" + bindIp + "'; " +
                        "ALTER USER IF EXISTS '" + safeUser + "'@'" + bindIp + "' IDENTIFIED BY '" + safePass + "'; " +
                        "CREATE USER IF NOT EXISTS '" + safeUser + "'@'localhost' IDENTIFIED BY '" + safePass + "'; " +
                        "GRANT ALL PRIVILEGES ON `" + name + "`.* TO '" + safeUser + "'@'localhost'; " +
                        "ALTER USER IF EXISTS '" + safeUser + "'@'localhost' IDENTIFIED BY '" + safePass + "'; " +
                        "FLUSH PRIVILEGES;";
                try {
                    String connectionPassword = (!isNewDb && config.getUsername().equals("root"))
                            ? new String(config.getPassword())
                            : null;
                    db.run(sql, "root", connectionPassword, null);
                } catch (Exception e) {
                    System.err.println(
                            "Warning: Failed to update user privileges. If you changed the password in config, you might need to update it manually in the database.");
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
        DatabaseConfig config = activeConfigs.remove(name);

        if (db != null) {
            try {
                System.out.println("Stopping database: " + name + "...");

                if (config != null) {
                    String osExt = System.getProperty("os.name").toLowerCase().contains("win") ? ".exe" : "";
                    File mysqladmin = new File(new File(binariesDir, "bin"), "mysqladmin" + osExt);
                    if (mysqladmin.exists()) {
                        java.util.List<String> cmd = new java.util.ArrayList<>();
                        cmd.add(mysqladmin.getAbsolutePath());
                        cmd.add("-u");
                        cmd.add(config.getUsername());
                        if (config.getPassword().length > 0) {
                            cmd.add("-p" + new String(config.getPassword()));
                        }
                        cmd.add("--port=" + config.getPort());
                        cmd.add("-h");
                        cmd.add("127.0.0.1");
                        cmd.add("shutdown");

                        ProcessBuilder pb = new ProcessBuilder(cmd);
                        Process p = pb.start();
                        p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                    }
                }

                db.stop();
                System.out.println("Database " + name + " stopped.");
            } catch (Exception e) {
                System.err.println("Failed to stop database " + name + ": " + e.getMessage());
            }
        }
    }

    public void stopAll() {
        watchdogExecutor.shutdownNow();
        for (String name : new HashMap<>(activeDatabases).keySet()) {
            stopDatabase(name);
        }
    }
}
