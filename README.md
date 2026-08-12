# DBC - Database Manager

**DBC** (Database Creator) is a powerful yet incredibly easy-to-use MySQL (MariaDB) database manager designed specifically for developers, system administrators, and microservice networks.

## 🌟 Why DBC?

Setting up isolated databases for multiple applications or microservices typically requires installing a heavy MySQL server on Linux, creating users via SQL queries, and writing complex bash scripts for backups.

**DBC solves all of this:**
- **Zero Setup:** You don't need to install MySQL on your server! DBC includes an embedded MariaDB engine. The application is **fully cross-platform** and works out of the box on **Windows, Linux, and macOS**.
- **High Isolation:** Each database runs as a **separate, isolated process** on its own port. If one database hangs, the databases for other applications will continue to run perfectly.
- **Auto-backups to Google Drive:** Built-in integration with `rclone` and AES-256 encryption (via Zip4j). Your backups will automatically be sent to the cloud in an encrypted format. *(Note: To upload to the cloud, the `rclone` utility must be installed on your computer/server. If it is not installed, DBC will still work and create local `.zip` archives, but will warn you that uploading is unavailable).*
- **Single TOML Config:** Everything is managed through one clean and intuitive `config.toml` file.

## ⚙️ Config Example (`config.toml`)

```toml
drive-token = "token"
client-id = "id"
client-secret = "secret"
password = "super_secure_password" # Using AES-256 encryption for ZIP archives (never lose it!)
drive-folder = "DBC_Backups/"
time-zone = "UTC" # App and database timezone
log-level = "info" # Console log verbosity (debug, info, warn, error)

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
- `db enable <name>` — Enable a database.
- `db disable <name>` — Disable a database.
- `db reload` — Reload the config file. Deleted databases will be automatically stopped and renamed to `.old`.
- `db backup` — Trigger a manual backup of all active databases.
- `db stop` — Safely stop all databases and the program.

## 🛠 Compilation and Start
The project is compiled using Maven.
```bash
mvn clean package
java --enable-native-access=ALL-UNNAMED -jar target/DBC.jar
```
