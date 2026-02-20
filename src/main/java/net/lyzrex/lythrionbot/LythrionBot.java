package net.lyzrex.lythrionbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.lyzrex.lythrionbot.command.CommandListener;
import net.lyzrex.lythrionbot.db.DatabaseManager;
import net.lyzrex.lythrionbot.db.SyntrixRepository;
import net.lyzrex.lythrionbot.game.GameScoreRepository;
import net.lyzrex.lythrionbot.game.GameService;
import net.lyzrex.lythrionbot.i18n.Messages;
import net.lyzrex.lythrionbot.language.LanguageService;
import net.lyzrex.lythrionbot.profile.UserProfileRepository;
import net.lyzrex.lythrionbot.status.MaintenanceManager;
import net.lyzrex.lythrionbot.status.StatusService;
import net.lyzrex.lythrionbot.ticket.TicketService;
import net.lyzrex.lythrionbot.listener.JoinListener;

import de.murmelmeister.murmelapi.MurmelAPI;
import de.murmelmeister.murmelapi.punishment.PunishmentService;
import de.murmelmeister.murmelapi.punishment.audit.PunishmentLogProvider;
import de.murmelmeister.murmelapi.punishment.user.PunishmentCurrentUserProvider;
import de.murmelmeister.murmelapi.group.GroupProvider;
import de.murmelmeister.murmelapi.user.UserProvider;
import de.murmelmeister.murmelapi.user.UserService;
import de.murmelmeister.murmelapi.user.playtime.UserPlayTimeProvider;
import de.murmelmeister.library.database.Database;

import java.io.InputStream;
import java.util.Properties;

public final class LythrionBot {

    private LythrionBot() {
    }

    public static void main(String[] args) throws Exception {

        // --- 1. Konfiguration & Token ---
        ConfigManager.load();
        Messages.load();

        String token = Env.get("BOT_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("BOT_TOKEN missing in .env file.");
            return;
        }

        // --- 2. Datenbankverbindung (MurmelAPI) ---
        Properties dbProperties = new Properties();
        String propertiesFileName = "database.properties";

        try (InputStream in = LythrionBot.class.getClassLoader().getResourceAsStream(propertiesFileName)) {
            if (in == null) {
                System.err.println("File 'database.properties' not found in resources.");
                return;
            }
            dbProperties.load(in);
            dbProperties.setProperty("maximumPoolSize", "10");
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        try {
            MurmelAPI.connect(dbProperties);
            System.out.println("MurmelAPI initialized.");
        } catch (Exception e) {
            System.err.println("Failed to connect to database!");
            e.printStackTrace();
            return;
        }

        // --- 3. Abhängigkeiten (Services & Repositories) ---
        Database murmelDatabase = MurmelAPI.getDatabase();
        DatabaseManager databaseManager = new DatabaseManager();

        MaintenanceManager maintenanceManager = new MaintenanceManager();
        StatusService statusService = new StatusService(maintenanceManager);

        UserProvider userProvider = MurmelAPI.getUserProvider();
        PunishmentCurrentUserProvider punishmentCurrentUserProvider = MurmelAPI.getPunishmentCurrentUserProvider();
        PunishmentService punishmentService = MurmelAPI.getPunishmentService();
        GroupProvider groupProvider = MurmelAPI.getGroupProvider();
        PunishmentLogProvider punishmentLogProvider = MurmelAPI.getPunishmentLogProvider();
        UserService userService = MurmelAPI.getUserService();
        UserPlayTimeProvider playTimeProvider = MurmelAPI.getUserPlayTimeProvider();

        UserProfileRepository userRepo = new UserProfileRepository(murmelDatabase);
        SyntrixRepository syntrixRepo = new SyntrixRepository(murmelDatabase);
        GameScoreRepository gameScoreRepo = new GameScoreRepository(murmelDatabase);

        LanguageService languageService = new LanguageService(userProvider);
        GameService gameService = new GameService(gameScoreRepo, userProvider);

        TicketService ticketService = new TicketService(
                ConfigManager.getString("tickets.categoryId", "0"),
                ConfigManager.getString("tickets.staffRoleId", "0"),
                ConfigManager.getString("tickets.logChannelId", "0")
        );

        // --- 4. JDA Build ---
        JDABuilder builder = JDABuilder.createDefault(token)
                .enableIntents(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.MESSAGE_CONTENT
                )
                .setStatus(OnlineStatus.ONLINE)
                .setActivity(Activity.playing("Lythrion.net"));

        JDA jda = builder.build().awaitReady();

        // --- 5. Slash Commands Registrierung ---
        CommandData statusCmd = Commands.slash("status", "Shows the status of the Lythrion.net network");
        CommandData maintenanceCmd = Commands.slash("maintenance", "View or change maintenance modes")
                .addSubcommands(
                        new SubcommandData("status", "Show current maintenance state"),
                        new SubcommandData("set", "Toggle maintenance mode for a service")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "service", "Target service", true)
                                                .addChoice("Velocity (main)", "main")
                                                .addChoice("Lobby", "lobby")
                                                .addChoice("Citybuild", "citybuild"),
                                        new OptionData(OptionType.BOOLEAN, "enabled", "Enable maintenance?", true)
                                )
                );
        CommandData botInfoCmd = Commands.slash("botinfo", "Shows technical information about the bot");
        CommandData latencyCmd = Commands.slash("latency", "Show gateway, database and API latency");
        CommandData ticketCmd = Commands.slash("ticketpanel", "Post the ticket panel message (admin only)");

        CommandData profileCmd = Commands.slash("profile", "Show a player profile")
                .addOptions(new OptionData(OptionType.STRING, "input", "Minecraft name or UUID", true));

        CommandData rpsCmd = Commands.slash("rps", "Rock Paper Scissors")
                .addSubcommands(
                        new SubcommandData("play", "Play a round against the bot")
                                .addOptions(new OptionData(OptionType.STRING, "choice", "Your move", true).addChoice("Rock", "rock").addChoice("Paper", "paper").addChoice("Scissors", "scissors")),
                        new SubcommandData("stats", "Show your RPS statistics"),
                        new SubcommandData("top", "Show the RPS leaderboard")
                );
        CommandData rollCmd = Commands.slash("roll", "Roll a dice and bet against the bot").addOptions(new OptionData(OptionType.INTEGER, "betrag", "The amount you want to bet", true));

        // NEUER EMBED COMMAND
        CommandData embedCmd = Commands.slash("embed", "Sendet ein benutzerdefiniertes Embed in diesen Kanal")
                .addOptions(
                        new OptionData(OptionType.STRING, "titel", "Der Titel des Embeds", true),
                        new OptionData(OptionType.STRING, "beschreibung", "Der Inhalt (nutze \\n für Zeilenumbrüche)", true),
                        new OptionData(OptionType.STRING, "farbe", "Hex-Farbe (z.B. #2ecc71)", false)
                );

        // Admin Moderation etc.
        CommandData punishCmd = Commands.slash("punish", "Punish a Minecraft player (Ban/Mute/Kick)")
                .addOptions(
                        new OptionData(OptionType.STRING, "player", "Minecraft Name", true),
                        new OptionData(OptionType.STRING, "type", "Type", true).addChoice("Ban", "BAN").addChoice("Mute", "MUTE").addChoice("Kick", "KICK"),
                        new OptionData(OptionType.STRING, "reason", "Reason", true),
                        new OptionData(OptionType.STRING, "duration", "Duration (e.g. 1d, 12h) [Optional for Ban/Mute]", false)
                );

        CommandData clearCmd = Commands.slash("clear", "Bulk delete messages").addOptions(new OptionData(OptionType.INTEGER, "amount", "Number of messages", true));
        CommandData kickCmd = Commands.slash("kick", "Kick a Discord user").addOptions(new OptionData(OptionType.USER, "user", "User", true), new OptionData(OptionType.STRING, "reason", "Reason", false));
        CommandData timeoutCmd = Commands.slash("timeout", "Timeout a Discord user").addOptions(new OptionData(OptionType.USER, "user", "User", true), new OptionData(OptionType.STRING, "duration", "Duration", true), new OptionData(OptionType.STRING, "reason", "Reason", false));
        CommandData announceCmd = Commands.slash("announce", "Send an announcement").addOptions(new OptionData(OptionType.STRING, "message", "The message", true), new OptionData(OptionType.CHANNEL, "channel", "Channel", false));

        // Utility
        CommandData ipCmd = Commands.slash("ip", "Show Server IP");
        CommandData helpCmd = Commands.slash("help", "Show command list");
        CommandData pingCmd = Commands.slash("ping", "Bot Latency");
        CommandData suggestCmd = Commands.slash("suggest", "Submit a suggestion").addOptions(new OptionData(OptionType.STRING, "idea", "Your suggestion", true));
        CommandData feedbackCmd = Commands.slash("feedback", "Send feedback").addOptions(new OptionData(OptionType.STRING, "text", "Your feedback", true));
        CommandData tutorialCmd = Commands.slash("tutorial", "How to join Lythrion");

        CommandData leaderCmd = Commands.slash("leaderboard", "Show Lythrion Level Leaderboard");
        CommandData groupCmd = Commands.slash("group", "Show MurmelAPI group info").addOptions(new OptionData(OptionType.STRING, "name", "The group name", true));
        CommandData languageCmd = Commands.slash("language", "Change bot language").addOptions(new OptionData(OptionType.STRING, "choice", "Select language", true).addChoice("English", "english").addChoice("Deutsch", "deutsch"));

        jda.updateCommands()
                .addCommands(
                        statusCmd, maintenanceCmd, botInfoCmd, profileCmd, latencyCmd,
                        ticketCmd, rpsCmd, rollCmd, punishCmd, clearCmd, kickCmd, timeoutCmd,
                        announceCmd, ipCmd, helpCmd, pingCmd, suggestCmd, feedbackCmd,
                        tutorialCmd, leaderCmd, groupCmd, languageCmd, embedCmd
                )
                .queue();

        // --- 6. Listener Registrierung ---
        jda.addEventListener(new CommandListener(
                jda,
                statusService,
                maintenanceManager,
                ticketService,
                userRepo,
                databaseManager,
                gameService,
                punishmentService,
                groupProvider,
                punishmentLogProvider,
                userProvider,
                userService,
                playTimeProvider,
                punishmentCurrentUserProvider,
                languageService,
                syntrixRepo
        ));

        jda.addEventListener(new JoinListener());

        System.out.println("Lythrion main bot is running.");
// Create the CommandListener instance with all required dependencies
        CommandListener cmdListener = new CommandListener(
                jda, statusService, maintenanceManager, ticketService,
                userRepo, databaseManager, gameService, punishmentService,
                groupProvider, punishmentLogProvider, userProvider,
                userService, playTimeProvider, punishmentCurrentUserProvider,
                languageService, syntrixRepo
        );

        // Register the listener to handle slash commands
        jda.addEventListener(cmdListener);


        java.util.concurrent.Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
                cmdListener::checkPendingVerifications,
                5, 5, java.util.concurrent.TimeUnit.SECONDS
        );
    }
}