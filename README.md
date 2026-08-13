# DBC - Database Creator

**DBC** (Database Creator) is a powerful yet incredibly easy-to-use MySQL (MariaDB) database manager designed specifically for developers, system administrators, and microservice networks.

## 🌟 Why DBC?

Setting up isolated databases for multiple applications or microservices typically requires installing a heavy MySQL server on Linux, creating users via SQL queries, and writing complex bash scripts for backups.

**DBC solves all of this:**
- **Zero Setup:** You don't need to install MySQL on your server! DBC includes an embedded MariaDB engine. The application is **fully cross-platform** and works out of the box on **Windows, Linux, and macOS**.
- **Java 17+ Required:** Compatible with modern enterprise environments (runs perfectly on Java 17, 25, and newer).
- **High Isolation:** Each database runs as a **separate, isolated process** on its own port. If one database hangs, the databases for other applications will continue to run perfectly.
- **Flexible Encrypted Backups:** Built-in support for multiple backup providers (`local`, `google-drive`, `server`) with AES-256 encryption (via Zip4j). Configure scheduled auto-backups or trigger them manually. Google Drive backups automatically sync to the cloud via `rclone` integration. *(Note: For Google Drive backups, the `rclone` utility must be installed on your system).*
- **Single TOML Config:** Everything is managed through one clean and intuitive `config.toml` file.

## ⚙️ Config Example (`config.toml`)

```toml
time-zone = "UTC" # App and database timezone
log-level = "info" # Console log verbosity (debug, info, warn, error)
backup-type = "local" # Choose: local, google-drive, or server
password = "super_secure_password" # Using AES-256 encryption for ZIP archives (never lose it!)

[google-drive]
drive-token = "token"
client-id = "id"
client-secret = "secret"
drive-folder = "DBC_Backups/"

[server]
ip = "192.168.1.10"
port = 22
username = "admin"
password = "server_password"
remote-folder = "/backups"

[database.example]
ip = "127.0.0.1"
port = 3006
username = "root"
password = "strong_password"
auto-start = false
auto-backup = false
backup-interval = 24 # in hours
```

## 🚀 Commands
Use these commands to control your databases via the built-in interactive console:
- `db enable [name1,name2]` — Start one or multiple databases (separated by commas).
- `db disable [name1,name2]` — Stop one or multiple databases.
- `db reload [name1,name2]` — Reload the config file. If no names are provided, applies to all databases. Databases removed from config will be safely stopped.
- `db backup [name1,name2]` — Run a manual forced backup for specific databases (bypasses auto-backup setting).
- `db stop` — Safely stop all databases and the program.

## 🛠 Compilation and Start
The project is compiled using Maven.
```bash
mvn clean package
java -jar target/DBC.jar
```
