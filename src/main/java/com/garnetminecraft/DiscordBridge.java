package com.garnetminecraft;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class DiscordBridge {
    private static final String PLAYER_CHAT_WEBHOOK_NAME = "GarnetMinecraft Player Chat";
    private static final Set<String> EVENT_EMBED_TYPES = Set.of(
            "join", "leave", "death", "advancement", "kick", "server-start", "server-stop");

    private final GarnetMinecraft plugin;
    private final MessageParser messageParser;
    private JDA jda;
    private Guild guild;
    private Webhook playerChatWebhook;

    public DiscordBridge(GarnetMinecraft plugin) {
        this.plugin = plugin;
        this.messageParser = new MessageParser();
    }

    public void start() throws InterruptedException {
        final String token = plugin.getConfig().getString("bot-token", "").trim();
        if (token.isEmpty() || token.equals("PASTE_BOT_TOKEN_HERE")) {
            throw new IllegalArgumentException("Add your Discord bot token to config.yml.");
        }

        final DiscordCommands commands = new DiscordCommands(plugin);
        jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(commands)
                .addEventListeners(new ListenerAdapter() {
                    @Override
                    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
                        relayDiscordMessage(event);
                    }
                })
                .build();
        jda.awaitReady();

        final long guildId = plugin.getChannelId("server-id");
        guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("The bot could not find the Discord server. Check server-id in config.yml.");
        }

        final TextChannel chatChannel = guild.getTextChannelById(plugin.getChannelId("chat-channel-id"));
        if (chatChannel == null) {
            throw new IllegalArgumentException("The bot could not find chat-channel-id in the selected server.");
        }

        initializePlayerChatWebhook(chatChannel);
        commands.register(guild);
        plugin.getLogger().info("Connected to Discord as " + jda.getSelfUser().getName() + ".");
    }

    public void sendChatMessage(String type, String message) {
        sendChatMessage(type, message, Collections.singletonMap("message", message));
    }

    public void sendChatMessage(String type, String message, Map<String, String> placeholders) {
        sendMessage(type, plugin.getChannelId("chat-channel-id"), message, withMessage(message, placeholders));
    }

    public void sendPlayerChatMessage(Player player, String message) {
        final Webhook webhook = playerChatWebhook;
        if (webhook == null) {
            final String name = player.getName();
            sendChatMessage("chat", "**" + name + " »** " + message, Map.of(
                    "player", name,
                    "message", message));
            return;
        }

        webhook.sendMessage(message)
                .setUsername(player.getName())
                .setAvatarUrl("https://mc-heads.net/avatar/" + player.getUniqueId() + "/128")
                .setAllowedMentions(Collections.<Message.MentionType>emptyList())
                .queue(null, error -> plugin.getLogger().log(Level.WARNING, "Could not send player chat with a skin avatar to Discord.", error));
    }

    public void sendChatMessageAndWait(String type, String message, Map<String, String> placeholders) {
        final MessageCreateAction action = createMessage(
                type,
                plugin.getChannelId("chat-channel-id"),
                message,
                withMessage(message, placeholders));
        if (action == null) {
            return;
        }

        try {
            action.timeout(5, TimeUnit.SECONDS).complete();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not send the Discord " + type + " message before shutdown.", exception);
        }
    }

    public Message sendOrEditConsoleMessage(Message previousMessage, String content) {
        if (jda == null || guild == null) {
            return null;
        }

        final long channelId = plugin.getChannelId("console-channel-id");
        if (channelId == 0) {
            return null;
        }

        final TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) {
            plugin.getLogger().warning("A configured Discord channel was not found: " + channelId);
            return null;
        }

        final boolean useEmbed = usesEmbed("console");
        final MessageEmbed embed = useEmbed
                ? new EmbedBuilder().setDescription(content).build()
                : null;
        if (previousMessage != null) {
            try {
                if (useEmbed) {
                    return previousMessage.editMessageEmbeds(embed)
                            .setAllowedMentions(Collections.<Message.MentionType>emptyList())
                            .timeout(5, TimeUnit.SECONDS)
                            .complete();
                }
                return previousMessage.editMessage(content)
                        .setAllowedMentions(Collections.<Message.MentionType>emptyList())
                        .timeout(5, TimeUnit.SECONDS)
                        .complete();
            } catch (ErrorResponseException exception) {
                if (exception.getErrorResponse() != ErrorResponse.UNKNOWN_MESSAGE) {
                    plugin.getLogger().log(Level.WARNING, "Could not update the Discord console log.", exception);
                    return null;
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not update the Discord console log.", exception);
                return null;
            }
        }

        try {
            if (useEmbed) {
                return channel.sendMessageEmbeds(embed)
                        .setAllowedMentions(Collections.<Message.MentionType>emptyList())
                        .timeout(5, TimeUnit.SECONDS)
                        .complete();
            }
            return channel.sendMessage(content)
                    .setAllowedMentions(Collections.<Message.MentionType>emptyList())
                    .timeout(5, TimeUnit.SECONDS)
                    .complete();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not send the Discord console log.", exception);
            return null;
        }
    }

    public void stop() {
        playerChatWebhook = null;
        if (jda != null) {
            final JDA connection = jda;
            jda = null;
            guild = null;
            connection.shutdownNow();
            try {
                if (!connection.awaitShutdown(10, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("Discord connection did not shut down within 10 seconds.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                plugin.getLogger().log(Level.WARNING, "Interrupted while waiting for the Discord connection to shut down.", exception);
            }
        }
    }

    private void initializePlayerChatWebhook(TextChannel channel) {
        if (!plugin.getConfig().getBoolean("message-settings.chat.use-player-skins", false)) {
            return;
        }

        try {
            final List<Webhook> webhooks = channel.retrieveWebhooks().timeout(5, TimeUnit.SECONDS).complete();
            final String botId = jda.getSelfUser().getId();
            final String webhookName = PLAYER_CHAT_WEBHOOK_NAME + " " + botId;
            playerChatWebhook = webhooks.stream()
                    .filter(webhook -> webhookName.equals(webhook.getName()))
                    .filter(webhook -> webhook.getOwnerAsUser() == null
                            || botId.equals(webhook.getOwnerAsUser().getId()))
                    .findFirst()
                    .orElseGet(() -> channel.createWebhook(webhookName)
                            .timeout(5, TimeUnit.SECONDS)
                            .complete());
            plugin.getLogger().info("Player skin avatars are enabled for Minecraft chat.");
        } catch (RuntimeException exception) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Could not set up player skin chat webhooks. Verify that the bot has Manage Webhooks permission in the chat channel; regular chat formatting will be used.",
                    exception);
        }
    }

    private void sendMessage(String type, long channelId, String message, Map<String, String> placeholders) {
        final MessageCreateAction action = createMessage(type, channelId, message, placeholders);
        if (action != null) {
            action.queue(null, error -> plugin.getLogger().log(Level.WARNING, "Could not send a message to Discord.", error));
        }
    }

    private MessageCreateAction createMessage(
            String type,
            long channelId,
            String message,
            Map<String, String> placeholders) {
        if (jda == null || channelId == 0) {
            return null;
        }

        final TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) {
            plugin.getLogger().warning("A configured Discord channel was not found: " + channelId);
            return null;
        }

        final MessageCreateAction action;
        if (usesEmbed(type)) {
            final MessageEmbed embed = buildEmbed(type, message, placeholders);
            if (embed == null) {
                return null;
            }
            action = channel.sendMessageEmbeds(embed);
        } else {
            action = channel.sendMessage(message);
        }
        return action.setAllowedMentions(Collections.<Message.MentionType>emptyList());
    }

    private MessageEmbed buildEmbed(
            String type,
            String message,
            Map<String, String> placeholders) {
        if (!EVENT_EMBED_TYPES.contains(type)) {
            return new EmbedBuilder().setDescription(message).build();
        }

        final String path = "embed-settings." + type + ".embed.";
        final EmbedBuilder builder = new EmbedBuilder();
        final String title = resolveTemplate(plugin.getConfig().getString(path + "title", ""), placeholders);
        final String titleUrl = configuredUrl(path + "title-url", type, placeholders);
        final String description = resolveTemplate(
                plugin.getConfig().getString(path + "description", ""),
                placeholders);
        final String author = resolveTemplate(plugin.getConfig().getString(path + "author", ""), placeholders);
        final String authorUrl = configuredUrl(path + "author-url", type, placeholders);
        final String authorIcon = configuredUrl(path + "author-icon", type, placeholders);
        final String footer = resolveTemplate(plugin.getConfig().getString(path + "footer", ""), placeholders);
        final String footerIcon = configuredUrl(path + "footer-icon", type, placeholders);
        final String thumbnail = configuredUrl(path + "thumbnail", type, placeholders);
        final String image = configuredUrl(path + "image", type, placeholders);
        final String timestamp = plugin.getConfig().getString(path + "timestamp", "").trim();

        if (!title.isBlank()) {
            builder.setTitle(title, titleUrl);
        }
        if (!description.isBlank()) {
            builder.setDescription(description);
        }
        if (!author.isBlank()) {
            builder.setAuthor(author, authorUrl, authorIcon);
        }
        if (!footer.isBlank()) {
            builder.setFooter(footer, footerIcon);
        }
        if (thumbnail != null) {
            builder.setThumbnail(thumbnail);
        }
        if (image != null) {
            builder.setImage(image);
        }
        if (!timestamp.isEmpty()) {
            try {
                builder.setTimestamp(Instant.parse(timestamp));
            } catch (DateTimeParseException exception) {
                plugin.getLogger().warning("Invalid embed timestamp for '" + type + "': " + timestamp + ". Use ISO-8601.");
            }
        }

        boolean hasFields = false;
        int fieldCount = 0;
        for (Map<?, ?> field : plugin.getConfig().getMapList(path + "fields")) {
            final Object name = field.get("name");
            final Object value = field.get("value");
            if (name == null || value == null || name.toString().isBlank() || value.toString().isBlank()) {
                plugin.getLogger().warning("Embed field for '" + type + "' must have a non-empty name and value.");
                continue;
            }
            if (fieldCount >= 25) {
                plugin.getLogger().warning("Embed for '" + type + "' has more than 25 fields; remaining fields were skipped.");
                break;
            }
            try {
                builder.addField(
                        resolveTemplate(name.toString(), placeholders),
                        resolveTemplate(value.toString(), placeholders),
                        Boolean.parseBoolean(String.valueOf(field.get("inline"))));
                fieldCount++;
                hasFields = true;
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().log(Level.WARNING, "Invalid embed field for '" + type + "'; skipping it.", exception);
            }
        }

        final String colour = plugin.getConfig().getString(path + "colour", "").trim();
        if (!colour.isEmpty()) {
            if (colour.matches("#[0-9a-fA-F]{6}")) {
                builder.setColor(Color.decode(colour));
            } else {
                plugin.getLogger().warning("Invalid embed colour for '" + type + "': " + colour + ". Use #RRGGBB.");
            }
        }

        if (title.isBlank() && description.isBlank() && author.isBlank() && footer.isBlank()
                && thumbnail == null && image == null && !hasFields) {
            if (message.isBlank()) {
                plugin.getLogger().warning("Embed settings for '" + type + "' contain no visible content; skipping message.");
                return null;
            }
            builder.setDescription(message);
        }
        return builder.build();
    }

    private Map<String, String> withMessage(String message, Map<String, String> placeholders) {
        final Map<String, String> result = new HashMap<>(placeholders);
        result.putIfAbsent("message", message);
        return result;
    }

    private String resolveTemplate(String template, Map<String, String> placeholders) {
        String resolved = template;
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            resolved = resolved.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
        }
        return resolved;
    }

    private String configuredUrl(String path, String type, Map<String, String> placeholders) {
        final String value = configuredTemplate(path, placeholders).trim();
        if (value.isEmpty()) {
            return null;
        }

        try {
            final URI uri = URI.create(value);
            if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null) {
                return value;
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid optional URLs are skipped so a bad config does not prevent event messages.
        }
        plugin.getLogger().warning("Invalid embed URL for '" + type + "': " + value);
        return null;
    }

    private String configuredTemplate(String path, Map<String, String> placeholders) {
        return resolveTemplate(plugin.getConfig().getString(path, ""), placeholders);
    }

    private boolean usesEmbed(String type) {
        return EVENT_EMBED_TYPES.contains(type);
    }

    private void relayDiscordMessage(MessageReceivedEvent event) {
        if (!plugin.getBroadcastConfig().getBoolean("enabled", true)
                || event.getAuthor().isBot()
                || event.isWebhookMessage()
                || event.getChannel().getIdLong() != plugin.getChannelId("chat-channel-id")) {
            return;
        }

        final String name = event.getMember() == null
                ? event.getAuthor().getName()
                : event.getMember().getEffectiveName();
        final String message = event.getMessage().getContentDisplay();
        final String template = plugin.getBroadcastConfig().getString(
                "message",
                "&7[&9Discord&7] &f{user}&7: &f{message}");
        final String rendered = template
                .replace("{user}", name)
                .replace("{message}", message);
        final String format = plugin.getBroadcastConfig().getString("format", "legacy");
        final String minecraftMessage = LegacyComponentSerializer.legacySection()
                .serialize(messageParser.parseMessage(rendered, format));

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(minecraftMessage);
            }
        });
    }
}
