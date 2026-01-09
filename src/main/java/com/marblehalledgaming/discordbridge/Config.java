package com.marblehalledgaming.discordbridge;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.Collections;
import java.util.List;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> DISCORD_BOT_TOKEN;
    public static final ModConfigSpec.ConfigValue<String> DISCORD_CHANNEL_ID;
    public static final ModConfigSpec.ConfigValue<String> BOT_NAME;
    public static final ModConfigSpec.ConfigValue<String> BOT_AVATAR_URL;
    public static final ModConfigSpec.BooleanValue USE_WEBHOOKS;
    public static final ModConfigSpec.ConfigValue<String> WEBHOOK_URL;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ADMIN_IDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PUBLIC_COMMANDS;
    public static final ModConfigSpec.BooleanValue ENABLE_LINKING;
    public static final ModConfigSpec.BooleanValue USE_SQL;

    public static final ModConfigSpec.ConfigValue<String> SQL_HOST;
    public static final ModConfigSpec.IntValue SQL_PORT;
    public static final ModConfigSpec.ConfigValue<String> SQL_USER;
    public static final ModConfigSpec.ConfigValue<String> SQL_PASS;
    public static final ModConfigSpec.ConfigValue<String> SQL_DB;

    // Event Messages
    public static final ModConfigSpec.ConfigValue<String> SERVER_START_MESSAGE;
    public static final ModConfigSpec.ConfigValue<String> SERVER_STOP_MESSAGE;
    public static final ModConfigSpec.ConfigValue<String> JOIN_MESSAGE;
    public static final ModConfigSpec.ConfigValue<String> LEAVE_MESSAGE;
    public static final ModConfigSpec.ConfigValue<String> DEATH_MESSAGE_FORMAT;

    static {
        BUILDER.push("General Settings");
        DISCORD_BOT_TOKEN = BUILDER.comment("Discord Bot Token").define("bot_token", "your_token_here");
        DISCORD_CHANNEL_ID = BUILDER.comment("Discord Channel ID").define("channel_id", "0");
        BOT_NAME = BUILDER.comment("The Display Name of the Bot").define("bot_name", "Minecraft Bridge Bot");
        BOT_AVATAR_URL = BUILDER.comment("URL to an image (PNG/JPG) for the bot's profile picture").define("bot_avatar_url", "");
        USE_WEBHOOKS = BUILDER.comment("If true, chat and deaths will use player skins/names via Webhooks.").define("use_webhooks", false);
        WEBHOOK_URL = BUILDER.comment("The Discord Webhook URL (Required if use_webhooks is true)").define("webhook_url", "");
        ADMIN_IDS = BUILDER.comment("List of Discord User/Role IDs allowed to run admin commands (e.g., ['123456789', '987654321'])").defineList("admin_ids", Collections.emptyList(), obj -> obj instanceof String);
        PUBLIC_COMMANDS = BUILDER.comment("Format: 'discord_command:minecraft_command'. Example: ['list:list', 'tps:neoforge tps']").defineList("public_commands", List.of("list:list", "tps:neoforge tps"), obj -> obj instanceof String);
        BUILDER.pop();

        BUILDER.push("Database & Linking Settings");
        ENABLE_LINKING = BUILDER.comment("Enable Discord-to-Minecraft account linking").define("enable_linking", false);
        USE_SQL = BUILDER.comment("Use SQL Database for cross-server linking").define("use_sql", false);

        SQL_HOST = BUILDER.define("sql_host", "localhost");
        SQL_PORT = BUILDER.defineInRange("sql_port", 3306, 1, 65535);
        SQL_USER = BUILDER.define("sql_user", "root");
        SQL_PASS = BUILDER.define("sql_pass", "password");
        SQL_DB = BUILDER.define("sql_database", "discord_bridge");
        BUILDER.pop();

        BUILDER.push("Event Messages");
        SERVER_START_MESSAGE = BUILDER.define("server_start", "✅ **Server has started!**");
        SERVER_STOP_MESSAGE = BUILDER.define("server_stop", "🛑 **Server is shutting down...**");
        JOIN_MESSAGE = BUILDER.comment("Use %s for the username").define("player_join", "📥 **%s** joined the server.");
        LEAVE_MESSAGE = BUILDER.comment("Use %s for the username").define("player_leave", "📤 **%s** left the server.");
        DEATH_MESSAGE_FORMAT = BUILDER.comment("Use %s for the full death message").define("player_death", "☠️ **%s**");
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static final ModConfigSpec SPEC;
}