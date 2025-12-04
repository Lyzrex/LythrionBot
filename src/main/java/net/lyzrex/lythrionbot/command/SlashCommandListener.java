package net.lyzrex.lythrionbot.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.lyzrex.lythrionbot.ConfigManager;
import net.lyzrex.lythrionbot.db.DatabaseManager;
import net.lyzrex.lythrionbot.i18n.Messages;
import net.lyzrex.lythrionbot.profile.UserProfile;
import net.lyzrex.lythrionbot.profile.UserProfileRepository;
import net.lyzrex.lythrionbot.status.MaintenanceManager;
import net.lyzrex.lythrionbot.status.ServiceStatus;
import net.lyzrex.lythrionbot.status.StatusService;
import net.lyzrex.lythrionbot.ticket.TicketService;
import net.lyzrex.lythrionbot.game.GameScoreRepository;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Haupt-Listener für alle Slash-Commands:
 * /status, /maintenance, /botinfo, /latency, /ticketpanel, /profile, /game
 */
public class SlashCommandListener extends ListenerAdapter {

    private final JDA jda;
    private final StatusService statusService;
    private final MaintenanceManager maintenanceManager;
    private final TicketService ticketService;
    private final UserProfileRepository userRepo;
    private final GameScoreRepository gameRepo;
    private final DatabaseManager databaseManager;
    private final Random random = new Random();

    public SlashCommandListener(JDA jda,
                                StatusService statusService,
                                MaintenanceManager maintenanceManager,
                                TicketService ticketService,
                                UserProfileRepository userRepo,
                                GameScoreRepository gameRepo,
                                DatabaseManager databaseManager) {
        this.jda = jda;
        this.statusService = statusService;
        this.maintenanceManager = maintenanceManager;
        this.ticketService = ticketService;
        this.userRepo = userRepo;
        this.gameRepo = gameRepo;
        this.databaseManager = databaseManager;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String cmd = event.getName();
        Member member = event.getMember();
        boolean admin = member != null && member.hasPermission(Permission.ADMINISTRATOR);

        switch (cmd) {
            case "status" -> handleStatus(event, admin, member);
            case "maintenance" -> handleMaintenance(event, admin, member);
            case "botinfo" -> handleBotInfo(event, admin, member);
            case "latency" -> handleLatency(event, admin, member);
            case "ticketpanel" -> handleTicketPanel(event, admin);
            case "profile" -> handleProfile(event, admin);
            case "game" -> handleGame(event);
            default -> {
                // ignore unknown
            }
        }
    }

    /* ====================================================================== */
    /* /status – Network Status                                               */
    /* ====================================================================== */

    private void handleStatus(SlashCommandInteractionEvent event, boolean admin, Member member) {
        long start = System.currentTimeMillis();
        event.deferReply().queue();

        // HTTP-Abfragen parallel holen
        CompletableFuture<ServiceStatus> mainFuture =
                CompletableFuture.supplyAsync(statusService::fetchMainStatus);
        CompletableFuture<ServiceStatus> lobbyFuture =
                CompletableFuture.supplyAsync(statusService::fetchLobbyStatus);
        CompletableFuture<ServiceStatus> cbFuture =
                CompletableFuture.supplyAsync(statusService::fetchCitybuildStatus);

        CompletableFuture.allOf(mainFuture, lobbyFuture, cbFuture)
                .orTimeout(10, TimeUnit.SECONDS)
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        event.getHook()
                                .editOriginal("❌ Failed to fetch network status (timeout).")
                                .queue();
                        return;
                    }

                    ServiceStatus main = mainFuture.join();
                    ServiceStatus lobby = lobbyFuture.join();
                    ServiceStatus citybuild = cbFuture.join();

                    statusService.updatePresenceFromData(jda, main, lobby, citybuild);
                    MessageEmbed embed = statusService.buildStatusEmbed(main, lobby, citybuild);

                    long dur = System.currentTimeMillis() - start;
                    event.getHook().editOriginalEmbeds(embed).queue();

                    if (admin) {
                        sendDebugDm(member,
                                "🧪 Debug `/status`\n" +
                                        "Exec time: " + dur + "ms");
                    }
                });
    }

    /* ====================================================================== */
    /* /maintenance                                                           */
    /* ====================================================================== */

    private void handleMaintenance(SlashCommandInteractionEvent event, boolean admin, Member member) {
        String sub = event.getSubcommandName();
        if (sub == null) {
            event.reply("❌ Unknown maintenance subcommand.")
                    .setEphemeral(true).queue();
            return;
        }
        switch (sub) {
            case "status" -> handleMaintenanceStatus(event, admin, member);
            case "set" -> handleMaintenanceSet(event, admin, member);
            default -> event.reply("❌ Unknown maintenance subcommand.")
                    .setEphemeral(true).queue();
        }
    }

    private void handleMaintenanceStatus(SlashCommandInteractionEvent event,
                                         boolean admin,
                                         Member member) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🛠️ Lythrion Maintenance Overview")
                .setColor(0xfacc15)
                .setDescription(String.join("\n",
                        "**Velocity (main):** " + maintenanceFlag(maintenanceManager.isMain()),
                        "**Lobby:** " + maintenanceFlag(maintenanceManager.isLobby()),
                        "**Citybuild:** " + maintenanceFlag(maintenanceManager.isCitybuild())
                ))
                .setTimestamp(Instant.now());

        event.replyEmbeds(eb.build()).setEphemeral(true).queue();

        if (admin) {
            sendDebugDm(member, "🧪 Debug `/maintenance status`");
        }
    }

    private void handleMaintenanceSet(SlashCommandInteractionEvent event,
                                      boolean admin,
                                      Member member) {
        if (!admin) {
            event.reply("❌ You need **Administrator** permissions to change maintenance modes.")
                    .setEphemeral(true).queue();
            return;
        }

        String service = event.getOption("service").getAsString();
        boolean enabled = event.getOption("enabled").getAsBoolean();

        switch (service) {
            case "main" -> maintenanceManager.setMain(enabled);
            case "lobby" -> maintenanceManager.setLobby(enabled);
            case "citybuild" -> maintenanceManager.setCitybuild(enabled);
            default -> {
                event.reply("❌ Unknown service `" + service + "`.").setEphemeral(true).queue();
                return;
            }
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🛠️ Maintenance updated")
                .setColor(enabled ? 0xfacc15 : 0x22c55e)
                .setDescription(
                        "Service **" + serviceName(service) + "** is now set to **" +
                                (enabled ? "Maintenance" : "Active") + "**."
                )
                .setTimestamp(Instant.now());

        event.replyEmbeds(eb.build()).setEphemeral(true).queue();

        if (admin) {
            sendDebugDm(member,
                    "🧪 Debug `/maintenance set`\n" +
                            "Service: " + serviceName(service) + "\n" +
                            "New state: " + (enabled ? "Maintenance" : "Active"));
        }
    }

    private String maintenanceFlag(boolean active) {
        return active ? "🟠 Maintenance" : "✅ Active";
    }

    private String serviceName(String key) {
        return switch (key) {
            case "main" -> "Velocity (main)";
            case "lobby" -> "Lobby";
            case "citybuild" -> "Citybuild";
            default -> key;
        };
    }

    /* ====================================================================== */
    /* /botinfo – inkl. "Bot by Lyzrex" & MurmelAPI-DB-Ping                  */
    /* ====================================================================== */

    private void handleBotInfo(SlashCommandInteractionEvent event,
                               boolean admin,
                               Member member) {

        long start = System.currentTimeMillis();

        long wsPing = jda.getGatewayPing();
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        long uptimeMs = runtime.getUptime();
        String uptimeText = formatDuration(uptimeMs);
        int guildCount = jda.getGuilds().size();

        String networkName = ConfigManager.getString("network.name", "Lythrion Network");
        String networkIp = ConfigManager.getString("network.ip", "Lythrion.net");
        String iconUrl = ConfigManager.getString(
                "network.icon",
                "https://api.mcstatus.io/v2/icon/lythrion.net"
        );
        String botVersion = ConfigManager.getString("bot.version", "0.0.1-beta0.1");
        String discordLink = ConfigManager.getString(
                "network.discord",
                "https://discord.gg/7jWqTXzYRz"
        );

        Runtime rt = Runtime.getRuntime();
        long usedMem = rt.totalMemory() - rt.freeMemory();
        String memText = String.format("Used: %.1f MB", usedMem / 1024.0 / 1024.0);

        // DB-Ping (MurmelAPI DB)
        long murmelPing = -1;
        try {
            murmelPing = databaseManager.ping();
        } catch (Exception ignored) {}

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🛰️ " + networkName + " Bot")
                .setColor(0x00bcd4)
                .setThumbnail(iconUrl)
                .setDescription("**Bot by Lyzrex**\n" +
                        "Discord: " + discordLink)
                .addField(
                        "🤖 Bot",
                        String.join("\n",
                                "• **Version:** `" + botVersion + "`",
                                "• **Guilds:** " + guildCount,
                                "• **Commands:** `/status`, `/maintenance`, `/botinfo`, `/latency`, `/ticketpanel`, `/profile`, `/game`"
                        ),
                        false
                )
                .addField(
                        "📡 Runtime",
                        String.join("\n",
                                "• **Gateway ping:** " + wsPing + "ms",
                                "• **Uptime:** " + uptimeText,
                                "• **Java:** " + System.getProperty("java.version"),
                                "• **Memory:** " + memText
                        ),
                        false
                )
                .addField(
                        "🗄️ MurmelAPI",
                        "• **DB ping:** " + (murmelPing >= 0 ? murmelPing + "ms" : "N/A") + "\n" +
                                "_(replace with real MurmelAPI call if you expose one)_",
                        false
                )
                .addField(
                        "🌐 Lythrion Network",
                        String.join("\n",
                                "• **Name:** " + networkName,
                                "• **IP:** `" + networkIp + "`",
                                "• **Status overview:** `/status`"
                        ),
                        false
                )
                .setFooter("Bot by Lyzrex • " + networkName)
                .setTimestamp(Instant.now());

        event.replyEmbeds(eb.build()).setEphemeral(true).queue();

        if (admin) {
            long dur = System.currentTimeMillis() - start;
            sendDebugDm(member,
                    "🧪 Debug `/botinfo`\n" +
                            "Exec time: " + dur + "ms\n" +
                            "Invoker: " + event.getUser().getAsTag() +
                            " (" + event.getUser().getId() + ")");
        }
    }

    /* ====================================================================== */
    /* /latency – Bot / DB / API Ping                                         */
    /* ====================================================================== */

    private void handleLatency(SlashCommandInteractionEvent event,
                               boolean admin,
                               Member member) {
        long wsPing = jda.getGatewayPing();

        long dbPing = -1;
        long lythApiPing = -1;
        long mcStatusPing = -1;

        try {
            dbPing = databaseManager.ping();
        } catch (Exception ignored) {}

        try {
            lythApiPing = statusService.pingLythApi();
        } catch (Exception ignored) {}

        try {
            mcStatusPing = statusService.pingMcStatus();
        } catch (Exception ignored) {}

        // MurmelAPI Ping = aktuell DB-Ping
        long murmelPing = dbPing;

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("📶 Lythrion Latency Overview")
                .setColor(0x22c55e)
                .addField("WebSocket", wsPing + "ms", true)
                .addField("Database", (dbPing >= 0 ? dbPing + "ms" : "N/A"), true)
                .addField("MurmelAPI (DB)", (murmelPing >= 0 ? murmelPing + "ms" : "N/A"), true)
                .addField("Lyth API", (lythApiPing >= 0 ? lythApiPing + "ms" : "N/A"), true)
                .addField("mcstatus.io", (mcStatusPing >= 0 ? mcStatusPing + "ms" : "N/A"), true)
                .setTimestamp(Instant.now());

        event.replyEmbeds(eb.build()).setEphemeral(true).queue();

        if (admin) {
            sendDebugDm(member, "🧪 Debug `/latency`");
        }
    }

    /* ====================================================================== */
    /* /ticketpanel – GalaxyBot-Style Panel                                   */
    /* ====================================================================== */

    private void handleTicketPanel(SlashCommandInteractionEvent event, boolean admin) {
        if (!admin) {
            event.reply("❌ Only administrators may create the ticket panel.")
                    .setEphemeral(true).queue();
            return;
        }
        ticketService.sendTicketPanel(event);
    }

    /* ====================================================================== */
    /* /profile – zieht Daten aus murmelapi.users / user_playtime usw.        */
    /* ====================================================================== */

    private void handleProfile(SlashCommandInteractionEvent event, boolean admin) {
        String input = event.getOption("query").getAsString();

        event.deferReply().queue();

        Optional<UserProfile> opt = userRepo.findByNameOrUuid(input);
        if (opt.isEmpty()) {
            event.getHook().editOriginal("❌ No profile found for `" + input + "`.").queue();
            return;
        }

        UserProfile profile = opt.get();

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("📇 Player Profile – " + profile.getUsername())
                .setColor(0x3b82f6)
                .addField("ID", String.valueOf(profile.getId()), true)
                .addField("UUID", profile.getUuid().toString(), false)
                .addField("First login", profile.getFirstLogin().toString(), true)
                .addField("Language ID", String.valueOf(profile.getLanguageId()), true)
                .addField("Playtime", formatDuration(profile.getPlaytimeSeconds() * 1000L), true)
                .setTimestamp(Instant.now());

        event.getHook().editOriginalEmbeds(eb.build()).queue();

        if (admin) {
            sendDebugDm(event.getMember(),
                    "🧪 Debug `/profile`\n" +
                            "Query: " + input);
        }
    }

    /* ====================================================================== */
    /* /game – kleines Guessing-Game mit Score in DB                          */
    /* ====================================================================== */

    private void handleGame(SlashCommandInteractionEvent event) {
        String sub = event.getSubcommandName();
        if (sub == null || !"guess".equals(sub)) {
            event.reply("❌ Unknown game subcommand.").setEphemeral(true).queue();
            return;
        }

        int secret = random.nextInt(10) + 1;
        int guess = event.getOption("number").getAsInt();

        User user = event.getUser();
        boolean win = (guess == secret);

        gameRepo.addGameResult(user.getIdLong(), win);

        String msg = win
                ? "🎉 Correct! The number was **" + secret + "**."
                : "❌ Wrong. The number was **" + secret + "**.";

        int score = gameRepo.getScore(user.getIdLong());

        event.reply(msg + "\nYour current score: **" + score + "**")
                .setEphemeral(true).queue();
    }

    /* ====================================================================== */
    /* Ticket-Events (SelectMenu / Button) werden von LythrionBot an          */
    /* TicketService delegiert; diese Methoden sind hier nicht nötig.         */
    /* ====================================================================== */

    public void handleTicketSelect(StringSelectInteractionEvent event) {
        ticketService.handleCategorySelect(event);
    }

    public void handleTicketClose(ButtonInteractionEvent event) {
        ticketService.handleCloseButton(event);
    }

    /* ====================================================================== */
    /* Helper                                                                 */
    /* ====================================================================== */

    private void sendDebugDm(Member member, String msg) {
        if (member == null) return;
        member.getUser()
                .openPrivateChannel()
                .queue(ch -> ch.sendMessage(msg).queue(), err -> {});
    }

    private String formatDuration(long ms) {
        long totalSeconds = ms / 1000;
        long days = totalSeconds / 86400;
        totalSeconds %= 86400;
        long hours = totalSeconds / 3600;
        totalSeconds %= 3600;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0 || sb.length() > 0) sb.append(hours).append("h ");
        if (minutes > 0 || sb.length() > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        return sb.toString().trim();
    }
}
