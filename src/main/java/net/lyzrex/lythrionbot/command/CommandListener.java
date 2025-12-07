package net.lyzrex.lythrionbot.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.lyzrex.lythrionbot.ConfigManager;
import net.lyzrex.lythrionbot.db.DatabaseManager;
import net.lyzrex.lythrionbot.profile.UserProfileRepository;
import net.lyzrex.lythrionbot.status.MaintenanceManager;
import net.lyzrex.lythrionbot.status.ServiceStatus;
import net.lyzrex.lythrionbot.status.StatusService;
import net.lyzrex.lythrionbot.ticket.TicketService;
import net.lyzrex.lythrionbot.game.GameService;
import net.lyzrex.lythrionbot.i18n.Language;
import net.lyzrex.lythrionbot.language.LanguageService;

import de.murmelmeister.murmelapi.group.Group;
import de.murmelmeister.murmelapi.group.GroupProvider;
import de.murmelmeister.murmelapi.language.LanguageProvider;
import de.murmelmeister.murmelapi.punishment.PunishmentService;
import de.murmelmeister.murmelapi.punishment.audit.PunishmentLog;
import de.murmelmeister.murmelapi.punishment.audit.PunishmentLogProvider;
import de.murmelmeister.murmelapi.punishment.type.PunishmentType;
import de.murmelmeister.murmelapi.punishment.user.PunishmentCurrentUserProvider;

import de.murmelmeister.murmelapi.user.User;
import de.murmelmeister.murmelapi.user.UserProvider;
import de.murmelmeister.murmelapi.user.UserService;
import de.murmelmeister.murmelapi.user.playtime.UserPlayTime;
import de.murmelmeister.murmelapi.user.playtime.UserPlayTimeProvider;
import de.murmelmeister.murmelapi.MurmelAPI;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/**
 * Haupt-Listener für alle Slash-Commands:
 * /status, /maintenance, /botinfo, /latency, /ticketpanel, /profile, /rps, /roll, /group, /punishment, /language
 */
public class CommandListener extends ListenerAdapter {

    private final JDA jda;
    private final StatusService statusService;
    private final MaintenanceManager maintenanceManager;
    private final TicketService ticketService;
    private final UserProfileRepository userRepo;
    private final DatabaseManager databaseManager;
    private final GameService gameService;
    private final PunishmentService punishmentService;
    private final GroupProvider groupProvider;
    private final PunishmentLogProvider punishmentLogProvider;
    private final UserProvider userProvider;
    private final UserService userService;
    private final UserPlayTimeProvider playTimeProvider;
    private final PunishmentCurrentUserProvider punishmentCurrentUserProvider;
    private final LanguageService languageService;
    private final LanguageProvider languageProvider;

    private final Random random = new Random();

    public CommandListener(JDA jda,
                           StatusService statusService,
                           MaintenanceManager maintenanceManager,
                           TicketService ticketService,
                           UserProfileRepository userRepo,
                           DatabaseManager databaseManager,
                           GameService gameService,
                           PunishmentService punishmentService,
                           GroupProvider groupProvider,
                           PunishmentLogProvider punishmentLogProvider,
                           UserProvider userProvider,
                           UserService userService,
                           UserPlayTimeProvider playTimeProvider,
                           PunishmentCurrentUserProvider punishmentCurrentUserProvider,
                           LanguageService languageService) {
        this.jda = jda;
        this.statusService = statusService;
        this.maintenanceManager = maintenanceManager;
        this.ticketService = ticketService;
        this.userRepo = userRepo;
        this.databaseManager = databaseManager;
        this.gameService = gameService;
        this.punishmentService = punishmentService;
        this.groupProvider = groupProvider;
        this.punishmentLogProvider = punishmentLogProvider;
        this.userProvider = userProvider;
        this.userService = userService;
        this.playTimeProvider = playTimeProvider;
        this.punishmentCurrentUserProvider = punishmentCurrentUserProvider;
        this.languageService = languageService;
        this.languageProvider = MurmelAPI.getLanguageProvider();
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
            case "rps" -> handleRps(event);
            case "roll" -> handleRoll(event);
            case "group" -> handleGroup(event, admin);
            case "punishment" -> handlePunishment(event, admin);
            case "language" -> handleLanguage(event);
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
        String iconUrl = ConfigManager.getString(
                "network.icon",
                "https://api.mcstatus.io/v2/icon/lythrion.net"
        );
        String botVersion = ConfigManager.getString("bot.version", "0.0.2-beta1.13");

        Runtime rt = Runtime.getRuntime();
        long usedMem = rt.totalMemory() - rt.freeMemory();
        String memText = String.format("Used: %.1f MB", usedMem / 1024.0 / 1024.0);

        // DB-Ping (MurmelAPI DB) - Nutzt den korrigierten, sicheren Ping des Delegates
        long murmelPing = databaseManager.ping();

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🛰️ Bot Info & Diagnostics")
                .setColor(0x00bcd4)
                .setThumbnail(iconUrl)
                .setDescription("Status und Metriken des **" + networkName + "** Bots.")
                .addField(
                        "🤖 Bot & Version",
                        String.join("\n",
                                "• **Version:** `" + botVersion + "`",
                                "• **Guilds:** " + guildCount,
                                "• **Owner:** Lyzrex"
                        ),
                        false
                )
                .addField(
                        "📡 Runtime Diagnostics",
                        String.join("\n",
                                "• **Gateway Ping:** " + wsPing + "ms",
                                "• **DB Ping:** " + (murmelPing >= 0 ? murmelPing + "ms" : "❌ N/A"),
                                "• **Uptime:** " + uptimeText,
                                "• **Memory:** " + memText
                        ),
                        false
                )
                .setFooter("Bot by Lyzrex")
                .setTimestamp(Instant.now());

        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    /* ====================================================================== */
    /* /latency – Bot / DB / API Ping                                         */
    /* ====================================================================== */

    private void handleLatency(SlashCommandInteractionEvent event,
                               boolean admin,
                               Member member) {
        long wsPing = jda.getGatewayPing();

        long dbPing = databaseManager.ping();
        long lythApiPing = statusService.pingStatusApi();
        long mcStatusPing = statusService.pingExternalMcStatusApi();

        long murmelPing = dbPing;

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("📶 Lythrion Latency Overview")
                .setColor(0x22c55e)
                .addField("Gateway (WS)", wsPing + "ms", true)
                .addField("MurmelAPI (DB)", (murmelPing >= 0 ? murmelPing + "ms" : "❌ N/A"), true)
                .addField("Lyth Status API", (lythApiPing >= 0 ? lythApiPing + "ms" : "❌ N/A"), true)
                .addField("mcstatus.io", (mcStatusPing >= 0 ? mcStatusPing + "ms" : "❌ N/A"), true)
                .setTimestamp(Instant.now());

        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    /* ====================================================================== */
    /* /ticketpanel – GalaxyBot-Style Panel (Hinzugefügt)                     */
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
    /* /profile – zieht Daten direkt aus der MurmelAPI (ULTIMATIV KORRIGIERT) */
    /* ====================================================================== */

    private void handleProfile(SlashCommandInteractionEvent event, boolean admin) {
        String input = event.getOption("input").getAsString();

        event.deferReply().queue();

        // 1. MurmelAPI User abrufen
        User user = userProvider.findByUsername(input);
        if (user == null) {
            try {
                if (input.length() >= 36) {
                    user = userProvider.findByMojangId(UUID.fromString(input));
                }
            } catch (IllegalArgumentException ignored) {}
        }

        if (user == null) {
            event.getHook().editOriginal("❌ No profile found for `" + input + "`.").queue();
            return;
        }

        // 2. PlayTime-Daten abrufen
        UserPlayTime playtime = playTimeProvider.findByUserId(user.id());

        // 3. Gruppeninformationen abrufen
        Group primaryGroup = groupProvider.findById(1);

        // 4. Daten formatieren
        String firstLoginText = (user.firstLogin() != null)
                ? formatLocalDateTime(user.firstLogin())
                : "N/A";

        long totalPlayTimeSeconds = (playtime != null) ? playtime.getPlayTime() : 0L;
        int loginCount = (playtime != null) ? playtime.getLoginCount() : 0;
        String formattedPlaytime = formatDuration(totalPlayTimeSeconds * 1000L);

        String groupName = (primaryGroup != null) ? primaryGroup.groupName() : "Guest";
        String langCode = formatLanguageId(user.languageId());

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("👤 Player Profile: " + user.username())
                .setColor(0x00bcd4) // Neutrale Farbe
                .setDescription("### MurmelAPI User ID: `" + user.id() + "`\n" +
                        "**UUID:** `" + user.mojangId().toString() + "`")

                // --- Datenblock 1: Eckdaten & Gruppe ---
                .addField("⭐ Eckdaten",
                        String.join("\n",
                                "• **Gruppe:** `" + groupName + "`",
                                "• **Erster Login:** " + firstLoginText,
                                "• **Sprache:** `" + langCode + "`"
                        ),
                        true)

                // --- Datenblock 2: Spielstatistiken ---
                .addField("📈 Spielstatistiken",
                        String.join("\n",
                                "• **Spielzeit:** " + formattedPlaytime,
                                "• **Login-Zähler:** `" + loginCount + "`"
                        ),
                        true)

                // --- Debug/Admin Block (Nicht Inline für saubere Trennung) ---
                .addField("\u200B", "\u200B", false) // Leere Trennung
                .addField("⚙️ Debug-Status",
                        String.join("\n",
                                "• **Debug User:** " + (user.debugUser() ? "Ja" : "Nein"),
                                "• **Debug Enabled:** " + (user.debugEnabled() ? "🟢 Aktiv" : "⚪ Off")
                        ),
                        false)
                .setFooter("Daten powered by MurmelAPI")
                .setTimestamp(Instant.now());

        event.getHook().editOriginalEmbeds(eb.build()).queue();
    }

    /* ====================================================================== */
    /* /language [choice] (NEU)                                               */
    /* ====================================================================== */

    private void handleLanguage(SlashCommandInteractionEvent event) {
        String choice = event.getOption("choice").getAsString();

        Optional<Language> targetLang = Language.fromString(choice);

        if (targetLang.isEmpty()) {
            event.reply("❌ Ungültige Sprachauswahl. Verfügbar: English, Deutsch.").setEphemeral(true).queue();
            return;
        }

        User user = userProvider.findByUsername(event.getUser().getName());
        int userId = user != null ? user.id() : -1;

        if (userId == -1 || user == null) {
            event.reply("❌ Dein Minecraft/MurmelAPI-Account konnte nicht gefunden werden. Bitte verbinde deinen Account zuerst.").setEphemeral(true).queue();
            return;
        }

        boolean success = languageService.setLanguage(userId, targetLang.get());

        if (success) {
            String langCode = targetLang.get().name().equalsIgnoreCase("DE") ? "Deutsch (`de-DE`)" : "English (`en-US`)";
            event.reply("✅ Die Sprache wurde auf **" + langCode + "** umgestellt.").setEphemeral(true).queue();
        } else {
            event.reply("❌ Fehler beim Speichern der Sprache. Konnte MurmelAPI nicht aktualisieren.").setEphemeral(true).queue();
        }
    }


    /* ====================================================================== */
    /* /rps – Rock Paper Scissors (RPS) Game                                  */
    /* ====================================================================== */

    private void handleRps(SlashCommandInteractionEvent event) {
        String sub = event.getSubcommandName();
        if (sub == null) {
            event.reply("❌ Unknown rps subcommand.")
                    .setEphemeral(true).queue();
            return;
        }

        switch (sub) {
            case "play" -> gameService.handleRpsPlay(event);
            case "stats" -> gameService.handleRpsStats(event);
            case "top" -> gameService.handleRpsTop(event);
            default -> event.reply("❌ Unknown rps subcommand.")
                    .setEphemeral(true).queue();
        }
    }

    /* ====================================================================== */
    /* /roll – Dice Game                                                      */
    /* ====================================================================== */

    private void handleRoll(SlashCommandInteractionEvent event) {
        gameService.handleDiceRoll(event);
    }

    /* ====================================================================== */
    /* /group – MurmelAPI Group Info (NEU: Subcommands)                       */
    /* ====================================================================== */

    private void handleGroup(SlashCommandInteractionEvent event, boolean admin) {
        String groupName = event.getOption("name").getAsString();
        event.deferReply().queue();

        Group group = groupProvider.findByName(groupName);

        if (group == null) {
            event.getHook().editOriginal("❌ Gruppe mit dem Namen `" + groupName + "` wurde nicht gefunden.").queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🏷️ MurmelAPI Gruppe: " + group.groupName())
                .setColor(0x00bcd4)
                .setDescription("Detaillierte Informationen zur Gruppe **" + group.groupName() + "**.")
                .addField("ID", String.valueOf(group.id()), true)
                .addField("Priorität", String.valueOf(group.priority()), true)
                .addField("Standardgruppe", group.isDefault() ? "🟢 Ja" : "⚪ Nein", true)
                .addField("Erstellt von (ID)", String.valueOf(group.createdBy()), true)
                .addField("Erstellt am", formatLocalDateTime(group.createdAt()), true)
                .setFooter("Daten powered by MurmelAPI")
                .setTimestamp(Instant.now());

        event.getHook().editOriginalEmbeds(eb.build()).queue();
    }

    /* ====================================================================== */
    /* /punishment – Subcommand Handler                                       */
    /* ====================================================================== */

    private void handlePunishment(SlashCommandInteractionEvent event, boolean admin) {
        if (!admin) {
            event.reply("❌ Nur Administratoren dürfen diesen Befehl verwenden.")
                    .setEphemeral(true).queue();
            return;
        }

        String sub = event.getSubcommandName();
        if (sub == null) return;

        switch (sub) {
            case "history" -> handlePunishmentHistory(event);
            case "list" -> handlePunishmentList(event);
            default -> event.reply("❌ Unbekanntes Punishment Subkommando.").setEphemeral(true).queue();
        }
    }

    /* ====================================================================== */
    /* /punishment history – MurmelAPI Punishment Audit                       */
    /* ====================================================================== */

    private void handlePunishmentHistory(SlashCommandInteractionEvent event) {
        String input = event.getOption("query").getAsString(); // Minecraft Name oder UUID
        event.deferReply().queue();

        // 1. User anhand des Inputs finden (MurmelAPI)
        User user = userProvider.findByUsername(input);
        if (user == null) {
            try {
                if (input.length() >= 36) {
                    user = userProvider.findByMojangId(UUID.fromString(input));
                }
            } catch (IllegalArgumentException ignored) {}
        }


        if (user == null) {
            event.getHook().editOriginal("❌ MurmelAPI User für `" + input + "` nicht gefunden.").queue();
            return;
        }

        // 2. Punishment Logs abrufen (Zuletzt 10 Logs)
        int userId = user.id();
        List<PunishmentLog> logs = punishmentLogProvider.getLogsByUserId(userId);

        if (logs.isEmpty()) {
            event.getHook().editOriginal("✅ Keine Punishment-Einträge für **" + user.username() + "** gefunden.").queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        logs.stream().limit(10).forEach(log -> {
            PunishmentType type = PunishmentType.fromId(log.reasonTypeId());
            String typeName = type != null ? type.getName() : "Unbekannt";

            String duration = log.isPermanent() ? "Permanent" : formatDuration(log.reasonDuration() * 1000L);

            sb.append("`").append(log.id().toString().substring(0, 8)).append("` | ")
                    .append("**Aktion:** `").append(log.action().name()).append("` | ")
                    .append("**Typ:** `").append(typeName).append("` (Dauer: ").append(duration).append(")\n")
                    .append("   **Grund:** *").append(log.reasonText()).append("*\n");
        });

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("📜 Punishment History für " + user.username())
                .setColor(0xef4444)
                .setDescription(sb.toString())
                .setFooter("Zeigt die letzten 10 Einträge an | Daten powered by MurmelAPI")
                .setTimestamp(Instant.now());

        event.getHook().editOriginalEmbeds(eb.build()).queue();
    }

    /* ====================================================================== */
    /* /punishment list – Zeigt alle aktuellen Bestrafungen                   */
    /* ====================================================================== */

    private void handlePunishmentList(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        List<Integer> bannedUserIds = punishmentCurrentUserProvider.getAllPunishedUserIds(PunishmentType.BAN.getId());
        List<Integer> mutedUserIds = punishmentCurrentUserProvider.getAllPunishedUserIds(PunishmentType.MUTE.getId());

        if (bannedUserIds.isEmpty() && mutedUserIds.isEmpty()) {
            event.getHook().editOriginal("✅ Aktuell sind keine User permanent gebannt oder gemutet.").queue();
            return;
        }

        StringBuilder sbBans = new StringBuilder();
        StringBuilder sbMutes = new StringBuilder();

        bannedUserIds.stream().limit(10).forEach(userId -> {
            User user = userProvider.findById(userId);
            sbBans.append("• `").append(user != null ? user.username() : "ID " + userId).append("`\n");
        });

        mutedUserIds.stream().limit(10).forEach(userId -> {
            User user = userProvider.findById(userId);
            sbMutes.append("• `").append(user != null ? user.username() : "ID " + userId).append("`\n");
        });

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🚨 Aktive Bestrafungen")
                .setColor(0xef4444)
                .setDescription("Zeigt die ersten 10 aktiven Banns und Mutes.")
                .addField("Aktive Banns (" + bannedUserIds.size() + ")",
                        sbBans.length() > 0 ? sbBans.toString() : "Keine",
                        true)
                .addField("Aktive Mutes (" + mutedUserIds.size() + ")",
                        sbMutes.length() > 0 ? sbMutes.toString() : "Keine",
                        true)
                .setFooter("Daten powered by MurmelAPI")
                .setTimestamp(Instant.now());

        event.getHook().editOriginalEmbeds(eb.build()).queue();
    }


    /* ====================================================================== */
    /* Ticket-Events (SelectMenu / Button)                                    */
    /* ====================================================================== */

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        handleTicketSelect(event);
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        handleTicketClose(event);
    }

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

    private String formatLocalDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "N/A";
        return dateTime.toLocalDate().toString() + " " + dateTime.toLocalTime().withNano(0).toString();
    }

    /**
     * Konvertiert die MurmelAPI languageId in den Klartext-Code (z.B. 1 -> "en-US").
     */
    private String formatLanguageId(int languageId) {
        de.murmelmeister.murmelapi.language.Language murmelLang = languageProvider.get(languageId);
        return murmelLang != null ? murmelLang.code() : "Unbekannt";
    }
}