package net.lyzrex.lythrionbot.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.lyzrex.lythrionbot.ConfigManager;
import net.lyzrex.lythrionbot.db.DatabaseManager;
import net.lyzrex.lythrionbot.game.GameService;
import net.lyzrex.lythrionbot.murmel.MurmelApiClient;
import net.lyzrex.lythrionbot.murmel.MurmelApiStatus;
import net.lyzrex.lythrionbot.profile.UserProfile;
import net.lyzrex.lythrionbot.profile.UserProfileRepository;
import net.lyzrex.lythrionbot.status.MaintenanceManager;
import net.lyzrex.lythrionbot.status.ServiceStatus;
import net.lyzrex.lythrionbot.status.StatusService;
import net.lyzrex.lythrionbot.ticket.TicketService;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Vollständige Implementierung des Slash-Command-Listeners. Enthält alle
 * registrierten Kommandos (Utility, Netzwerk, Profile, Spiele, Moderation)
 * und die MurmelAPI-Integration in einem einzigen Einstiegspunkt:
 * /status, /maintenance, /botinfo, /latency, /ping, /help, /ticketpanel,
 * /profile, /rps, /moderation.
 */
public class SlashCommandListener extends ListenerAdapter {

    private final JDA jda;
    private final StatusService statusService;
    private final MaintenanceManager maintenanceManager;
    private final TicketService ticketService;
    private final UserProfileRepository userRepo;
    private final GameService gameService;
    private final DatabaseManager databaseManager;
    private final MurmelApiClient murmelApiClient;

    public SlashCommandListener(JDA jda,
                                StatusService statusService,
                                MaintenanceManager maintenanceManager,
                                TicketService ticketService,
                                UserProfileRepository userRepo,
                                GameService gameService,
                                DatabaseManager databaseManager,
                                MurmelApiClient murmelApiClient) {
        this.jda = jda;
        this.statusService = statusService;
        this.maintenanceManager = maintenanceManager;
        this.ticketService = ticketService;
        this.userRepo = userRepo;
        this.gameService = gameService;
        this.databaseManager = databaseManager;
        this.murmelApiClient = murmelApiClient;
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
            case "ping" -> handlePing(event, admin, member);
            case "help" -> handleHelp(event);
            case "moderation" -> handleModeration(event, admin, member);
            case "ticketpanel" -> handleTicketPanel(event, admin);
            case "profile" -> handleProfile(event, admin);
            case "rps" -> handleRps(event);
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
                    var embed = statusService.buildStatusEmbed(main, lobby, citybuild);

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

        OptionMapping serviceOpt = event.getOption("service");
        OptionMapping enabledOpt = event.getOption("enabled");

        if (serviceOpt == null || enabledOpt == null) {
            event.reply("❌ Missing required options `service` or `enabled`.")
                    .setEphemeral(true).queue();
            return;
        }

        String service = serviceOpt.getAsString();
        boolean enabled = enabledOpt.getAsBoolean();

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

        event.deferReply(true).queue();

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

        murmelApiClient.checkHealthAsync()
                .completeOnTimeout(new MurmelApiStatus(false, -1L, 0, "MurmelAPI health check timed out"), 7, TimeUnit.SECONDS)
                .whenComplete((murmelStatus, throwable) -> {
                    MurmelApiStatus status = throwable == null ? murmelStatus
                            : new MurmelApiStatus(false, -1L, 0, "MurmelAPI request failed");

                    String murmelDetails = String.join("\n",
                            "• Endpoint: " + murmelApiClient.getHealthUrl(),
                            "• " + status.describe(),
                            "• Repo: https://github.com/Murmelmeister/MurmelAPI",
                            "• Author: Murmelmeister / PyjamaMurmeli"
                    );

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
                                            "• **Commands:** `/status`, `/maintenance`, `/botinfo`, `/latency`, `/ping`, `/help`, `/ticketpanel`, `/profile`, `/rps`, `/moderation`"
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
                                    murmelDetails,
                                    false
                            )
                            .addField(
                                    "💡 Credits",
                                    "• Bot code: by Lyzrex\n" +
                                            "• MurmelAPI: Murmelmeister/PyjamaMurmeli",
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

                    event.getHook().editOriginalEmbeds(eb.build()).queue();

                    if (admin) {
                        long dur = System.currentTimeMillis() - start;
                        sendDebugDm(member,
                                "🧪 Debug `/botinfo`\n" +
                                        "Exec time: " + dur + "ms\n" +
                                        "Invoker: " + event.getUser().getAsTag() +
                                        " (" + event.getUser().getId() + ")");
                    }
                });
    }

    /* ====================================================================== */
    /* /latency – Bot / DB / API Ping                                         */
    /* ====================================================================== */

    private void handleLatency(SlashCommandInteractionEvent event,
                               boolean admin,
                               Member member) {
        event.deferReply(true).queue();

        long wsPing = jda.getGatewayPing();
        MurmelApiStatus fallbackStatus = new MurmelApiStatus(false, -1L, 0, "MurmelAPI health check pending...");

        CompletableFuture<MurmelApiStatus> murmelFuture = murmelApiClient.checkHealthAsync()
                .completeOnTimeout(new MurmelApiStatus(false, -1L, 0, "MurmelAPI health check timed out"), 7, TimeUnit.SECONDS);
        CompletableFuture<Long> dbFuture = CompletableFuture.supplyAsync(databaseManager::ping)
                .exceptionally(ex -> -1L);
        CompletableFuture<Long> statusApiFuture = CompletableFuture.supplyAsync(statusService::pingStatusApi)
                .exceptionally(ex -> -1L);

        CompletableFuture.allOf(murmelFuture, dbFuture, statusApiFuture)
                .orTimeout(7, TimeUnit.SECONDS)
                .whenComplete((ignored, throwable) -> {
                    MurmelApiStatus murmelStatus = murmelFuture.getNow(fallbackStatus);
                    long dbPing = dbFuture.getNow(-1L);
                    long statusApiPing = statusApiFuture.getNow(-1L);

                    String murmelField = String.join("\n",
                            "Status: " + murmelStatus.describeBrief(),
                            "Details: " + murmelStatus.message()
                    );

                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle("📶 Lythrion Latency Overview")
                            .setColor(0x22c55e)
                            .addField("WebSocket", wsPing + "ms", true)
                            .addField("Database", (dbPing >= 0 ? dbPing + "ms" : "N/A"), true)
                            .addField("MurmelAPI", murmelField, false)
                            .addField("Status API", (statusApiPing >= 0 ? statusApiPing + "ms" : "N/A"), true)
                            .addField("MurmelAPI Endpoint", murmelApiClient.getHealthUrl(), false)
                            .setTimestamp(Instant.now());

                    event.getHook().editOriginalEmbeds(eb.build()).queue();

                    if (admin) {
                        sendDebugDm(member, "🧪 Debug `/latency`");
                    }
                });
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
        OptionMapping option = event.getOption("input");
        if (option == null) {
            event.reply("❌ Missing required option `input`.").setEphemeral(true).queue();
            return;
        }
        String input = option.getAsString();

        event.deferReply().queue();

        Optional<UserProfile> opt = userRepo.findByNameOrUuid(input);
        if (opt.isEmpty()) {
            event.getHook().editOriginal("❌ No profile found for `" + input + "`.").queue();
            return;
        }

        UserProfile profile = opt.get();

        String firstLoginText = profile.getFirstLogin() != null
                ? profile.getFirstLogin().toString()
                : "Unknown";

        String languageText = profile.getLanguageName() != null
                ? profile.getLanguageName() + " (#" + profile.getLanguageId() + ")"
                : "#" + profile.getLanguageId();

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("📇 Player Profile – " + profile.getUsername())
                .setColor(0x3b82f6)
                .addField("ID", String.valueOf(profile.getId()), true)
                .addField("UUID", profile.getUuid(), false)
                .addField("First login", firstLoginText, true)
                .addField("Language", languageText, true)
                .addField("Playtime", formatDuration(profile.getPlaytimeSeconds() * 1000L), true)
                .addField("Logins", String.valueOf(profile.getLoginCount()), true)
                .addField("Debug", profile.isDebugEnabled() ? "Enabled" : "Disabled", true)
                .setTimestamp(Instant.now());

        event.getHook().editOriginalEmbeds(eb.build()).queue();

        if (admin) {
            sendDebugDm(event.getMember(),
                    "🧪 Debug `/profile`\n" +
                            "Query: " + input);
        }
    }

    /* ====================================================================== */
    /* /rps – Rock Paper Scissors with stats                                  */
    /* ====================================================================== */

    private void handleRps(SlashCommandInteractionEvent event) {
        String sub = event.getSubcommandName();
        if (sub == null) {
            event.reply("❌ Please choose a subcommand (play, stats or top).").setEphemeral(true).queue();
            return;
        }

        switch (sub) {
            case "play" -> gameService.handleRpsPlay(event);
            case "stats" -> gameService.handleRpsStats(event);
            case "top" -> gameService.handleRpsTop(event);
            default -> event.reply("❌ Unknown RPS subcommand.").setEphemeral(true).queue();
        }
    }

    /* ====================================================================== */
    /* /ping – compact latency + credits                                      */
    /* ====================================================================== */

    private void handlePing(SlashCommandInteractionEvent event, boolean admin, Member member) {
        event.deferReply(true).queue();

        long wsPing = jda.getGatewayPing();
        CompletableFuture<Long> dbFuture = CompletableFuture.supplyAsync(databaseManager::ping)
                .exceptionally(ex -> -1L);
        CompletableFuture<MurmelApiStatus> murmelFuture = murmelApiClient.checkHealthAsync()
                .completeOnTimeout(new MurmelApiStatus(false, -1L, 0, "MurmelAPI health check timed out"), 7, TimeUnit.SECONDS);

        CompletableFuture.allOf(dbFuture, murmelFuture)
                .whenComplete((ignored, throwable) -> {
                    long dbPing = dbFuture.getNow(-1L);
                    MurmelApiStatus murmelStatus = murmelFuture.getNow(new MurmelApiStatus(false, -1L, 0, "no response"));

                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle("🏓 Pong!")
                            .setColor(0x22c55e)
                            .setDescription("Detailed latency overview")
                            .addField("Gateway", wsPing + "ms", true)
                            .addField("Database", dbPing >= 0 ? dbPing + "ms" : "N/A", true)
                            .addField("MurmelAPI", murmelStatus.describeBrief(), false)
                            .addField("Author", "Bot by **Lyzrex**", true)
                            .addField("MurmelAPI", "by Murmelmeister / PyjamaMurmeli", true)
                            .addField("GitHub", "https://github.com/Murmelmeister/MurmelAPI", false)
                            .setTimestamp(Instant.now());

                    event.getHook().editOriginalEmbeds(eb.build()).queue();

                    if (admin) {
                        sendDebugDm(member, "🧪 Debug `/ping`");
                    }
                });
    }

    /* ====================================================================== */
    /* /help – command overview + credits                                     */
    /* ====================================================================== */

    private void handleHelp(SlashCommandInteractionEvent event) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🤖 Lythrion.net Help")
                .setColor(0x3b82f6)
                .setDescription("Here are all available commands and functions.")
                .addField("Utility", "`/ping`, `/help`, `/latency`, `/botinfo`", false)
                .addField("Network", "`/status`, `/maintenance`, `/ticketpanel`", false)
                .addField("Profiles", "`/profile <name|uuid>`", false)
                .addField("Games", "`/rps play|stats|top`", false)
                .addField("Moderation", "`/moderation ban|kick|timeout|unban`", false)
                .addField("Credits", "Bot by **Lyzrex** • MurmelAPI by **Murmelmeister/PyjamaMurmeli**", false)
                .addField("MurmelAPI GitHub", "https://github.com/Murmelmeister/MurmelAPI", false)
                .setTimestamp(Instant.now());

        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    /* ====================================================================== */
    /* /moderation – ban, kick, timeout, unban                                */
    /* ====================================================================== */

    private void handleModeration(SlashCommandInteractionEvent event, boolean admin, Member member) {
        String sub = event.getSubcommandName();
        if (sub == null) {
            event.reply("❌ Unknown moderation subcommand.").setEphemeral(true).queue();
            return;
        }

        if (!event.isFromGuild()) {
            event.reply("❌ Moderation commands can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        switch (sub) {
            case "ban" -> handleBan(event, member);
            case "kick" -> handleKick(event, member);
            case "timeout" -> handleTimeout(event, member);
            case "unban" -> handleUnban(event, member);
            default -> event.reply("❌ Unknown moderation subcommand.").setEphemeral(true).queue();
        }
    }

    private void handleBan(SlashCommandInteractionEvent event, Member executor) {
        if (executor == null || !executor.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("❌ You need BAN_MEMBERS to use this.").setEphemeral(true).queue();
            return;
        }

        Member target = event.getOption("user") != null ? event.getOption("user").getAsMember() : null;
        String reason = Optional.ofNullable(event.getOption("reason")).map(OptionMapping::getAsString).orElse("No reason provided");
        int deleteDays = Optional.ofNullable(event.getOption("delete_days")).map(OptionMapping::getAsInt).orElse(0);

        if (target == null) {
            event.reply("❌ Could not resolve that member.").setEphemeral(true).queue();
            return;
        }

        event.getGuild().ban(target, deleteDays, reason).queue(
                ignored -> event.reply("✅ Banned **" + target.getEffectiveName() + "** (" + reason + ")").setEphemeral(true).queue(),
                err -> event.reply("❌ Failed to ban: " + err.getMessage()).setEphemeral(true).queue()
        );
    }

    private void handleKick(SlashCommandInteractionEvent event, Member executor) {
        if (executor == null || !executor.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("❌ You need KICK_MEMBERS to use this.").setEphemeral(true).queue();
            return;
        }

        Member target = event.getOption("user") != null ? event.getOption("user").getAsMember() : null;
        String reason = Optional.ofNullable(event.getOption("reason")).map(OptionMapping::getAsString).orElse("No reason provided");

        if (target == null) {
            event.reply("❌ Could not resolve that member.").setEphemeral(true).queue();
            return;
        }

        target.kick(reason).queue(
                ignored -> event.reply("✅ Kicked **" + target.getEffectiveName() + "** (" + reason + ")").setEphemeral(true).queue(),
                err -> event.reply("❌ Failed to kick: " + err.getMessage()).setEphemeral(true).queue()
        );
    }

    private void handleTimeout(SlashCommandInteractionEvent event, Member executor) {
        if (executor == null || !executor.hasPermission(Permission.MODERATE_MEMBERS)) {
            event.reply("❌ You need MODERATE_MEMBERS to use this.").setEphemeral(true).queue();
            return;
        }

        Member target = event.getOption("user") != null ? event.getOption("user").getAsMember() : null;
        int minutes = Optional.ofNullable(event.getOption("minutes")).map(OptionMapping::getAsInt).orElse(0);
        String reason = Optional.ofNullable(event.getOption("reason")).map(OptionMapping::getAsString).orElse("No reason provided");

        if (target == null || minutes <= 0) {
            event.reply("❌ Provide a valid member and timeout duration (minutes).").setEphemeral(true).queue();
            return;
        }

        target.timeoutFor(Duration.ofMinutes(minutes)).reason(reason).queue(
                ignored -> event.reply("✅ Timed out **" + target.getEffectiveName() + "** for " + minutes + " minutes.").setEphemeral(true).queue(),
                err -> event.reply("❌ Failed to timeout: " + err.getMessage()).setEphemeral(true).queue()
        );
    }

    private void handleUnban(SlashCommandInteractionEvent event, Member executor) {
        if (executor == null || !executor.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("❌ You need BAN_MEMBERS to use this.").setEphemeral(true).queue();
            return;
        }

        OptionMapping opt = event.getOption("user_id");
        if (opt == null) {
            event.reply("❌ Provide a user ID to unban.").setEphemeral(true).queue();
            return;
        }

        String userId = opt.getAsString();
        event.getGuild().unban(userId).queue(
                ignored -> event.reply("✅ Unbanned user ID " + userId).setEphemeral(true).queue(),
                err -> event.reply("❌ Failed to unban: " + err.getMessage()).setEphemeral(true).queue()
        );
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
