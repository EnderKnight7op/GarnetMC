package com.garnetminecraft;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.logging.Level;

public class DiscordCommands extends ListenerAdapter {
    private final GarnetMinecraft plugin;

    public DiscordCommands(GarnetMinecraft plugin) {
        this.plugin = plugin;
    }

    public void register(Guild guild) {
        guild.upsertCommand(Commands.slash("list", "Show the players currently online."))
                .queue(null, error -> plugin.getLogger().log(Level.WARNING, "Could not register /list.", error));
        guild.upsertCommand(Commands.slash("msg", "Send a private message to an online player.")
                        .addOptions(
                                new OptionData(OptionType.STRING, "player", "The exact Minecraft player name.", true),
                                new OptionData(OptionType.STRING, "message", "The message to send.", true)))
                .queue(null, error -> plugin.getLogger().log(Level.WARNING, "Could not register /msg.", error));
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.isFromGuild() || event.getGuild().getIdLong() != plugin.getChannelId("server-id")) {
            return;
        }

        if (event.getName().equals("list")) {
            showPlayers(event);
        } else if (event.getName().equals("msg")) {
            sendPrivateMessage(event);
        }
    }

    private void showPlayers(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue(hook -> Bukkit.getScheduler().runTask(plugin, () -> {
            final List<Player> players = List.copyOf(Bukkit.getOnlinePlayers());
            if (players.isEmpty()) {
                editReply(hook, "There are no players online.");
                return;
            }

            final String names = players.stream()
                    .map(Player::getName)
                    .reduce((first, second) -> first + ", " + second)
                    .orElse("");
            editReply(hook, "Online (" + players.size() + "): " + names);
        }), error -> plugin.getLogger().log(Level.WARNING, "Could not acknowledge /list.", error));
    }

    private void sendPrivateMessage(SlashCommandInteractionEvent event) {
        final Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.MESSAGE_MANAGE)) {
            event.reply("You need the Manage Messages permission to use this command.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        final String playerName = event.getOption("player").getAsString();
        final String message = event.getOption("message").getAsString();
        final String sender = member.getEffectiveName();
        event.deferReply(true).queue(hook -> Bukkit.getScheduler().runTask(plugin, () -> {
            final Player player = Bukkit.getPlayerExact(playerName);
            if (player == null) {
                editReply(hook, "That player is not online. Player names must match exactly.");
                return;
            }

            player.sendMessage("[Discord] " + sender + " whispers to you: " + message);
            editReply(hook, "Message sent to " + player.getName() + ".");
        }), error -> plugin.getLogger().log(Level.WARNING, "Could not acknowledge /msg.", error));
    }

    private void editReply(InteractionHook hook, String message) {
        hook.editOriginal(message)
                .queue(null, error -> plugin.getLogger().log(Level.WARNING, "Could not send a Discord command reply.", error));
    }
}
