package com.marblehalledgaming.discordbridge;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Mod(DiscordBridge.MODID)
public class DiscordBridge {
    public static final String MODID = "discordbridge";
    public static final Logger LOGGER = LogUtils.getLogger();

    // This is our single, reusable client for all Webhook requests
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public static net.minecraft.server.MinecraftServer serverInstance;

    public static JDA jda;

    public DiscordBridge(IEventBus modEventBus, ModContainer modContainer) {
        // Register the main mod class to the bus
        NeoForge.EVENT_BUS.register(this);

        // IMPORTANT: Register the event listener class IMMEDIATELY
        // This ensures the RegisterCommandsEvent is caught when the server starts up
        NeoForge.EVENT_BUS.register(new MinecraftEventListener());

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Load local links at mod construction
        LinkManager.load();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        serverInstance = event.getServer();
        String token = Config.DISCORD_BOT_TOKEN.get();

        if (!isTokenValid(token)) {
            LOGGER.error("DISCORD BRIDGE: INVALID TOKEN!");
            return;
        }

        // Start Discord in a separate thread so it doesn't hang the server startup
        new Thread(() -> {
            try {
                jda = JDABuilder.createDefault(token.trim())
                        .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                        .addEventListeners(new DiscordToMCListener())
                        .build();

                jda.awaitReady();

                updateBotProfile();

                // Register Discord Slash Commands
                var commands = jda.updateCommands();
                for (String entry : Config.PUBLIC_COMMANDS.get()) {
                    String[] parts = entry.split(":");
                    if (parts.length == 2) {
                        commands.addCommands(Commands.slash(parts[0], "Execute " + parts[0] + " on the server"));
                    }
                }
                commands.addCommands(Commands.slash("link", "Link your Minecraft account")
                        .addOption(OptionType.STRING, "code", "The 4-digit code from in-game", true));
                commands.queue();
                LOGGER.info("Discord Bridge: JDA and Slash Commands Ready.");

                // Initialize SQL if enabled
                if (Config.USE_SQL.get()) {
                    DatabaseManager.init();
                }

                // Wait a tiny bit or check if the server is actually in its play state
                if (serverInstance != null && !serverInstance.isStopped()) {
                    // If you want to wait until the server is truly "Ready" for players:
                    while (!serverInstance.isRunning() && !serverInstance.isStopped()) {
                        Thread.sleep(1000); // Check every second
                    }

                    if (!serverInstance.isStopped()) {
                        sendMessageToDiscord(Config.SERVER_START_MESSAGE.get());
                    }
                }

            } catch (Exception e) {
                LOGGER.error("Discord Bridge: JDA Startup Failed", e);
            }
        }).start();
    }

    private boolean isTokenValid(String token) {
        return token != null && !token.isEmpty() && !token.contains(" ")
                && !token.equalsIgnoreCase("PUT TOKEN HERE") && token.length() > 30;
    }

    /**
     * Standard Bot Message (Fallback or System messages)
     */
    public static void sendMessageToDiscord(String content) {
        if (jda != null) {
            try {
                var channel = jda.getTextChannelById(Config.DISCORD_CHANNEL_ID.get());
                if (channel != null) {
                    channel.sendMessage(content).queue();
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Sends a message via Webhook to allow for Player Skins and custom names
     */
    public static void sendWebhookMessage(String username, String content, String avatarUrl) {
        String webhookUrl = Config.WEBHOOK_URL.get();
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            // Fallback to bot if webhook is not configured
            sendMessageToDiscord("**" + username + "**: " + content);
            return;
        }

        JsonObject json = new JsonObject();
        json.addProperty("username", username);
        json.addProperty("content", content);
        json.addProperty("avatar_url", avatarUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .exceptionally(ex -> {
                    LOGGER.error("Discord Bridge: Webhook failed to send: " + ex.getMessage());
                    return null;
                });
    }

    public static JDA getJDA() {
        return jda;
    }

    private void updateBotProfile() {
        if (jda == null) return;

        String newName = Config.BOT_NAME.get();
        String avatarUrl = Config.BOT_AVATAR_URL.get();

        if (!newName.isEmpty() && !newName.equals("Minecraft Bridge Bot")) {
            jda.getSelfUser().getManager().setName(newName).queue(
                    success -> LOGGER.info("Discord Bridge: Bot name updated to {}", newName),
                    error -> LOGGER.warn("Discord Bridge: Failed to update name (Rate limited?)")
            );
        }

        if (!avatarUrl.isEmpty()) {
            try {
                java.net.URL url = new java.net.URL(avatarUrl);
                java.io.InputStream in = url.openStream();
                net.dv8tion.jda.api.entities.Icon icon = net.dv8tion.jda.api.entities.Icon.from(in);

                jda.getSelfUser().getManager().setAvatar(icon).queue(
                        success -> LOGGER.info("Discord Bridge: Bot avatar updated."),
                        error -> LOGGER.warn("Discord Bridge: Failed to update avatar (Rate limited or invalid URL?)")
                );
            } catch (Exception e) {
                LOGGER.error("Discord Bridge: Could not load avatar from URL: " + avatarUrl);
            }
        }
    }
}