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

    private String pendingCommand = null;
    private Instant pendingCommandTime = null;

    public Manager(com.villagev.studio.dbc.config.Manager configManager, DatabaseManager dbManager, BackupManager backupManager) {
        this.configManager = configManager;
        this.dbManager = dbManager;
        this.backupManager = backupManager;
    }

    public void start() {
        try {
            Terminal terminal = TerminalBuilder.builder().system(true).build();
            LineReader lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(createCompleter())
                    .build();

            System.out.println("Type 'db help' for a list of commands.");

            while (true) {
                String line;
                try {
                    line = lineReader.readLine("> ").trim();
                } catch (UserInterruptException e) {
                    continue;
                } catch (org.jline.reader.EndOfFileException e) {
                    System.out.println("\nShutting down Database Creator safely...");
                    dbManager.stopAll();
                    break;
                }

                if (line.isEmpty())
                    continue;

                if (!processCommand(line)) {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean processCommand(String line) {
        AppConfig config = configManager.getConfig();
        String[] args = line.split("\\s+");

        if (args[0].equalsIgnoreCase("db")) {
            if (args.length < 2) {
                System.out.println("Usage: db <enable|disable|reload|backup|stop|help> [name]");
                return true;
            }

            String action = args[1].toLowerCase();
            String targets = args.length > 2 ? args[2] : null;

            if ((action.equals("backup") || action.equals("stop")) && targets == null) {
                if (pendingCommand != null && pendingCommand.equals(action)) {
                    if (Instant.now().minusSeconds(5).isBefore(pendingCommandTime)) {
                        pendingCommand = null;
                        if (action.equals("backup")) {
                            backupManager.backupAllActive(config, true);
                            return true;
                        } else {
                            System.out.println("Shutting down Database Creator safely...");
                            dbManager.stopAll();
                            return false;
                        }
                    } else {
                        System.out.println("Confirmation expired. Type again to confirm.");
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
                        for (String name : resolveDbNames(targets, config)) {
                            backupManager.runBackup(name, config.getDatabases().get(name), config, true);
                        }
                    }
                    break;
                case "reload":
                    System.out.println("Reloading configuration...");
                    configManager.loadConfig();
                    AppConfig newConfig = configManager.getConfig();

                    if (targets != null) {
                        for (String name : resolveDbNames(targets, config)) {
                            if (!newConfig.getDatabases().containsKey(name)) {
                                System.out.println("Database " + name + " was removed from config. Stopping it.");
                                dbManager.stopDatabase(name);
                            } else {
                                System.out.println("Reloading database: " + name + "...");
                                dbManager.stopDatabase(name);
                                dbManager.startDatabase(name, newConfig.getDatabases().get(name));
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
                            if (entry.getValue().isAutoStart()) {
                                System.out.println("Reloading database: " + entry.getKey() + "...");
                                dbManager.stopDatabase(entry.getKey());
                                dbManager.startDatabase(entry.getKey(), entry.getValue());
                            }
                        }
                    }
                    break;
                case "help":
                    System.out.println("Commands:");
                    System.out.println("  db enable [name1,name2]  - Start databases");
                    System.out.println("  db disable [name1,name2] - Stop databases");
                    System.out.println("  db reload [name1,name2]  - Reload config (and restart DBs)");
                    System.out.println("  db backup [name1,name2]  - Run manual backup (double tap for all)");
                    System.out.println("  db stop                  - Safely shutdown manager (double tap)");
                    break;
                default:
                    System.out.println("Unknown action. Type 'db help'.");
            }
        } else if (line.equalsIgnoreCase("help") || line.equalsIgnoreCase("?")) {
            System.out.println("Type 'db help' for a list of commands.");
        } else {
            System.out.println("Unknown command.");
        }
        return true;
    }

    private java.util.List<String> resolveDbNames(String input, AppConfig config) {
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
            if (config.getDatabases().containsKey(name)) {
                validNames.add(name);
            } else {
                System.out.println("Database '" + name + "' not found in config. Skipping.");
            }
        }
        return validNames;
    }

    private Completer createCompleter() {
        return (reader, line, candidates) -> {
            int index = line.wordIndex();
            String word = line.word();

            if (index == 0) {
                if ("db".startsWith(word.toLowerCase()))
                    candidates.add(new Candidate("db"));
                if ("help".startsWith(word.toLowerCase()))
                    candidates.add(new Candidate("help"));
                if ("?".startsWith(word))
                    candidates.add(new Candidate("?"));
                if ("exit".startsWith(word.toLowerCase()))
                    candidates.add(new Candidate("exit"));
            } else if (index == 1 && line.words().get(0).equalsIgnoreCase("db")) {
                String[] actions = { "enable", "disable", "reload", "backup", "stop", "help" };
                for (String action : actions) {
                    if (action.startsWith(word.toLowerCase()))
                        candidates.add(new Candidate(action));
                }
            } else if (index == 2 && line.words().get(0).equalsIgnoreCase("db")) {
                String action = line.words().get(1).toLowerCase();
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
