package com.marblehalledgaming.discordbridge;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class DatabaseManager {
    // Increment this number whenever you add a new column to requiredColumns
    private static final int CURRENT_SCHEMA_VERSION = 3;
    private static Connection connection;
    private static boolean enabled = false;

    public static void init() {
        if (!Config.USE_SQL.get()) return;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            String url = "jdbc:mysql://" + Config.SQL_HOST.get() + ":" + Config.SQL_PORT.get() + "/" + Config.SQL_DB.get() + "?connectTimeout=5000&socketTimeout=5000";

            connection = DriverManager.getConnection(url, Config.SQL_USER.get(), Config.SQL_PASS.get());
            enabled = true;

            // 2. Check if we need to update
            if (getDatabaseVersion() < CURRENT_SCHEMA_VERSION) {
                DiscordBridge.LOGGER.info("Database: Update detected. Syncing schema...");

                // Create the main table shell if it doesn't exist
                try (Statement s = connection.createStatement()) {
                    s.execute("CREATE TABLE IF NOT EXISTS bridge_metadata (schema_version INT)");
                    s.execute("CREATE TABLE IF NOT EXISTS linked_accounts (uuid VARCHAR(36) PRIMARY KEY)");
                }

                validateSchema();
                updateDatabaseVersion();
            } else {
                DiscordBridge.LOGGER.info("Database: Schema is up to date (Version {}). Skipping validation.", CURRENT_SCHEMA_VERSION);
            }

        } catch (SQLException | ClassNotFoundException e) {
            enabled = false;
            DiscordBridge.LOGGER.error("Discord Bridge: Database connection failed! Discord Bridge will operate in limited mode.");
            DiscordBridge.LOGGER.error("Detailed Error: " + e.getMessage());
        }
    }

    public static boolean reconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}

        enabled = false;
        connection = null;
        init();
        return enabled;
    }

    public static void linkAccount(String uuid, String discordId) {
        if (!enabled || connection == null) return;

        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO linked_accounts (uuid, discord_id) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE discord_id = ?, linked_at = DATE DEFAULT (CURRENT_DATE)")) {
            ps.setString(1, uuid);
            ps.setString(2, discordId);
            ps.setString(3, discordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            DiscordBridge.LOGGER.error("SQL Error during link: " + e.getMessage());
        }
    }

    public static String getDiscordId(String uuid) {
        if (!enabled || connection == null) return null;
        try (PreparedStatement ps = connection.prepareStatement("SELECT discord_id FROM linked_accounts WHERE uuid = ?")) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("discord_id");
        } catch (SQLException e) {
            DiscordBridge.LOGGER.error("SQL Error fetching Discord ID: " + e.getMessage());
        }
        return null;
    }

    /**
     * Fetches link information for a specific UUID or ALL players if uuid is null.
     */
    public static Map<String, String> getLinkInfo(String uuid) {
        Map<String, String> results = new LinkedHashMap<>();
        if (!enabled || connection == null) return results;

        String query = (uuid == null)
                ? "SELECT uuid, discord_id, linked_at FROM linked_accounts"
                : "SELECT uuid, discord_id, linked_at FROM linked_accounts WHERE uuid = ?";

        try (PreparedStatement ps = connection.prepareStatement(query)) {
            if (uuid != null) ps.setString(1, uuid);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Date sqlDate = rs.getDate("linked_at");
                String dateStr = (sqlDate != null) ? sqlDate.toString() : "Unknown";

                String info = String.format("§7ID: §b%s §7| Linked: §e%s",
                        rs.getString("discord_id"),
                        dateStr);
                results.put(rs.getString("uuid"), info);
            }
        } catch (SQLException e) {
            DiscordBridge.LOGGER.error("SQL Lookup Error: " + e.getMessage());
        }
        return results;
    }

    public static String getRawStatus() {
        if (!Config.USE_SQL.get()) return "§8DISABLED (Config)";
        try {
            if (connection != null && !connection.isClosed() && connection.isValid(2)) {
                return "§aCONNECTED";
            }
        } catch (SQLException ignored) {}
        return "§cOFFLINE";
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static void validateSchema() {
        try {
            DatabaseMetaData meta = connection.getMetaData();

            // Define all required columns here (except the Primary Key)
            // Format: { "column_name", "sql_definition" }
            Map<String, String> requiredColumns = new LinkedHashMap<>();
            requiredColumns.put("discord_id", "VARCHAR(20) NOT NULL");
            requiredColumns.put("linked_at", "DATE DEFAULT (CURRENT_DATE)");
            // requiredColumns.put("future_column", "INT DEFAULT 0"); // Just add new ones here!

            for (Map.Entry<String, String> entry : requiredColumns.entrySet()) {
                String columnName = entry.getKey();
                String definition = entry.getValue();

                // Check if this specific column exists
                try (ResultSet rs = meta.getColumns(null, null, "linked_accounts", columnName)) {
                    if (!rs.next()) {
                        DiscordBridge.LOGGER.info("Database: Adding missing column '{}'...", columnName);
                        try (Statement s = connection.createStatement()) {
                            s.execute("ALTER TABLE linked_accounts ADD COLUMN " + columnName + " " + definition);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            DiscordBridge.LOGGER.error("Error during database synchronization: " + e.getMessage());
        }
    }

    private static int getDatabaseVersion() {
        if (connection == null) return 0;

        try {
            // 1. Check if the metadata table exists first
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, "bridge_metadata", new String[]{"TABLE"})) {
                if (!rs.next()) {
                    // Table doesn't exist yet, so we are at version 0
                    return 0;
                }
            }

            // 2. If it exists, now it's safe to query it
            try (Statement s = connection.createStatement();
                 ResultSet rs = s.executeQuery("SELECT schema_version FROM bridge_metadata")) {
                if (rs.next()) {
                    return rs.getInt("schema_version");
                }
            }
        } catch (SQLException e) {
            DiscordBridge.LOGGER.error("Error checking database version: " + e.getMessage());
        }
        return 0;
    }

    private static void updateDatabaseVersion() {
        try (Statement s = connection.createStatement()) {
            s.execute("DELETE FROM bridge_metadata"); // Keep only one row
            s.execute("INSERT INTO bridge_metadata (schema_version) VALUES (" + CURRENT_SCHEMA_VERSION + ")");
        } catch (SQLException e) {
            DiscordBridge.LOGGER.error("Failed to update schema version: " + e.getMessage());
        }
    }
}