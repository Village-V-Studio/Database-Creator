package com.villagev.studio.dbc.console;

import java.time.Instant;
import java.util.Map;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import com.villagev.studio.dbc.config.AppConfig;
import com.villagev.studio.dbc.config.DatabaseConfig;
import com.villagev.studio.dbc.core.BackupManager;
import com.villagev.studio.dbc.core.DatabaseManager;

public class Manager {
    private final com.villagev.studio.dbc.config.Manager configManager;
    private final DatabaseManager dbManager;
    private final BackupManager backupManager;
    private final java.util.concurrent.ExecutorService manualBackupExecutor;

    private String pendingCommand = null;
    private Instant pendingCommandTime = null;

    public Manager(com.villagev.studio.dbc.config.Manager configManager, DatabaseManager dbManager,
            BackupManager backupManager) {
        this.configManager = configManager;
        this.dbManager = dbManager;
        this.backupManager = backupManager;
        this.manualBackupExecutor = new java.util.concurrent.ThreadPoolExecutor(1, 1,
                0L, java.util.concurrent.TimeUnit.MILLISECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(10),
                new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
    }

    public void start() {
        try {
            Terminal terminal = TerminalBuilder.builder().system(true).build();
            LineReader lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(createCompleter())
                    .build();

            com.villagev.studio.dbc.core.LogManager.setLineReader(lineReader);

            System.out.println("Type 'help' for a list of commands.");

            try {
                while (true) {
                    String line;
                    try {
                        line = lineReader.readLine("> ").trim();
                    } catch (UserInterruptException e) {
                        continue;
                    } catch (org.jline.reader.EndOfFileException e) {
                        System.out.println("\nShutting down Database Creator safely...");
                        break;
                    }

                    if (line.isEmpty())
                        continue;

                    if (!processCommand(line)) {
                        break;
                    }
                }
            } finally {
                dbManager.stopAll();
                manualBackupExecutor.shutdown();
                try {
                    if (!manualBackupExecutor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                        manualBackupExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    manualBackupExecutor.shutdownNow();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean processCommand(String line) {
        if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
            System.out.println("Shutting down Database Creator safely...");
            return false;
        }

        if (line.equalsIgnoreCase("stop")) {
            if (pendingCommand != null && pendingCommand.equals("stop")) {
                if (Instant.now().minusSeconds(5).isBefore(pendingCommandTime)) {
                    pendingCommand = null;
                    System.out.println("Shutting down Database Creator safely...");
                    return false;
                } else {
                    System.out.println("Confirmation expired. Type again to confirm.");
                    pendingCommand = null;
                    return true;
                }
            } else {
                pendingCommand = "stop";
                pendingCommandTime = Instant.now();
                System.out.println("Are you sure? Type 'stop' again within 5 seconds to confirm.");
                return true;
            }
        }

        if (line.equalsIgnoreCase("help") || line.equalsIgnoreCase("?")) {
            System.out.println("Commands:");
            System.out.println("  db enable [name1,name2]  - Start databases");
            System.out.println("  db disable [name1,name2] - Stop databases");
            System.out.println("  db reload [name1,name2]  - Reload config (and restart DBs)");
            System.out.println("  db backup [name1,name2]  - Run manual backup (double tap for all)");
            System.out.println("  db status                - Show all databases state");
            System.out.println("  stop                     - Safely shutdown manager (double tap)");
            System.out.println("  help                     - Show this help menu");
            return true;
        }

        String[] args = line.split("\\s+");
        String action = args[0].toLowerCase();

        if (pendingCommand != null) {
            if (!action.equals(pendingCommand)) {
                pendingCommand = null;
                System.out.println("Confirmation cancelled.");
            }
        }

        AppConfig config = configManager.getConfig();

        String targets = null;
        if (args.length > 1) {
            targets = String.join(",", java.util.Arrays.copyOfRange(args, 1, args.length));
        }

        if (action.equals("backup") && targets == null) {
            if (pendingCommand != null && pendingCommand.equals(action)) {
                if (Instant.now().minusSeconds(5).isBefore(pendingCommandTime)) {
                    pendingCommand = null;
                    manualBackupExecutor.submit(() -> backupManager.backupAllActive(config, true));
                    System.out.println("Global backup process started in background...");
                    return true;
                } else {
                    System.out.println("Confirmation expired. Type again to confirm.");
                    pendingCommand = null;
                    return true;
                }
            } else {
                pendingCommand = action;
                pendingCommandTime = Instant.now();
                System.out.println("Are you sure? Type '" + line + "' again within 5 seconds to confirm.");
                return true;
            }
        } else {
            pendingCommand = null;
        }

        switch (action) {
                case "enable":
                    for (String name : resolveDbNames(targets, config)) {
                        dbManager.startDatabase(name, config.getDatabases().get(name));
                    }
                    break;
                case "disable":
                    for (String name : resolveDbNames(targets, config)) {
                        dbManager.stopDatabase(name);
                    }
                    break;
                case "backup":
                    if (targets != null) {
                        java.util.List<String> names = resolveDbNames(targets, config);
                        manualBackupExecutor.submit(() -> {
                            for (String name : names) {
                                backupManager.runBackup(name, config.getDatabases().get(name), config, true);
                            }
                        });
                        System.out.println("Backup process started in background...");
                    }
                    break;
                case "reload":
                    System.out.println("Reloading configuration...");
                    configManager.loadConfig();
                    AppConfig newConfig = configManager.getConfig();

                    if (targets != null) {
                        for (String name : resolveDbNames(targets, config, newConfig)) {
                            if (!config.getDatabases().containsKey(name)) {
                                System.out.println("Starting new database: " + name + "...");
                                dbManager.startDatabase(name, newConfig.getDatabases().get(name));
                            } else if (!newConfig.getDatabases().containsKey(name)) {
                                System.out.println("Database " + name + " was removed from config. Stopping it.");
                                dbManager.stopDatabase(name);
                            } else if (isDbConfigChanged(config.getDatabases().get(name),
                                    newConfig.getDatabases().get(name))) {
                                System.out.println("Reloading database: " + name + " (config changed)...");
                                dbManager.stopDatabase(name);
                                dbManager.startDatabase(name, newConfig.getDatabases().get(name));
                            } else {
                                System.out.println("Database " + name + " config unchanged. Skipping.");
                            }
                        }
                    } else {
                        for (String oldName : config.getDatabases().keySet()) {
                            if (!newConfig.getDatabases().containsKey(oldName)) {
                                System.out.println("Database " + oldName + " was removed from config. Stopping it.");
                                dbManager.stopDatabase(oldName);
                            }
                        }
                        for (Map.Entry<String, DatabaseConfig> entry : newConfig.getDatabases().entrySet()) {
                            String name = entry.getKey();
                            DatabaseConfig newDbConfig = entry.getValue();

                            boolean isRunning = dbManager.isRunning(name);
                            if (newDbConfig.isAutoStart() || isRunning) {
                                if (!config.getDatabases().containsKey(name)) {
                                    System.out.println("Starting new database: " + name + "...");
                                    dbManager.startDatabase(name, newDbConfig);
                                } else if (isDbConfigChanged(config.getDatabases().get(name), newDbConfig)) {
                                    System.out.println("Reloading database: " + name + " (config changed)...");
                                    dbManager.stopDatabase(name);
                                    dbManager.startDatabase(name, newDbConfig);
                                }
                            }
                        }
                    }
                    break;
                case "status":
                    System.out.println("\n-------------------------------------------------------------");
                    System.out.printf("%-20s %-10s %-10s %-15s\n", "Name", "Status", "Port", "Auto-Backup");
                    System.out.println("-------------------------------------------------------------");
                    for (Map.Entry<String, DatabaseConfig> entry : config.getDatabases().entrySet()) {
                        String name = entry.getKey();
                        DatabaseConfig dbConfig = entry.getValue();
                        String state = dbManager.isRunning(name) ? "[RUNNING]" : "[STOPPED]";
                        System.out.printf("%-20s %-10s %-10s %-15s\n", name, state, dbConfig.getPort(),
                                dbConfig.isAutoBackup());
                    }
                    System.out.println("-------------------------------------------------------------\n");
                    break;
            default:
                System.out.println("Unknown command. Type 'help'.");
        }
        return true;
    }

    private java.util.List<String> resolveDbNames(String input, AppConfig config, AppConfig newConfig) {
        if (input == null) {
            if (config.getDatabases().size() == 1) {
                return java.util.Collections.singletonList(config.getDatabases().keySet().iterator().next());
            }
            System.out.println("Please specify one or more database names separated by commas.");
            return java.util.Collections.emptyList();
        }
        java.util.List<String> validNames = new java.util.ArrayList<>();
        for (String name : input.split(",")) {
            name = name.trim();
            if (config.getDatabases().containsKey(name)
                    || (newConfig != null && newConfig.getDatabases().containsKey(name))) {
                validNames.add(name);
            } else {
                System.out.println("Database '" + name + "' not found in config. Skipping.");
            }
        }
        return validNames;
    }

    private java.util.List<String> resolveDbNames(String input, AppConfig config) {
        return resolveDbNames(input, config, null);
    }

    private boolean isDbConfigChanged(DatabaseConfig oldDb, DatabaseConfig newDb) {
        // NOTE: We only compare fields that dictate the MariaDB daemon's network/auth
        // state.
        // If these change, the engine MUST restart.
        // Other fields (autoBackup, maxLocalBackups) are read dynamically by DBC and do
        // not require DB downtime.
        if (oldDb.getPort() != newDb.getPort())
            return true;
        if (!java.util.Objects.equals(oldDb.getUsername(), newDb.getUsername()))
            return true;
        if (!java.util.Arrays.equals(oldDb.getPassword(), newDb.getPassword()))
            return true;
        if (!java.util.Objects.equals(oldDb.getIp(), newDb.getIp()))
            return true;
        return false;
    }

    private Completer createCompleter() {
        return (reader, line, candidates) -> {
            int index = line.wordIndex();
            String word = line.word();

            if (index == 0) {
                String[] actions = { "enable", "disable", "reload", "backup", "status", "stop", "help" };
                for (String a : actions) {
                    if (a.startsWith(word.toLowerCase()))
                        candidates.add(new Candidate(a));
                }
            } else if (index == 1) {
                String action = line.words().get(0).toLowerCase();
                if (action.equals("enable") || action.equals("disable") || action.equals("reload")
                        || action.equals("backup")) {
                    String prefix = "";
                    String currentWord = word;
                    if (word.contains(",")) {
                        int lastComma = word.lastIndexOf(',');
                        prefix = word.substring(0, lastComma + 1);
                        currentWord = word.substring(lastComma + 1);
                    }
                    for (String dbName : configManager.getConfig().getDatabases().keySet()) {
                        if (dbName.toLowerCase().startsWith(currentWord.toLowerCase())) {
                            candidates.add(new Candidate(prefix + dbName));
                        }
                    }
                }
            }
        };
    }
}
