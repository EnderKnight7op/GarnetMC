package com.garnetminecraft;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;

public class MinecraftListener implements Listener {
    private final GarnetMinecraft plugin;
    private final DiscordBridge discord;
    private final MessageParser messageParser;

    public MinecraftListener(GarnetMinecraft plugin, DiscordBridge discord) {
        this.plugin = plugin;
        this.discord = discord;
        this.messageParser = new MessageParser(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getConfig().getBoolean("message-settings.chat.enabled", true)) {
            return;
        }

        final String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (plugin.getConfig().getBoolean("message-settings.chat.use-player-skins", false)) {
            discord.sendPlayerChatMessage(event.getPlayer(), message);
        } else {
            final String player = event.getPlayer().getName();
            discord.sendChatMessage(
                    "chat",
                    "**" + player + " »** " + message,
                    Map.of("player", player, "message", message));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(formatAnnouncement("join-message", event.getPlayer().getName(), event.getPlayer()));
        sendEventMessage("join", Map.of("player", event.getPlayer().getName()));
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        event.quitMessage(formatAnnouncement("leave-message", event.getPlayer().getName(), event.getPlayer()));
        sendEventMessage("leave", Map.of("player", event.getPlayer().getName()));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (event.deathMessage() != null) {
            final String message = PlainTextComponentSerializer.plainText().serialize(event.deathMessage());
            sendEventMessage("death", Map.of(
                    "player", event.getEntity().getName(),
                    "message", message));
        }
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        final String advancementKey = event.getAdvancement().getKey().getKey();
        if (advancementKey.startsWith("recipes/")) {
            return;
        }

        if (event.getAdvancement().displayName() == null) {
            return;
        }
        final String advancement = PlainTextComponentSerializer.plainText()
                .serialize(event.getAdvancement().displayName());
        sendEventMessage("advancement", Map.of(
                "player", event.getPlayer().getName(),
                "advancement", advancement));
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        sendEventMessage("kick", Map.of("player", event.getPlayer().getName()));
    }

    private void sendEventMessage(String type, Map<String, String> placeholders) {
        if (plugin.getConfig().getBoolean("embed-settings." + type + ".enabled", true)) {
            discord.sendChatMessage(type, "", placeholders);
        }
    }

    private Component formatAnnouncement(
            String setting,
            String playerName,
            Player player) {
        String message = plugin.getConfig().getString(setting);
        if (message == null) {
            return null;
        }

        message = message.replace("%player%", playerName);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            message = PlaceholderAPI.setPlaceholders(player, message);
        }
        return messageParser.parseMessage(message);
    }
}
