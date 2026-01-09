package com.marblehalledgaming.discordbridge;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public class DiscordToMCListener extends ListenerAdapter {

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String targetChannelId = Config.DISCORD_CHANNEL_ID.get();
        if (!event.getChannel().getId().equals(targetChannelId)) {
            return;
        }

        String message = event.getMessage().getContentRaw();

        // 1. Handle Admin Prefix Commands (!cmd)
        if (message.startsWith("!cmd ")) {
            if (hasPermission(event)) {
                String command = message.substring(5);
                executeServerCommand(command, event);
            } else {
                event.getChannel().sendMessage("❌ You do not have permission to run server commands.").queue();
            }
            return;
        }

        // 2. Mirror Discord chat to Minecraft
        if (DiscordBridge.serverInstance != null) {
            String name = event.getAuthor().getName();
            DiscordBridge.serverInstance.execute(() -> {
                DiscordBridge.serverInstance.getPlayerList().broadcastSystemMessage(
                        Component.literal("§9[Discord] §f" + name + ": " + message), false
                );
            });
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        // --- 1. HANDLE LINK COMMAND ---
        if (event.getName().equals("link")) {
            handleLinkInteraction(event);
            return;
        }

        // --- 2. HANDLE CONFIG COMMANDS (/tps, /list, etc) ---
        String mcCommand = null;
        for (String entry : Config.PUBLIC_COMMANDS.get()) {
            String[] parts = entry.split(":");
            if (parts.length == 2 && parts[0].equalsIgnoreCase(event.getName())) {
                mcCommand = parts[1];
                break;
            }
        }

        if (mcCommand != null) {
            executePublicCommandWithOutput(mcCommand, event);
        }
    }

    private void handleLinkInteraction(SlashCommandInteractionEvent event) {
        OptionMapping codeOption = event.getOption("code");
        if (codeOption == null) {
            event.reply("❌ Please provide the 4-digit code.").setEphemeral(true).queue();
            return;
        }

        String code = codeOption.getAsString();
        // Check if the code exists in our temporary memory
        UUID playerUuid = MinecraftEventListener.getUuidByCode(code);

        if (playerUuid != null) {
            event.deferReply().queue(); // Acknowledge while we write to DB/File

            if (DiscordBridge.serverInstance != null) {
                DiscordBridge.serverInstance.execute(() -> {
                    // Decide where to save based on config
                    if (Config.USE_SQL.get()) {
                        DatabaseManager.linkAccount(playerUuid.toString(), event.getUser().getId());
                    } else {
                        LinkManager.addLink(playerUuid, event.getUser().getId());
                    }
                    event.getHook().sendMessage("✅ Successfully linked to Minecraft account!").queue();
                });
            }
        } else {
            event.reply("❌ Invalid or expired code. Use `/link` in Minecraft to get a new one.")
                    .setEphemeral(true).queue();
        }
    }

    private void executePublicCommandWithOutput(String finalMcCommand, SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        if (DiscordBridge.serverInstance != null) {
            DiscordBridge.serverInstance.execute(() -> {
                StringBuilder output = new StringBuilder();

                // Intercept command output
                CommandSource customSource = new CommandSource() {
                    @Override
                    public void sendSystemMessage(@NotNull Component component) {
                        output.append(component.getString()).append("\n");
                    }
                    @Override public boolean acceptsSuccess() { return true; }
                    @Override public boolean acceptsFailure() { return true; }
                    @Override public boolean shouldInformAdmins() { return false; }
                };

                CommandSourceStack stack = new CommandSourceStack(
                        customSource, Vec3.ZERO, Vec2.ZERO,
                        DiscordBridge.serverInstance.overworld(),
                        4, "DiscordBot", Component.literal("DiscordBot"),
                        DiscordBridge.serverInstance, null
                );

                DiscordBridge.serverInstance.getCommands().performPrefixedCommand(stack, finalMcCommand);

                String result = output.toString().trim();
                if (result.isEmpty()) {
                    event.getHook().sendMessage("✅ Command executed successfully.").queue();
                } else {
                    event.getHook().sendMessage("```\n" + result + "\n```").queue();
                }
            });
        }
    }

    private void executeServerCommand(String cmd, MessageReceivedEvent event) {
        if (DiscordBridge.serverInstance != null) {
            DiscordBridge.serverInstance.execute(() -> {
                try {
                    String formattedCmd = cmd.startsWith("/") ? cmd : "/" + cmd;
                    DiscordBridge.serverInstance.getCommands().performPrefixedCommand(
                            DiscordBridge.serverInstance.createCommandSourceStack(),
                            formattedCmd
                    );
                    event.getChannel().sendMessage("💻 Console executed: `" + cmd + "`").queue();
                } catch (Exception e) {
                    event.getChannel().sendMessage("⚠️ Execution error: " + e.getMessage()).queue();
                }
            });
        }
    }

    private boolean hasPermission(MessageReceivedEvent event) {
        String userId = event.getAuthor().getId();
        List<? extends String> authorizedIds = Config.ADMIN_IDS.get();
        if (authorizedIds.contains(userId)) return true;
        if (event.getMember() != null) {
            return event.getMember().getRoles().stream()
                    .anyMatch(role -> authorizedIds.contains(role.getId()));
        }
        return false;
    }
}