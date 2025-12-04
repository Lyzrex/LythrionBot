package net.lyzrex.lythrionbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.lyzrex.lythrionbot.command.SlashCommandListener;
import net.lyzrex.lythrionbot.db.DatabaseManager;
import net.lyzrex.lythrionbot.game.GameScoreRepository;
import net.lyzrex.lythrionbot.i18n.Messages;
import net.lyzrex.lythrionbot.moderation.ModerationRepository;
import net.lyzrex.lythrionbot.profile.UserProfileRepository;
import net.lyzrex.lythrionbot.status.MaintenanceManager;
import net.lyzrex.lythrionbot.status.StatusService;
import net.lyzrex.lythrionbot.ticket.TicketService;

public final class LythrionBot {

    private LythrionBot() {
    }

    public static void main(String[] args) throws Exception {
        ConfigManager.load();
        Messages.load();

        String token = Env.get("BOT_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("BOT_TOKEN missing (.env or environment variable).");
            return;
        }

        DatabaseManager databaseManager = new DatabaseManager();
        UserProfileRepository userRepo = new UserProfileRepository(databaseManager);
        GameScoreRepository gameRepo = new GameScoreRepository(databaseManager);
        ModerationRepository moderationRepo = new ModerationRepository(databaseManager);

        boolean mainMaint = ConfigManager.getBoolean("maintenance.main", false);
        boolean lobbyMaint = ConfigManager.getBoolean("maintenance.lobby", false);
        boolean cbMaint = ConfigManager.getBoolean("maintenance.citybuild", false);
        MaintenanceManager maintenanceManager = new MaintenanceManager(mainMaint, lobbyMaint, cbMaint);

        StatusService statusService = new StatusService(maintenanceManager);

        String ticketCategoryId = ConfigManager.getString("tickets.categoryId", "0");
        String ticketStaffRoleId = ConfigManager.getString("tickets.staffRoleId", "0");
        String ticketLogChannelId = ConfigManager.getString("tickets.logChannelId", "0");
        TicketService ticketService = new TicketService(ticketCategoryId, ticketStaffRoleId, ticketLogChannelId);

        long modLogChannelId = ConfigManager.getLong("moderation.logChannelId", 0L);

        JDABuilder builder = JDABuilder.createDefault(token);
        builder.enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS);

        JDA jda = builder.build().awaitReady();

        // Commands
        SlashCommandData statusCmd = Commands.slash("status", "Shows the status of the Lythrion.net network");

        SlashCommandData maintenanceCmd = Commands.slash("maintenance", "View or change maintenance modes")
                .addSubcommands(
                        new SubcommandData("status", "Show the current maintenance state of all services"),
                        new SubcommandData("set", "Toggle maintenance mode for a service")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "service", "Which service", true)
                                                .addChoice("Velocity (main)", "main")
                                                .addChoice("Lobby", "lobby")
                                                .addChoice("Citybuild", "citybuild"),
                                        new OptionData(OptionType.BOOLEAN, "enabled", "Enable maintenance?", true)
                                )
                );

        SlashCommandData botInfoCmd = Commands.slash(
                "botinfo", "Shows technical information about the Lythrion bot");

        SlashCommandData profileCmd = Commands.slash(
                        "profile", "Shows a Lythrion player profile")
                .addOptions(new OptionData(
                        OptionType.STRING,
                        "name_or_uuid",
                        "Minecraft username or UUID",
                        true
                ));

        SlashCommandData latencyCmd = Commands.slash(
                "latency", "Shows latency for bot, database and API");

        SlashCommandData leaderboardCmd = Commands.slash(
                        "leaderboard", "Shows Lythrion player leaderboards")
                .addSubcommands(
                        new SubcommandData("playtime", "Top players by playtime"),
                        new SubcommandData("logins", "Top players by login count")
                );

        SlashCommandData linksCmd = Commands.slash(
                "links", "Shows useful Lythrion links (website, store, docs, Discord)");

        SlashCommandData ticketPanelCmd = Commands.slash(
                "ticketpanel", "Sends the ticket panel to this channel (admin only)");

        SlashCommandData coinflipCmd = Commands.slash(
                "coinflip", "Flip a coin");

        SlashCommandData rollCmd = Commands.slash(
                        "roll", "Roll a dice")
                .addOptions(new OptionData(
                        OptionType.INTEGER,
                        "sides",
                        "Number of sides (2–1000, default 6)",
                        false
                ));

        SlashCommandData rpsCmd = Commands.slash(
                        "rps", "Play Rock-Paper-Scissors against the bot")
                .addOptions(new OptionData(
                                OptionType.STRING,
                                "choice",
                                "Your choice",
                                true
                        )
                                .addChoice("Rock", "rock")
                                .addChoice("Paper", "paper")
                                .addChoice("Scissors", "scissors")
                );

        SlashCommandData gameScoreCmd = Commands.slash(
                        "gamescore", "Shows game scores stored in the database")
                .addSubcommands(
                        new SubcommandData("rps", "Top players for Rock-Paper-Scissors")
                );

        SlashCommandData clearCmd = Commands.slash(
                        "clear", "Delete the last messages in this channel")
                .addOptions(new OptionData(
                        OptionType.INTEGER,
                        "amount",
                        "Number of messages to delete (1–100)",
                        true
                ));

        SlashCommandData kickCmd = Commands.slash(
                        "kick", "Kick a member from the guild")
                .addOptions(
                        new OptionData(OptionType.USER, "user", "Target user", true),
                        new OptionData(OptionType.STRING, "reason", "Reason for the kick", false)
                );

        SlashCommandData warnCmd = Commands.slash(
                        "warn", "Warn a member and store it in the database")
                .addOptions(
                        new OptionData(OptionType.USER, "user", "Target user", true),
                        new OptionData(OptionType.STRING, "reason", "Reason for the warning", true)
                );

        SlashCommandData timeoutCmd = Commands.slash(
                        "timeout", "Timeout a member and log it in the database")
                .addOptions(
                        new OptionData(OptionType.USER, "user", "Target user", true),
                        new OptionData(OptionType.INTEGER, "minutes", "Duration in minutes", true),
                        new OptionData(OptionType.STRING, "reason", "Reason for the timeout", false)
                );

        SlashCommandData banCmd = Commands.slash(
                        "ban", "Ban a member and log it in the database")
                .addOptions(
                        new OptionData(OptionType.USER, "user", "Target user", true),
                        new OptionData(OptionType.INTEGER, "days", "Delete messages of the last N days (0–7)", false),
                        new OptionData(OptionType.STRING, "reason", "Reason for the ban", false)
                );

        SlashCommandData modLogCmd = Commands.slash(
                        "modlog", "Show moderation history (warns / bans / timeouts) of a user")
                .addOptions(
                        new OptionData(OptionType.USER, "user", "Target user", true)
                );

        jda.updateCommands()
                .addCommands(
                        statusCmd,
                        maintenanceCmd,
                        botInfoCmd,
                        profileCmd,
                        latencyCmd,
                        leaderboardCmd,
                        linksCmd,
                        ticketPanelCmd,
                        coinflipCmd,
                        rollCmd,
                        rpsCmd,
                        gameScoreCmd,
                        clearCmd,
                        kickCmd,
                        warnCmd,
                        timeoutCmd,
                        banCmd,
                        modLogCmd
                )
                .queue();

        jda.addEventListener(new SlashCommandListener(
                jda,
                statusService,
                maintenanceManager,
                userRepo,
                databaseManager,
                ticketService,
                gameRepo,
                moderationRepo,
                modLogChannelId
        ));

        System.out.println("Lythrion main bot is running.");
    }
}
