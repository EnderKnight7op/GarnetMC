package com.garnetminecraft;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;

public class GarnetCommands implements CommandExecutor {
    private final GarnetMinecraft plugin;

    public GarnetCommands(GarnetMinecraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            if (!hasPermission(sender, "help")) {
                sender.sendMessage("You don't have permission to do that.");
                return true;
            }
            sender.sendMessage("GarnetMC commands:");
            sender.sendMessage("/garnetmc set join-message <message>");
            sender.sendMessage("/garnetmc set leave-message <message>");
            sender.sendMessage("/garnetmc reload");
            return true;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            if (!hasPermission(sender, "reload")) {
                sender.sendMessage("You don't have permission to do that.");
                return true;
            }
            try {
                plugin.reloadPluginConfigs();
                sender.sendMessage("GarnetMC configuration reloaded!");
            } catch (IOException | InvalidConfigurationException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not reload GarnetMC configuration.", exception);
                sender.sendMessage("Could not reload GarnetMC configuration. Check the server log.");
            }
            return true;
        }

        if ("set".equalsIgnoreCase(args[0]) && args.length >= 3) {
            if (!hasPermission(sender, "set")) {
                sender.sendMessage("You don't have permission to do that.");
                return true;
            }

            final String setting;
            if ("join-message".equalsIgnoreCase(args[1])) {
                setting = "join-message";
            } else if ("leave-message".equalsIgnoreCase(args[1])) {
                setting = "leave-message";
            } else {
                sender.sendMessage("Choose join-message or leave-message.");
                return true;
            }

            final String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            plugin.getJoinLeaveConfig().set(setting, message);
            try {
                plugin.saveJoinLeaveConfig();
            } catch (IOException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not save the join/leave configuration.", exception);
                sender.sendMessage("Could not save the message. Check the server log.");
                return true;
            }
            sender.sendMessage(("join-message".equals(setting) ? "Join" : "Leave") + " message updated!");
            return true;
        }

        sender.sendMessage("Usage: /garnetmc <help|reload|set>");
        return true;
    }

    private boolean hasPermission(CommandSender sender, String action) {
        return sender.hasPermission("garnetmc.command." + action)
                || sender.hasPermission("enderjoinquitmessages.command." + action);
    }
}
