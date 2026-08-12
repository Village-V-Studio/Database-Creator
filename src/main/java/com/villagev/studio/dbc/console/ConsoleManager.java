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
import com.villagev.studio.dbc.config.ConfigManager;
import com.villagev.studio.dbc.config.DatabaseConfig;
import com.villagev.studio.dbc.core.BackupManager;
import com.villagev.studio.dbc.core.DatabaseManager;

public class ConsoleManager {
    private final ConfigManager configManager;
    private final DatabaseManager dbManager;
    private final BackupManager backupManager;

    private String pendingCommand = null;
    private Instant pendingCommandTime = null;

    public ConsoleManager(ConfigManager configManager, DatabaseManager dbManager, BackupManager backupManager) {
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
            String dbName = args.length > 2 ? args[2] : null;

            if (action.equals("backup") || action.equals("stop")) {
                if (pendingCommand != null && pendingCommand.equals(action)) {
                    if (Instant.now().minusSeconds(5).isBefore(pendingCommandTime)) {
                        pendingCommand = null;
                        if (action.equals("backup")) {
                            backupManager.backupAllActive(config);
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
                    dbName = resolveDbName(dbName, config);
                    if (dbName != null && config.getDatabases().containsKey(dbName)) {
                        dbManager.startDatabase(dbName, config.getDatabases().get(dbName));
                    }
                    break;
                case "disable":
                    dbName = resolveDbName(dbName, config);
                    if (dbName != null) {
                        dbManager.stopDatabase(dbName);
                    }
                    break;
                case "reload":
                    System.out.println("Reloading configuration...");
                    configManager.loadConfig();
                    AppConfig newConfig = configManager.getConfig();

                    if (dbName != null) {
                        if (!newConfig.getDatabases().containsKey(dbName)) {
                            System.out.println("Database " + dbName + " was removed from config. Renaming to .old");
                            dbManager.deleteDatabase(dbName);
                        } else {
                            dbManager.stopDatabase(dbName);
                            dbManager.startDatabase(dbName, newConfig.getDatabases().get(dbName));
                        }
                    } else {
                        for (String oldName : config.getDatabases().keySet()) {
                            if (!newConfig.getDatabases().containsKey(oldName)) {
                                dbManager.deleteDatabase(oldName);
                            }
                        }
                        for (Map.Entry<String, DatabaseConfig> entry : newConfig.getDatabases().entrySet()) {
                            if (entry.getValue().isAutoStart()) {
                                dbManager.stopDatabase(entry.getKey());
                                dbManager.startDatabase(entry.getKey(), entry.getValue());
                            }
                        }
                    }
                    break;
                case "help":
                    System.out.println("Commands:");
                    System.out.println("  db enable [name]  - Start a database");
                    System.out.println("  db disable [name] - Stop a database");
                    System.out.println("  db reload [name]  - Reload config (and restart DBs)");
                    System.out.println("  db backup         - Run manual backup (double tap)");
                    System.out.println("  db stop           - Safely shutdown manager (double tap)");
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

    private String resolveDbName(String name, AppConfig config) {
        if (name != null)
            return name;
        if (config.getDatabases().size() == 1) {
            return config.getDatabases().keySet().iterator().next();
        }
        System.out.println("Multiple databases found. Please specify the name.");
        return null;
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
                if (action.equals("enable") || action.equals("disable") || action.equals("reload")) {
                    for (String dbName : configManager.getConfig().getDatabases().keySet()) {
                        if (dbName.toLowerCase().startsWith(word.toLowerCase())) {
                            candidates.add(new Candidate(dbName));
                        }
                    }
                }
            }
        };
    }
}
