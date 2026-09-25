package com.earthpol.kernel.data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class KernelDatabase implements AutoCloseable {

    private final Path databasePath;
    private Connection connection;

    public KernelDatabase(Path databasePath) {
        this.databasePath = databasePath;
    }

    public void open() throws SQLException {
        try {
            Files.createDirectories(databasePath.toAbsolutePath().getParent());
        } catch (Exception exception) {
            throw new SQLException("Unable to create the database directory.", exception);
        }

        this.connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
            statement.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid TEXT PRIMARY KEY,
                    last_known_name TEXT NOT NULL,
                    last_known_name_lower TEXT NOT NULL,
                    last_known_ip TEXT,
                    balance TEXT NOT NULL,
                    nickname TEXT,
                    god_mode INTEGER NOT NULL DEFAULT 0,
                    fly_mode INTEGER NOT NULL DEFAULT 0,
                    teleport_blocked INTEGER NOT NULL DEFAULT 0,
                    last_seen_epoch_ms INTEGER NOT NULL DEFAULT 0
                )
                """);
            statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_players_name
                ON players(last_known_name_lower)
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS homes (
                    owner_uuid TEXT NOT NULL,
                    home_name TEXT NOT NULL,
                    world_name TEXT NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    z REAL NOT NULL,
                    yaw REAL NOT NULL,
                    pitch REAL NOT NULL,
                    PRIMARY KEY (owner_uuid, home_name),
                    FOREIGN KEY (owner_uuid) REFERENCES players(uuid) ON DELETE CASCADE
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS ignores (
                    owner_uuid TEXT NOT NULL,
                    target_uuid TEXT NOT NULL,
                    PRIMARY KEY (owner_uuid, target_uuid),
                    FOREIGN KEY (owner_uuid) REFERENCES players(uuid) ON DELETE CASCADE
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS mail (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    recipient_uuid TEXT NOT NULL,
                    sender_uuid TEXT,
                    sender_name TEXT NOT NULL,
                    message TEXT NOT NULL,
                    sent_epoch_ms INTEGER NOT NULL,
                    delivered INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (recipient_uuid) REFERENCES players(uuid) ON DELETE CASCADE
                )
                """);
            statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_mail_recipient
                ON mail(recipient_uuid, delivered, sent_epoch_ms)
                """);
        }

        ensureColumn("players", "last_known_ip", "TEXT");
    }

    public Connection connection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            open();
        }

        return connection;
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void ensureColumn(String tableName, String columnName, String definition) throws SQLException {
        if (hasColumn(tableName, columnName)) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }

    private boolean hasColumn(String tableName, String columnName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
        }

        return false;
    }
}
