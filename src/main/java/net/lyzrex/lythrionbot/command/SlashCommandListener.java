package net.lyzrex.lythrionbot.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.lyzrex.lythrionbot.ConfigManager;
import net.lyzrex.lythrionbot.db.DatabaseManager;
import net.lyzrex.lythrionbot.game.GameScore;
import net.lyzrex.lythrionbot.game.GameScoreRepository;
import net.lyzrex.lythrionbot.moderation.ModerationEntry;
import net.lyzrex.lythrionbot.moderation.ModerationRepository;
import net.lyzrex.lythrionbot.profile.UserProfile;
import net.lyzrex.lythrionbot.profile.UserProfileRepository;
import net.lyzrex.lythrionbot.status.MaintenanceManager;
import net.lyzrex.lythrionbot.status.ServiceStatus;
import net.lyzrex.lythrionbot.status.ServiceType;
import net.lyzrex.lythrionbot.status.StatusService;
import net.lyzrex.lythrionbot.ticket.TicketService;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class SlashCommandListener extends ListenerAdapter {

    private final JDA jda;
    private final StatusService statusService;
    private final MaintenanceManager maintenanceManager;
    private final UserProfileRepository userRepo;
    private final DatabaseManager databaseManager;
    private final TicketService ticketService;
    private final GameScoreRepository gameRepo;
    private final ModerationRepository moderationRepo;
    private final long moderationLogChannelId;

    public SlashCommandListener(JDA jda,
                                StatusService statusService,
                                MaintenanceManager maintenanceManager,
                                UserProfileRepository userRepo,
                                DatabaseManager databaseManager,
                                TicketService ticketService,
                                GameScoreRepository gameRepo,
                                ModerationRepository moderationRepo,
                                long moderationLogChannelId) {
        this.jda = jda;
        this.statusService = statusService;
        this.maintenanceManager = maintenanceManager;
        this.userRepo = userRepo;
        this.databaseManager = databaseManager;
        this.ticketService = ticketService;
        this.gameRepo = gameRepo;
        this.moderationRepo = moderationRepo;
        this.moderationLogChannelId = moderationLogChannelId;
    }

    // ------------------------------------------------------------------------
    // Slash commands
    // ------------------------------------------------------------------------

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String cmd = event.getName();
        Member member = event.getMember();
        boolean admin = member != null && member.hasPermission(Permission.ADMINISTRATOR);

        switch (cmd) {
            case "status" -> handleStatus(event, admin, member);
            case "maintenance" -> handleMaintenance(event, admin, member);
            case "botinfo" -> handleBotInfo(event, admin, member);
            case "profile" -> handleProfile(event, admin, member);
            case "latency" -> handleLatency(event, admin, member);
            case "leaderboard" -> handleLeaderboard(event, admin, member);
            case "links" -> handleLinks(event, admin, member);
            case "ticketpanel" -> handleTicketPanel(event, admin, member);

            case "coinflip" -> handleCoinflip(event);
            case "roll" -> handleRoll(event);
            case "rps" -> handleRps(event);
            case "gamescore" -> handleGameScore(event);

            case "clear" -> handleClear(event, admin, member);
            case "kick" -> handleKick(event, admin, member);
            case "warn" -> handleWarn(event, admin, member);
            case "timeout" -> handleTimeout(event, admin, member);
            case "ban" -> handleBan(event, admin, member);
            case "modlog" -> handleModlog(event, admin, member);

            default -> {
            }
        }
    }

    // ticket components
    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        ticketService.handleCategorySelect(event);
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        ticketService.handleCloseButton(event);
    }

    // ------------------------------------------------------------------------
    // /status
    // ------------------------------------------------------------------------

    private void handleStatus(SlashCommandInteractionEvent event, boolean admin, Member member) {
        long start = System.currentTimeMillis();
        event.deferReply().queue();

        StatusService.StatusTriple triple = statusService.fetchAllFast();
        ServiceStatus main = triple.main();
        ServiceStatus lobby = triple.lobby();
        ServiceStatus citybuild = triple.citybuild();

        statusService.updatePresenceFromData(jda, main, lobby, citybuild);

        event.getHook().editOriginalEmbeds(
                statusService.buildStatusEmbed(main, lobby, citybuild)
        ).queue();

        if (admin) {
            long dur = System.currentTimeMillis() - start;
            sendDebugDm(member, "🧪 Debug `/status`\nExec time: " + dur + "ms");
        }
    }

    // ------------------------------------------------------------------------
    // /maintenance
    // ------------------------------------------------------------------------

    private void handleMaintenance(SlashCommandInteractionEvent event, boolean admin, Member member) {
        String sub = event.getSubcommandName();
        if (sub == null) {
            event.reply("❌ Unknown maintenance subcommand.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        switch (sub) {
            case "status" -> handleMaintenanceStatus(event, admin, member);
            case "set" -> handleMaintenanceSet(event, admin, member);
            default -> event.reply("❌ Unknown maintenance subcommand.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void handleMaintenanceStatus(SlashCommandInteractionEvent event, boolean admin, Member member) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🛠️ Lythrion Maintenance Overview")
                .setColor(0xfacc15)
                .setDescription(String.join("\n",
                        "**Velocity (main):** " +
                                maintenanceFlag(maintenanceManager.isMaintenance(ServiceType.MAIN)),
                        "**Lobby:** " +
                                maintenanceFlag(maintenanceManager.isMaintenance(ServiceType.LOBBY)),
                        "**Citybuild:** " +
                                maintenanceFlag(maintenanceManager.isMaintenance(ServiceType.CITYBUILD))
                ))
                .setTimestamp(Instant.now());

        event.replyEmbeds(eb.build())
                .setEphemeral(true)
                .queue();

        if (admin) {
            sendDebugDm(member, "🧪 Debug `/maintenance status`");
        }
    }

    private void handleMaintenanceSet(SlashCommandInteractionEvent event, boolean admin, Member member) {
        if (!admin) {
            event.reply("❌ You need **Administrator** permission to change maintenance modes.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String service = event.getOption("service").getAsString();
        boolean enabled = event.getOption("enabled").getAsBoolean();

        switch (service) {
            case "main" -> maintenanceManager.setMaintenance(ServiceType.MAIN, enabled);
            case "lobby" -> maintenanceManager.setMaintenance(ServiceType.LOBBY, enabled);
            case "citybuild" -> maintenanceManager.setMaintenance(ServiceType.CITYBUILD, enabled);
            default -> {
                event.reply("❌ Unknown service `" + service + "`.")
                        .setEphemeral(true)
                        .queue();
                return;
            }
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🛠️ Maintenance updated")
                .setColor(enabled ? 0xfacc15 : 0x22c55e)
                .setDescription("Service **" + serviceName(service) + "** is now set to **" +
                        (enabled ? "Maintenance" : "Active") + "**.")
                .setTimestamp(Instant.now());

        event.replyEmbeds(eb.build())
                .setEphemeral(true)
                .queue();

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

    // ------------------------------------------------------------------------
    // /botinfo
    // ------------------------------------------------------------------------

    private void handleBotInfo(SlashCommandInteractionEvent event, boolean admin, Member member) {
        long start = System.currentTimeMillis();

        long wsPing = jda.getGatewayPing();
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        long uptimeMs = runtime.getUptime();
        String uptimeText = formatDuration(uptimeMs);
        int guildCount = jda.getGuilds().size();

        String networkName = ConfigManager.getString("network.name", "Lythrion Network");
        String networkIp = ConfigManager.getString("network.ip", "Lythrion.net");
        String iconUrl = ConfigManager.getString("network.icon", "https://api.mcstatus.io/v2/icon/lythrion.net");
        String botVersion = ConfigManager.getString("bot.version", "0.0.1-beta0.1");
        String discordLink = ConfigManager.getString("network.discord", "https://discord.gg/yourInvite");

        Runtime rt = Runtime.getRuntime();
        long usedMem = rt.totalMemory() - rt.freeMemory();
        String memText = String.format("Used: %.1f MB", usedMem / 1024.0 / 1024.0);

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🛰️ " + networkName + " Bot")
                .setColor(0x00bcd4)
                .setThumbnail(iconUrl)
                .setDescription("**Bot by Lyzrex**\nDiscord: " + discordLink)
                .addField(
                        "🤖 Bot",
                        String.join("\n",
                                "• **Version:** `" + botVersion + "`",
                                "• **Guilds:** " + guildCount,
                                "• **Commands:** `/status`, `/maintenance`, `/botinfo`, `/profile`, `/latency`, `/leaderboard`, `/links`, `/ticketpanel`, `/coinflip`, `/roll`, `/rps`, `/gamescore`, `/clear`, `/kick`, `/warn`, `/timeout`, `/ban`, `/modlog`"
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

        event.replyEmbeds(eb.build())
                .setEphemeral(true)
                .queue();

        if (admin) {
            long dur = System.currentTimeMillis() - start;
            sendDebugDm(member,
                    "🧪 Debug `/botinfo`\n" +
                            "Exec time: " + dur + "ms\n" +
                            "Invoker: " + event.getUser().getAsTag() +
                            " (" + event.getUser().getId() + ")");
        }
    }

    // ------------------------------------------------------------------------
    // /profile
    // ------------------------------------------------------------------------

    private void handleProfile(SlashCommandInteractionEvent event, boolean admin, Member member) {
        String input = event.getOption("name_or_uuid").getAsString();
        event.deferReply(true).queue();

        Optional<UserProfile> opt = userRepo.findByNameOrUuid(input);
        if (opt.isEmpty()) {
            event.getHook().editOriginal("❌ No profile found for `" + input + "`").queue();
            if (admin) {
                sendDebugDm(member, "🧪 Debug `/profile` – not found: " + input);
            }
            return;
        }

        UserProfile profile = opt.get();

        String langName = switch (profile.getLanguageId()) {
            case 1 -> "English";
            case 2 -> "Deutsch";
            default -> "Unknown (" + profile.getLanguageId() + ")";
        };

        String playtime = formatDuration(profile.getPlayTimeSeconds() * 1000L);

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🎮 Player Profile: " + profile.getUsername())
                .setColor(0x3b82f6)
                .addField("User ID", String.valueOf(profile.getId()), true)
                .addField("UUID", profile.getUuid(), true)
                .addField("Language", langName, true)
                .addField("Playtime", playtime, true)
                .addField("Login count", String.valueOf(profile.getLoginCount()), true)
                .addField("Debug enabled", profile.isDebugEnabled() ? "Yes" : "No", true)
                .setTimestamp(Instant.now());

        event.getHook().editOriginalEmbeds(eb.build()).queue();

        if (admin) {
            sendDebugDm(member,
                    "🧪 Debug `/profile`\n" +
                            "Target: " + profile.getUsername() +
                            " (#" + profile.getId() + ")");
        }
    }

    // ------------------------------------------------------------------------
    // /latency
    // ------------------------------------------------------------------------

    private void handleLatency(SlashCommandInteractionEvent event, boolean admin, Member member) {
        long start = System.currentTimeMillis();
        event.deferReply(true).queue();

        long gatewayPing = jda.getGatewayPing();

        long dbPing;
        try (var conn = databaseManager.getConnection();
             var st = conn.createStatement()) {
            long t0 = System.nanoTime();
            st.execute("SELECT 1");
            dbPing = (System.nanoTime() - t0) / 1_000_000L;
        } catch (Exception e) {
            dbPing = -1;
        }
        String dbText = (dbPing >= 0) ? dbPing + "ms" : "N/A";

        StatusService.StatusTriple triple = statusService.fetchAllFast();
        ServiceStatus main = triple.main();
        ServiceStatus lobby = triple.lobby();
        ServiceStatus citybuild = triple.citybuild();

        long apiPing = statusService.pingApi("http://138.201.19.210:8765/status?token=ServiceLobbyStatus");
        String apiText = (apiPing >= 0) ? apiPing + "ms" : "N/A";

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("📡 Lythrion Latency Overview")
                .setColor(0x22c55e)
                .setDescription("Real latency for bot, database and Lythrion services.")
                .addField(
                        "🤖 Bot",
                        "• **Gateway ping:** " + gatewayPing + "ms\n" +
                                "• **DB ping:** " + dbText,
                        false
                )
                .addField(
                        "🌐 LythCore API",
                        "• **Status endpoint:** " + apiText,
                        false
                )
                .addField(
                        "⚙️ Services",
                        String.join("\n",
                                "• **Velocity:** " + main.getPingMs() + "ms " + (main.isOnline() ? "🟢" : "🔴"),
                                "• **Lobby:** " + lobby.getPingMs() + "ms " + (lobby.isOnline() ? "🟢" : "🔴"),
                                "• **Citybuild:** " + citybuild.getPingMs() + "ms " + (citybuild.isOnline() ? "🟢" : "🔴")
                        ),
                        false
                )
                .setFooter("Lythrion Network • Latency monitor")
                .setTimestamp(Instant.now());

        event.getHook().editOriginalEmbeds(eb.build()).queue();

        if (admin) {
            long dur = System.currentTimeMillis() - start;
            sendDebugDm(member, "🧪 Debug `/latency`\nExec time: " + dur + "ms");
        }
    }

    // ------------------------------------------------------------------------
    // /leaderboard
    // ------------------------------------------------------------------------

    private void handleLeaderboard(SlashCommandInteractionEvent event, boolean admin, Member member) {
        String sub = event.getSubcommandName();
        if (sub == null) {
            event.reply("❌ Unknown leaderboard subcommand.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.deferReply().queue();
        int limit = 10;
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(0x8b5cf6)
                .setTimestamp(Instant.now());

        if (sub.equals("playtime")) {
            List<UserProfile> top = userRepo.findTopByPlaytime(limit);
            if (top.isEmpty()) {
                event.getHook().editOriginal("No playtime data available.").queue();
                return;
            }

            StringBuilder sb = new StringBuilder();
            int pos = 1;
            for (UserProfile p : top) {
                String playtime = formatDuration(p.getPlayTimeSeconds() * 1000L);
                sb.append("**#").append(pos++).append("** ")
                        .append(p.getUsername())
                        .append(" – ").append(playtime)
                        .append("\n");
            }

            eb.setTitle("🏆 Lythrion Leaderboard – Playtime")
                    .setDescription(sb.toString())
                    .setFooter("Top " + top.size() + " players by playtime");

            event.getHook().editOriginalEmbeds(eb.build()).queue();

            if (admin) {
                sendDebugDm(member, "🧪 Debug `/leaderboard playtime`");
            }
        } else if (sub.equals("logins")) {
            List<UserProfile> top = userRepo.findTopByLoginCount(limit);
            if (top.isEmpty()) {
                event.getHook().editOriginal("No login data available.").queue();
                return;
            }

            StringBuilder sb = new StringBuilder();
            int pos = 1;
            for (UserProfile p : top) {
                sb.append("**#").append(pos++).append("** ")
                        .append(p.getUsername())
                        .append(" – ")
                        .append(p.getLoginCount()).append(" logins")
                        .append("\n");
            }

            eb.setTitle("🏆 Lythrion Leaderboard – Logins")
                    .setDescription(sb.toString())
                    .setFooter("Top " + top.size() + " players by logins");

            event.getHook().editOriginalEmbeds(eb.build()).queue();

            if (admin) {
                sendDebugDm(member, "🧪 Debug `/leaderboard logins`");
            }
        } else {
            event.getHook().editOriginal("❌ Unknown leaderboard subcommand.").queue();
        }
    }

    // ------------------------------------------------------------------------
    // /links
    // ------------------------------------------------------------------------

    private void handleLinks(SlashCommandInteractionEvent event, boolean admin, Member member) {
        String networkName = ConfigManager.getString("network.name", "Lythrion Network");
        String website = ConfigManager.getString("network.website", "https://example.com");
        String store = ConfigManager.getString("network.store", "https://store.example.com");
        String docs = ConfigManager.getString("network.docs", "https://docs.example.com");
        String discord = ConfigManager.getString("network.discord", "https://discord.gg/yourInvite");

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🔗 " + networkName + " Links")
                .setColor(0x10b981)
                .setDescription("Useful links for the Lythrion community.")
                .addField("🌐 Website", website, false)
                .addField("🛒 Store", store, false)
                .addField("📚 Docs / Wiki", docs, false)
                .addField("💬 Discord", discord, false)
                .setFooter(networkName + " • Official links")
                .setTimestamp(Instant.now());

        event.replyEmbeds(eb.build()).queue();

        if (admin) {
            sendDebugDm(member, "🧪 Debug `/links`");
        }
    }

    // ------------------------------------------------------------------------
    // /ticketpanel
    // ------------------------------------------------------------------------

    private void handleTicketPanel(SlashCommandInteractionEvent event, boolean admin, Member member) {
        if (!admin) {
            event.reply("❌ You need **Administrator** permission to send the ticket panel.")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        ticketService.sendTicketPanel(event);
    }

    // ------------------------------------------------------------------------
    // Fun commands
    // ------------------------------------------------------------------------

    private void handleCoinflip(SlashCommandInteractionEvent event) {
        boolean heads = ThreadLocalRandom.current().nextBoolean();
        String result = heads ? "Heads" : "Tails";

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🪙 Coinflip")
                .setColor(0xf97316)
                .setDescription("You flipped: **" + result + "**")
                .setTimestamp(Instant.now());

        event.replyEmbeds(eb.build()).queue();
    }

    private void handleRoll(SlashCommandInteractionEvent event) {
        int sides = 6;
        if (event.getOption("sides") != null) {
            long v = event.getOption("sides").getAsLong();
            if (v < 2) v = 2;
            if (v > 1000) v = 1000;
            sides = (int) v;
        }

        int result = ThreadLocalRandom.current().nextInt(1, sides + 1);

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🎲 Dice Roll")
                .setColor(0x38bdf8)
                .setDescription("You rolled **" + result + "** (1–" + sides + ")")
                .setTimestamp(Instant.now());

        event.replyEmbeds(eb.build()).queue();
    }

    // ------------------------------------------------------------------------
    // /rps and /gamescore
    // ------------------------------------------------------------------------

    private void handleRps(SlashCommandInteractionEvent event) {
        String choice = event.getOption("choice").getAsString().toLowerCase();
        String userChoice = switch (choice) {
            case "rock" -> "Rock";
            case "paper" -> "Paper";
            case "scissors" -> "Scissors";
            default -> null;
        };

        if (userChoice == null) {
            event.reply("❌ Invalid choice. Use Rock, Paper or Scissors.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String[] options = {"Rock", "Paper", "Scissors"};
        String botChoice = options[ThreadLocalRandom.current().nextInt(options.length)];

        GameScoreRepository.Outcome outcome;
        String resultText;

        if (userChoice.equals(botChoice)) {
            outcome = GameScoreRepository.Outcome.DRAW;
            resultText = "It's a draw!";
        } else if (
                (userChoice.equals("Rock") && botChoice.equals("Scissors")) ||
                        (userChoice.equals("Paper") && botChoice.equals("Rock")) ||
                        (userChoice.equals("Scissors") && botChoice.equals("Paper"))
        ) {
            outcome = GameScoreRepository.Outcome.WIN;
            resultText = "You win!";
        } else {
            outcome = GameScoreRepository.Outcome.LOSS;
            resultText = "You lose!";
        }

        long userId = event.getUser().getIdLong();
        gameRepo.addRpsResult(userId, outcome);

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("✊📄✂ Rock-Paper-Scissors")
                .setColor(0x22c55e)
                .setDescription(String.join("\n",
                        "You chose **" + userChoice + "**",
                        "Bot chose **" + botChoice + "**",
                        "",
                        resultText
                ))
                .setTimestamp(Instant.now());

        event.replyEmbeds(eb.build()).queue();
    }

    private void handleGameScore(SlashCommandInteractionEvent event, boolean admin, Member member) {
        String sub = event.getSubcommandName();
        if (sub == null) {
            event.reply("❌ Unknown gamescore subcommand.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (!sub.equals("rps")) {
            event.reply("❌ Unknown gamescore subcommand.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.deferReply().queue();

        List<GameScore> top = gameRepo.getTopRps(10);
        if (top.isEmpty()) {
            event.getHook().editOriginal("No RPS game scores available yet.").queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        int pos = 1;
        for (GameScore gs : top) {
            User user = jda.getUserById(gs.getUserId());
            String name = (user != null) ? user.getAsTag() : String.valueOf(gs.getUserId());

            sb.append("**#").append(pos++).append("** ")
                    .append(name)
                    .append(" – ")
                    .append(gs.getWins()).append("W / ")
                    .append(gs.getLosses()).append("L / ")
                    .append(gs.getDraws()).append("D")
                    .append(" (").append(gs.getTotalGames()).append(" games)")
                    .append("\n");
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🏆 RPS Leaderboard")
                .setColor(0xfacc15)
                .setDescription(sb.toString())
                .setFooter("Top " + top.size() + " players by RPS performance")
                .setTimestamp(Instant.now());

        event.getHook().editOriginalEmbeds(eb.build()).queue();

        if (admin) {
            sendDebugDm(member, "🧪 Debug `/gamescore rps`");
        }
    }

    // ------------------------------------------------------------------------
    // Moderation
    // ------------------------------------------------------------------------

    private void handleClear(SlashCommandInteractionEvent event, boolean admin, Member member) {
        if (member == null ||
                !(admin || member.hasPermission(Permission.MESSAGE_MANAGE))) {
            event.reply("❌ You need `Manage Messages` permission to use this.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        int amount = (int) event.getOption("amount").getAsLong();
        if (amount < 1) amount = 1;
        if (amount > 100) amount = 100;

        event.deferReply(true).queue();

        if (!(event.getChannel() instanceof TextChannel textChannel)) {
            event.getHook().editOriginal("❌ This command can only be used in text channels.").queue();
            return;
        }

        textChannel.getHistory().retrievePast(amount).queue(
                messages -> {
                    messages.forEach(m -> m.delete().queue());
                    event.getHook().editOriginal("🧹 Deleted " + messages.size() + " messages.").queue();
                },
                error -> event.getHook().editOriginal("❌ Failed to delete messages.").queue()
        );
    }

    private void handleKick(SlashCommandInteractionEvent event, boolean admin, Member member) {
        if (member == null ||
                !(admin || member.hasPermission(Permission.KICK_MEMBERS))) {
            event.reply("❌ You need `Kick Members` permission to use this.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Member target = event.getOption("user").getAsMember();
        if (target == null) {
            event.reply("❌ User not found.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String reason = event.getOption("reason") != null
                ? event.getOption("reason").getAsString()
                : "No reason provided";

        event.deferReply(true).queue();

        target.kick(reason).queue(
                v -> event.getHook()
                        .editOriginal("✅ Kicked " + target.getAsMention() + " (`" + reason + "`)").queue(),
                err -> event.getHook()
                        .editOriginal("❌ Failed to kick user: " + err.getMessage()).queue()
        );
    }

    private void handleWarn(SlashCommandInteractionEvent event, boolean admin, Member member) {
        if (member == null ||
                !(admin || member.hasPermission(Permission.MODERATE_MEMBERS))) {
            event.reply("❌ You need `Moderate Members` permission to use this.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("❌ This command can only be used in a guild.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Member target = event.getOption("user").getAsMember();
        if (target == null) {
            event.reply("❌ User not found.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String reason = event.getOption("reason").getAsString();

        moderationRepo.logWarn(
                guild.getIdLong(),
                target.getIdLong(),
                member.getIdLong(),
                reason
        );

        event.reply("⚠️ Warned " + target.getAsMention() + " for `" + reason + "`.")
                .setEphemeral(false)
                .queue();

        logModerationEmbed(guild, "⚠️ Warn", target.getUser(), member.getUser(), reason, null);
    }

    private void handleTimeout(SlashCommandInteractionEvent event, boolean admin, Member member) {
        if (member == null ||
                !(admin || member.hasPermission(Permission.MODERATE_MEMBERS))) {
            event.reply("❌ You need `Moderate Members` permission to use this.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("❌ This command can only be used in a guild.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Member target = event.getOption("user").getAsMember();
        if (target == null) {
            event.reply("❌ User not found.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        int minutes = event.getOption("minutes").getAsInt();
        if (minutes < 1) minutes = 1;
        if (minutes > 60 * 24 * 7) minutes = 60 * 24 * 7;

        String reason = event.getOption("reason") != null
                ? event.getOption("reason").getAsString()
                : "No reason provided";

        Duration duration = Duration.ofMinutes(minutes);
        long seconds = duration.getSeconds();

        event.deferReply(true).queue();

        target.timeoutFor(duration)
                .reason(reason)
                .queue(
                        v -> {
                            moderationRepo.logTimeout(
                                    guild.getIdLong(),
                                    target.getIdLong(),
                                    member.getIdLong(),
                                    reason,
                                    seconds
                            );
                            event.getHook()
                                    .editOriginal("⏱️ Timed out " + target.getAsMention() +
                                            " for " + minutes + "m (`" + reason + "`).")
                                    .queue();

                            logModerationEmbed(guild, "⏱️ Timeout", target.getUser(), member.getUser(), reason, seconds);
                        },
                        err -> event.getHook()
                                .editOriginal("❌ Failed to timeout user: " + err.getMessage())
                                .queue()
                );
    }

    private void handleBan(SlashCommandInteractionEvent event, boolean admin, Member member) {
        if (member == null ||
                !(admin || member.hasPermission(Permission.BAN_MEMBERS))) {
            event.reply("❌ You need `Ban Members` permission to use this.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("❌ This command can only be used in a guild.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Member target = event.getOption("user").getAsMember();
        if (target == null) {
            event.reply("❌ User not found.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        int days = 0;
        if (event.getOption("days") != null) {
            days = event.getOption("days").getAsInt();
            if (days < 0) days = 0;
            if (days > 7) days = 7;
        }

        String reason = event.getOption("reason") != null
                ? event.getOption("reason").getAsString()
                : "No reason provided";

        event.deferReply(true).queue();

        guild.ban(target, days, TimeUnit.valueOf(reason)).queue(
                v -> {
                    moderationRepo.logBan(
                            guild.getIdLong(),
                            target.getIdLong(),
                            member.getIdLong(),
                            reason
                    );
                    event.getHook()
                            .editOriginal("🔨 Banned " + target.getAsMention() +
                                    " (`" + reason + "`, delete " + days + " days of messages).")
                            .queue();

                    logModerationEmbed(guild, "🔨 Ban", target.getUser(), member.getUser(), reason, null);
                },
                err -> event.getHook()
                        .editOriginal("❌ Failed to ban user: " + err.getMessage())
                        .queue()
        );
    }

    private void handleModlog(SlashCommandInteractionEvent event, boolean admin, Member member) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("❌ This command can only be used in a guild.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        User target = event.getOption("user").getAsUser();
        long userId = target.getIdLong();

        List<ModerationEntry> history = moderationRepo.getHistory(guild.getIdLong(), userId, 10);
        if (history.isEmpty()) {
            event.reply("No moderation history for " + target.getAsMention() + ".")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (ModerationEntry e : history) {
            String type = e.getAction();
            String reason = e.getReason() != null ? e.getReason() : "No reason";
            String durationText = "";
            if (e.getDurationSeconds() != null) {
                durationText = " (" + e.getDurationSeconds() + "s)";
            }
            sb.append("`#").append(e.getId()).append("` ")
                    .append("**").append(type).append("**")
                    .append(durationText)
                    .append(" by <@").append(e.getModeratorId()).append(">")
                    .append(" – ").append(reason)
                    .append("\n");
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("📂 Moderation history for " + target.getAsTag())
                .setColor(0xf97316)
                .setDescription(sb.toString())
                .setTimestamp(Instant.now());

        event.replyEmbeds(eb.build())
                .setEphemeral(true)
                .queue();

        if (admin) {
            sendDebugDm(member, "🧪 Debug `/modlog` for " + target.getAsTag());
        }
    }

    private void logModerationEmbed(Guild guild,
                                    String title,
                                    User target,
                                    User moderator,
                                    String reason,
                                    Long durationSeconds) {
        if (moderationLogChannelId == 0) return;
        TextChannel logChannel = guild.getTextChannelById(moderationLogChannelId);
        if (logChannel == null) return;

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(title)
                .setColor(0xef4444)
                .addField("User", target.getAsMention() + " (`" + target.getId() + "`)", false)
                .addField("Moderator", moderator.getAsMention() + " (`" + moderator.getId() + "`)", false)
                .addField("Reason", reason != null ? reason : "No reason provided", false)
                .setTimestamp(Instant.now());

        if (durationSeconds != null) {
            eb.addField("Duration", durationSeconds + " seconds", false);
        }

        logChannel.sendMessageEmbeds(eb.build()).queue();
    }

    // ------------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------------

    private void sendDebugDm(Member member, String msg) {
        if (member == null) return;
        member.getUser()
                .openPrivateChannel()
                .queue(ch -> ch.sendMessage(msg).queue(), err -> {
                });
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
