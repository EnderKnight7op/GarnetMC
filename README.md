# GarnetMC

GarnetMC is a standalone Paper plugin. It connects directly to Discord, so it does not need GarnetDiscord
or a separate bot host. Install it only if you run a Minecraft server.

## What it does

- Displays configurable join and leave messages in Minecraft chat.
- Sends Minecraft chat to Discord and Discord chat to Minecraft.
- Sends join, leave, death, advancement, and kick messages to Discord.
- Sends event notifications as customizable Discord embeds.
- Can show Minecraft player skin avatars beside chat messages.
- Adds Discord `/list` and `/msg` commands.
- Can forward server console logs to a separate Discord channel.

## Set up the Discord bot

1. In the [Discord Developer Portal](https://discord.com/developers/applications), create an application and add a bot.
2. On the bot's page, turn on **Message Content Intent**.
3. Invite the bot using the `bot` and `applications.commands` OAuth2 scopes. Give it permission to view channels,
   send messages, embed links, and use slash commands. Also grant **Manage Webhooks** if you enable player skin
   avatars for chat.
4. In Discord, turn on **Developer Mode** under **User Settings → Advanced**. Right-click your server and chat
   channel, then choose **Copy Server ID** and **Copy Channel ID**.
5. Stop your Minecraft server and open `plugins/GarnetMC/config.yml`.
6. Put the bot token and copied IDs into `bot-token`, `server-id`, and `chat-channel-id`. Keep the IDs in quotes.
7. Optionally set `console-channel-id`. Leave it as `"0"` to disable console forwarding.
8. Start the server. Discord `/list` and `/msg` commands are registered when the plugin connects.

Treat the bot token like a password. Do not share it or post it publicly. If it is exposed, reset it in the Developer
Portal. If you also run GarnetDiscord, using separate bot applications keeps their commands and connections separate.
Remove the old `GarnetMinecraft` and `EnderJoinLeaveMessages` plugin JARs before installing GarnetMC. On first startup,
GarnetMC imports the previous config files when available. Use a full server stop and start when installing or updating
this plugin; avoid `/reload` and plugin-manager reloads,
which can close the plugin JAR while Discord's background threads are still running.

## Message formats

Join, leave, death, advancement, kick, server-start, and server-stop notifications are always embeds. Minecraft chat
is always a regular message. Console logs are always regular messages in a `diff` code block; there is no console embed
option.

Set `embed-settings.<type>.enabled: false` to disable any one event embed. Set `message-settings.chat.enabled: false`
to disable chat relaying in both directions, or `message-settings.console.enabled: false` to disable console forwarding.
These switches default to `true`. Set `message-settings.chat.use-player-skins: true` to send Minecraft chat through a
Discord webhook using each player's name and skin avatar; the bot needs **Manage Webhooks** in the chat channel.
This option defaults to `false`.

## Minecraft join and leave messages

The join and leave announcements shown to Minecraft players are configured separately from their Discord embeds:

```yaml
format: legacy
join-message: "&a[+] %player%"
leave-message: "&c[-] %player%"
```

Set `format` to `legacy` for `&` colour codes or `minimessage` for MiniMessage tags. Use `%player%` for the player's
name. If PlaceholderAPI is installed, its placeholders are also expanded. The commands `/garnetmc help`,
`/garnetmc reload`, `/garnetmc set join-message <message>`, and `/garnetmc set leave-message <message>` manage
these settings. The old `/EnderJoinQuitMessages`, `/ejqm`, and `/jqm` command labels remain available as aliases.
Recipe-unlock advancements are excluded from advancement notifications.

## Console logging

Console output is buffered and sent as a live-updating Discord message every two seconds. New lines edit the current
message; a new one is started when the Discord message limit is reached. The output uses a `diff` code block by default,
with level markers, and only forwards the levels listed in `console-log-levels`. GarnetMC only forwards logs; it
does not run commands received in Discord.

```yaml
console-channel-id: "123456789012345678"
console-log-levels: [info, warn, error]
```

The console channel should be restricted to trusted people.

## Customize embeds

Each event's text and appearance are configured under `embed-settings.<type>.embed`. The supported settings are
`description`, `title`, `title-url`, `colour`, `author`, `author-url`, `author-icon`, `footer`, `footer-icon`,
`thumbnail`, `image`, `timestamp`, and `fields`. Only `description` has default text. Use a quoted `#RRGGBB` hex value
for `colour`; blank or invalid values are ignored. Image and icon values must be valid HTTP or HTTPS URLs. Set
`timestamp` to an ISO-8601 date-time. `fields` is a list of name/value pairs with an optional `inline` boolean.
Supported placeholders are `{player}` for player events, `{message}` for the death message, and `{advancement}` for
the advancement's display title. Set `enabled: false` under an event to disable it. The `server-start` message is sent
after Discord connects; `server-stop` is sent only during a normal Paper server shutdown. For example:

```yaml
embed-settings:
  join:
    embed:
      description: "Welcome, {player}!"

      title: "A player joined"
      colour: "#57F287"
      author: "Garnet Minecraft"
      author-url: "https://example.com"
      author-icon: "https://example.com/icon.png"

      footer: "Garnet Minecraft"
      footer-icon: "https://example.com/footer.png"

      thumbnail: "https://example.com/player.png"
      image: "https://example.com/banner.png"

      timestamp: ""
      fields:
        - name: "Player"
          value: "{player}"
          inline: true
```

Existing `event-messages` settings are moved to the corresponding embed descriptions automatically. Restart the server
after editing the config.

## Commands

- `/list` — privately shows the online Minecraft players to the Discord user who ran it.
- `/msg <player> <message>` — sends a private message to an online player. The Discord user needs **Manage Messages**
  permission. The player name must match exactly.

To show player skin avatars beside Minecraft chat messages, set:

```yaml
message-settings:
  chat:
    use-player-skins: true
```

## Build

From the GarnetMinecraft workspace root, run:

```powershell
gradle build
```

Install `build/libs/GarnetMC-1.0.0.jar` in the Paper server's `plugins/` directory.
