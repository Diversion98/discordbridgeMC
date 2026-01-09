package com.marblehalledgaming.discordbridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LinkManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("discord_links.json");
    private static Map<UUID, String> links = new HashMap<>();

    public static void load() {
        if (!Files.exists(PATH)) {
            save(); // Create empty file if it doesn't exist
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            links = GSON.fromJson(reader, new TypeToken<Map<UUID, String>>(){}.getType());
            if (links == null) links = new HashMap<>();
        } catch (IOException e) {
            DiscordBridge.LOGGER.error("Failed to load Discord links!", e);
        }
    }

    public static void save() {
        try (Writer writer = Files.newBufferedWriter(PATH)) {
            GSON.toJson(links, writer);
        } catch (IOException e) {
            DiscordBridge.LOGGER.error("Failed to save Discord links!", e);
        }
    }

    public static void addLink(UUID uuid, String discordId) {
        links.put(uuid, discordId);
        save(); // Save immediately when a link is made
    }

    public static String getDiscordId(UUID uuid) {
        return links.get(uuid);
    }
}