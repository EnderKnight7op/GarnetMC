package com.garnetminecraft;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class MessageParser {
    public Component parseMessage(String message, String format) {
        if ("minimessage".equalsIgnoreCase(format)) {
            return MiniMessage.miniMessage().deserialize(message);
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(message);
    }
}
