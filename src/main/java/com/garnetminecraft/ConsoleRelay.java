package com.garnetminecraft;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.bukkit.scheduler.BukkitTask;
import net.dv8tion.jda.api.entities.Message;

import java.util.concurrent.ConcurrentLinkedQueue;

public class ConsoleRelay {
    private static final String APPENDER_NAME = "GarnetMinecraftConsoleRelay";
    private static final int MAX_MESSAGE_LENGTH = 1900;
    private static final String CODE_BLOCK_PREFIX = "```diff\n";
    private static final String CODE_BLOCK_SUFFIX = "\n```";
    private static final int MAX_CONTENT_LENGTH = MAX_MESSAGE_LENGTH
            - CODE_BLOCK_PREFIX.length() - CODE_BLOCK_SUFFIX.length();

    private final GarnetMinecraft plugin;
    private final DiscordBridge discord;
    private final ConcurrentLinkedQueue<String> pendingLines = new ConcurrentLinkedQueue<>();
    private final StringBuilder currentMessageContent = new StringBuilder();
    private LoggerContext loggerContext;
    private ConsoleAppender appender;
    private BukkitTask flushTask;
    private Message currentDiscordMessage;

    public ConsoleRelay(GarnetMinecraft plugin, DiscordBridge discord) {
        this.plugin = plugin;
        this.discord = discord;
    }

    public void start() {
        loggerContext = (LoggerContext) LogManager.getContext(false);
        final LoggerConfig rootLogger = loggerContext.getConfiguration().getRootLogger();
        appender = new ConsoleAppender();
        appender.start();
        rootLogger.addAppender(appender, Level.ALL, null);
        loggerContext.updateLoggers();

        flushTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::sendPending, 40L, 40L);
    }

    public void stop() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        if (loggerContext != null && appender != null) {
            loggerContext.getConfiguration().getRootLogger().removeAppender(APPENDER_NAME);
            loggerContext.updateLoggers();
            appender.stop();
            appender = null;
            loggerContext = null;
        }
        sendPending();
    }

    private synchronized void sendPending() {
        String line;
        while ((line = pendingLines.poll()) != null) {
            final String originalLine = line;
            if (line.length() > MAX_CONTENT_LENGTH) {
                line = line.substring(0, MAX_CONTENT_LENGTH);
            }

            if (currentMessageContent.length() > 0
                    && currentMessageContent.length() + line.length() + 1 > MAX_CONTENT_LENGTH) {
                if (!publishCurrentMessage()) {
                    pendingLines.offer(originalLine);
                    return;
                }
                currentMessageContent.setLength(0);
                currentDiscordMessage = null;
            }

            if (currentMessageContent.length() > 0) {
                currentMessageContent.append('\n');
            }
            currentMessageContent.append(line);
        }
        if (currentMessageContent.length() > 0) {
            publishCurrentMessage();
        }
    }

    private boolean publishCurrentMessage() {
        final String content = CODE_BLOCK_PREFIX + currentMessageContent + CODE_BLOCK_SUFFIX;
        final Message updatedMessage = discord.sendOrEditConsoleMessage(currentDiscordMessage, content);
        if (updatedMessage == null) {
            return false;
        }
        currentDiscordMessage = updatedMessage;
        return true;
    }

    private boolean acceptsLevel(Level level) {
        final String configuredLevel = level == Level.FATAL ? "error" : level.name().toLowerCase();
        return plugin.getConfig().getStringList("console-log-levels").stream()
                .anyMatch(value -> value.equalsIgnoreCase(configuredLevel));
    }

    private class ConsoleAppender extends AbstractAppender {
        private ConsoleAppender() {
            super(APPENDER_NAME, null, PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            final String loggerName = event.getLoggerName();
            if (acceptsLevel(event.getLevel())
                    && (loggerName == null || !loggerName.startsWith("com.garnetminecraft"))) {
                final String marker = event.getLevel() == Level.ERROR || event.getLevel() == Level.FATAL
                        ? "-"
                        : event.getLevel() == Level.WARN ? "!" : "+";
                final String logger = loggerName == null ? "" : loggerName + ": ";
                pendingLines.offer(marker + " [" + event.getLevel() + "] " + logger
                        + event.getMessage().getFormattedMessage());
            }
        }
    }
}
