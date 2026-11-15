package com.garnetminecraft;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;

public class GarnetMinecraft extends JavaPlugin {
    private DiscordBridge discord;
    private ConsoleRelay consoleRelay;
    private FileConfiguration joinLeaveConfig;
    private FileConfiguration broadcastConfig;
    private boolean startedSuccessfully;

    @Override
    public void onEnable() {
        try {
            migrateLegacyPluginConfig();
            saveDefaultConfig();
            loadFeatureConfigs();
            migrateBridgeSettings();
            migrateEmbedSettings();
            migrateEventMessages();
            migrateAdvancementDescription();
            removeObsoleteFormatSettings();
            getConfig().options().copyDefaults(true);
            saveConfig();
        } catch (IOException | InvalidConfigurationException exception) {
            getLogger().log(Level.SEVERE, "Could not load or migrate GarnetMC configuration.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            final PluginCommand command = getCommand("garnetmc");
            if (command == null) {
                throw new IllegalStateException("The garnetmc command is missing from plugin.yml.");
            }
            command.setExecutor(new GarnetCommands(this));

            discord = new DiscordBridge(this);
            discord.start();

            getServer().getPluginManager().registerEvents(new MinecraftListener(this, discord), this);

            if (getChannelId("console-channel-id") != 0
                    && getConfig().getBoolean("message-settings.console.enabled", true)) {
                consoleRelay = new ConsoleRelay(this, discord);
                consoleRelay.start();
            }
            startedSuccessfully = true;
            sendLifecycleMessage("server-start");
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            getLogger().log(Level.SEVERE, "GarnetMC could not start. Check config.yml and the bot setup.", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void loadFeatureConfigs() throws IOException, InvalidConfigurationException {
        final File joinLeaveFile = new File(getDataFolder(), "join-leave.yml");
        final boolean createJoinLeaveConfig = !joinLeaveFile.exists();
        if (createJoinLeaveConfig) {
            saveResource("join-leave.yml", false);
        }
        joinLeaveConfig = loadYamlConfiguration(joinLeaveFile);
        if (createJoinLeaveConfig) {
            migrateJoinLeaveConfig();
        }

        final File broadcastFile = new File(getDataFolder(), "broadcast.yml");
        final boolean createBroadcastConfig = !broadcastFile.exists();
        if (createBroadcastConfig) {
            saveResource("broadcast.yml", false);
        }
        broadcastConfig = loadYamlConfiguration(broadcastFile);
        if (createBroadcastConfig
                && getConfig().contains("message-settings.chat.enabled", true)
                && !getConfig().getBoolean("message-settings.chat.enabled", true)) {
            broadcastConfig.set("enabled", false);
            broadcastConfig.save(broadcastFile);
        }

        removeLegacyJoinLeaveSettings();
    }

    private FileConfiguration loadYamlConfiguration(File file) throws IOException, InvalidConfigurationException {
        final YamlConfiguration configuration = new YamlConfiguration();
        configuration.load(file);
        return configuration;
    }

    private void migrateJoinLeaveConfig() throws IOException, InvalidConfigurationException {
        final File legacyFile = new File(
                new File(getDataFolder().getParentFile(), "EnderJoinLeaveMessages"),
                "config.yml");
        if (legacyFile.isFile()) {
            copyJoinLeaveSettings(loadYamlConfiguration(legacyFile));
        } else {
            copyJoinLeaveSettings(getConfig());
        }

        joinLeaveConfig.save(new File(getDataFolder(), "join-leave.yml"));
        getLogger().info("Created join-leave.yml with available existing message settings.");
    }

    private void copyJoinLeaveSettings(FileConfiguration source) {
        for (String setting : new String[] {"format", "join-message", "leave-message"}) {
            if (source.contains(setting, true)) {
                joinLeaveConfig.set(setting, source.get(setting));
            }
        }
    }

    private void removeLegacyJoinLeaveSettings() throws IOException {
        boolean removed = false;
        for (String setting : new String[] {"format", "join-message", "leave-message"}) {
            if (getConfig().contains(setting, true)) {
                getConfig().set(setting, null);
                removed = true;
            }
        }
        if (removed) {
            saveConfig();
        }
    }

    public FileConfiguration getJoinLeaveConfig() {
        return joinLeaveConfig;
    }

    public FileConfiguration getBroadcastConfig() {
        return broadcastConfig;
    }

    public void saveJoinLeaveConfig() throws IOException {
        joinLeaveConfig.save(new File(getDataFolder(), "join-leave.yml"));
    }

    public void reloadPluginConfigs() throws IOException, InvalidConfigurationException {
        reloadConfig();
        loadFeatureConfigs();
    }

    private void migrateLegacyPluginConfig() throws IOException {
        final File currentConfig = new File(getDataFolder(), "config.yml");
        if (currentConfig.exists()) {
            return;
        }

        final File legacyConfig = new File(
                new File(getDataFolder().getParentFile(), "GarnetMinecraft"),
                "config.yml");
        if (!legacyConfig.isFile()) {
            return;
        }

        if (!getDataFolder().mkdirs() && !getDataFolder().isDirectory()) {
            throw new IOException("Could not create the GarnetMC plugin data folder.");
        }
        Files.copy(legacyConfig.toPath(), currentConfig.toPath());
        getLogger().info("Copied the existing GarnetMinecraft config to the GarnetMC data folder.");
    }

    private void migrateBridgeSettings() {
        if (!getConfig().contains("discord-guild-id", true)) {
            return;
        }

        String guildId = getConfig().getString("discord-guild-id");
        if (guildId != null) {
            getConfig().set("server-id", guildId);
        }
        if (!getConfig().contains("bot-token", true)) {
            getConfig().set("bot-token", "PASTE_BOT_TOKEN_HERE");
        }
        getConfig().set("discord-guild-id", null);
        getConfig().set("bot-url", null);
        getConfig().set("shared-secret", null);
        saveConfig();
        getLogger().warning("Converted the previous Garnet bridge settings. Add your Discord bot token and verify server-id.");
    }

    private void migrateEventMessages() {
        if (!getConfig().contains("event-messages", true)) {
            return;
        }

        final String[] eventTypes = {
                "join", "leave", "death", "advancement", "kick", "server-start", "server-stop"
        };
        for (String type : eventTypes) {
            final String oldPath = "event-messages." + type;
            if (!getConfig().contains(oldPath, true)) {
                continue;
            }

            final String descriptionPath = "embed-settings." + type + ".embed.description";
            final String enabledPath = "embed-settings." + type + ".enabled";
            final String oldMessage = getConfig().getString(oldPath, "");
            if (!getConfig().contains(descriptionPath, true)) {
                getConfig().set(descriptionPath, oldMessage);
            }
            if (oldMessage.isBlank() && !getConfig().contains(enabledPath, true)) {
                getConfig().set(enabledPath, false);
            }
        }

        getConfig().set("event-messages", null);
        saveConfig();
        getLogger().info("Moved event message templates to embed-settings.");
    }

    private void migrateAdvancementDescription() {
        final String path = "embed-settings.advancement.embed.description";
        if ("{player} earned {advancement}.".equals(getConfig().getString(path, null))) {
            getConfig().set(path, "{player} has made the advancement [{advancement}].");
            saveConfig();
            getLogger().info("Updated the default advancement message to use the advancement display title.");
        }
    }

    private void migrateEmbedSettings() {
        final String[] eventTypes = {
                "join", "leave", "death", "advancement", "kick", "server-start", "server-stop"
        };
        boolean migrated = false;
        for (String type : eventTypes) {
            final String oldPath = "embed-settings." + type + ".";
            final String newPath = oldPath + "embed.";
            migrated |= moveSetting(oldPath + "description", newPath + "description");
            migrated |= moveSetting(oldPath + "title", newPath + "title");
            migrated |= moveSetting(oldPath + "author", newPath + "author");
            migrated |= moveSetting(oldPath + "colour", newPath + "colour");
            migrated |= moveSetting(oldPath + "color", newPath + "colour");
            for (String obsoleteSetting : new String[] {
                    "author-url", "author-icon", "footer", "footer-icon", "thumbnail", "image"
            }) {
                migrated |= moveSetting(oldPath + obsoleteSetting, newPath + obsoleteSetting);
            }
        }

        if (migrated) {
            saveConfig();
            getLogger().info("Moved embed options under each event's embed section.");
        }
    }

    private boolean moveSetting(String oldPath, String newPath) {
        if (!getConfig().contains(oldPath, true)) {
            return false;
        }
        if (!getConfig().contains(newPath, true)) {
            getConfig().set(newPath, getConfig().get(oldPath));
        }
        getConfig().set(oldPath, null);
        return true;
    }

    private void removeObsoleteFormatSettings() {
        boolean removed = false;
        for (String setting : new String[] {"embed-message-types", "console-use-code-blocks"}) {
            if (getConfig().contains(setting, true)) {
                getConfig().set(setting, null);
                removed = true;
            }
        }
        if (removed) {
            saveConfig();
            getLogger().info("Removed obsolete message format options; event and console formats are now fixed.");
        }
    }

    @Override
    public void onDisable() {
        if (consoleRelay != null) {
            consoleRelay.stop();
        }
        if (startedSuccessfully && Bukkit.isStopping()) {
            sendLifecycleMessage("server-stop");
        }
        if (discord != null) {
            discord.stop();
        }
    }

    private void sendLifecycleMessage(String type) {
        if (getConfig().getBoolean("embed-settings." + type + ".enabled", true) && discord != null) {
            if ("server-stop".equals(type)) {
                discord.sendChatMessageAndWait(type, "", java.util.Collections.emptyMap());
            } else {
                discord.sendChatMessage(type, "", java.util.Collections.emptyMap());
            }
        }
    }

    long getChannelId(String setting) {
        final String value = getConfig().getString(setting, "0").trim();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("The config value '" + setting + "' must be a Discord ID.", exception);
        }
    }
}
