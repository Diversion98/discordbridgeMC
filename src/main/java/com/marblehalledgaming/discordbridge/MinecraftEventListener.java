package com.marblehalledgaming.discordbridge;

import com.mojang.brigadier.CommandDispatcher;
import net.dv8tion.jda.api.JDA; // Added missing import
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.arguments.GameProfileArgument;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class MinecraftEventListener {
    private static final Map<String, UUID> pendingCodes = new HashMap<>();

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("link")
                .executes(context -> {
                    if (context.getSource().getEntity() instanceof Player player) {
                        handleLinkCommand(player);
                    } else {
                        context.getSource().sendFailure(Component.literal("Only players can use this command."));
                    }
                    return 1;
                })
        );

        dispatcher.register(Commands.literal("discordbridge")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reconnect")
                        .executes(context -> {
                            context.getSource().sendSystemMessage(Component.literal("§eAttempting to reconnect to SQL database..."));
                            boolean success = DatabaseManager.reconnect();
                            if (success) {
                                context.getSource().sendSystemMessage(Component.literal("§a✅ SQL Database reconnected!"));
                            } else {
                                context.getSource().sendFailure(Component.literal("❌ Reconnection failed. Check console."));
                            }
                            return 1;
                        })
                )
                .then(Commands.literal("status")
                        .executes(context -> {
                            JDA bot = DiscordBridge.getJDA();
                            String jdaStatus = "§cNOT INITIALIZED";

                            if (bot != null) {
                                jdaStatus = switch (bot.getStatus()) {
                                    case CONNECTED -> "§aCONNECTED";
                                    case CONNECTING_TO_WEBSOCKET, IDENTIFYING_SESSION -> "§6CONNECTING...";
                                    case ATTEMPTING_TO_RECONNECT -> "§eRECONNECTING...";
                                    case SHUTDOWN -> "§4SHUTDOWN";
                                    default -> "§e" + bot.getStatus().name();
                                };
                            }

                            String sqlStatus = DatabaseManager.getRawStatus();

                            context.getSource().sendSystemMessage(Component.literal("§b--- Discord Bridge Status ---"));
                            context.getSource().sendSystemMessage(Component.literal("§7Discord Bot: " + jdaStatus));
                            context.getSource().sendSystemMessage(Component.literal("§7SQL Database: " + sqlStatus));
                            return 1;
                        })
                ).then(Commands.literal("lookup")
                        .requires(source -> source.hasPermission(2))
                        // Optional argument: if omitted, it shows all
                        .executes(context -> {
                            showLookup(context.getSource(), null);
                            return 1;
                        })
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(context -> {
                                    var profiles = GameProfileArgument.getGameProfiles(context, "player");
                                    for (GameProfile profile : profiles) {
                                        showLookup(context.getSource(), profile.getId().toString());
                                    }
                                    return 1;
                                })
                        )
                )
        );
    }

    private void showLookup(CommandSourceStack source, String uuid) {
        Map<String, String> data = DatabaseManager.getLinkInfo(uuid);

        if (data.isEmpty()) {
            source.sendSystemMessage(Component.literal("§cNo link data found."));
            return;
        }

        source.sendSystemMessage(Component.literal("§b--- Discord Link Lookup ---"));
        data.forEach((foundUuid, info) -> {
            // Try to get the name from the server, otherwise show UUID
            String name = foundUuid;
            var player = source.getServer().getPlayerList().getPlayer(UUID.fromString(foundUuid));
            if (player != null) name = player.getName().getString();

            source.sendSystemMessage(Component.literal("§f" + name + ": " + info));
        });
    }

    private void handleLinkCommand(Player player) {
        if (!Config.ENABLE_LINKING.get()) {
            player.sendSystemMessage(Component.literal("§cAccount linking is currently disabled."));
            return;
        }
        String code = String.format("%04d", new Random().nextInt(10000));
        pendingCodes.put(code, player.getUUID());

        player.sendSystemMessage(Component.literal("§aYour linking code is: §f" + code));
        player.sendSystemMessage(Component.literal("§7Type §b/link " + code + " §7in Discord to finish linking."));
    }

    public static UUID getUuidByCode(String code) {
        return pendingCodes.remove(code);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        DiscordBridge.sendMessageToDiscord(Config.SERVER_STOP_MESSAGE.get());
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        String name = event.getEntity().getName().getString();
        DiscordBridge.sendMessageToDiscord(String.format(Config.JOIN_MESSAGE.get(), name));
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        String name = event.getEntity().getName().getString();
        DiscordBridge.sendMessageToDiscord(String.format(Config.LEAVE_MESSAGE.get(), name));
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            String username = player.getName().getString();
            String deathMsg = player.getCombatTracker().getDeathMessage().getString();
            String formatted = String.format(Config.DEATH_MESSAGE_FORMAT.get(), deathMsg);

            if (Config.USE_WEBHOOKS.get()) {
                DiscordBridge.sendWebhookMessage(username, formatted, "https://mc-heads.net/avatar/" + username + ".png");
            } else {
                DiscordBridge.sendMessageToDiscord(formatted);
            }
        }
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        String message = event.getRawText();
        String username = event.getPlayer().getName().getString();

        if (Config.USE_WEBHOOKS.get()) {
            DiscordBridge.sendWebhookMessage(username, message, "https://mc-heads.net/avatar/" + username + ".png");
        } else {
            DiscordBridge.sendMessageToDiscord("**" + username + "**: " + message);
        }
    }
}