package com.garnetminecraft;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;

public class MessageParser {
    private final JavaPlugin plugin;

    public MessageParser(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Component parseMessage(String message) {
        final String format = plugin.getConfig().getString("format", "legacy");
        if ("minimessage".equalsIgnoreCase(format)) {
            return MiniMessage.miniMessage().deserialize(message);
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(message);
    }
}
