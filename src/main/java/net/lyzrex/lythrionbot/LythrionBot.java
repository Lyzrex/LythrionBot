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
import net.lyzrex.lythrionbot.game.GameScoreRepository;
import net.lyzrex.lythrionbot.game.GameService;
import net.lyzrex.lythrionbot.i18n.Messages;
import net.lyzrex.lythrionbot.language.LanguageService;
import net.lyzrex.lythrionbot.profile.UserProfileRepository;
import net.lyzrex.lythrionbot.status.MaintenanceManager;
import net.lyzrex.lythrionbot.status.StatusService;
import net.lyzrex.lythrionbot.ticket.TicketService;


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
        if (token == null || token.isBlank() || token.equalsIgnoreCase("dein_discord_token")) {
            System.err.println("BOT_TOKEN missing or invalid (check .env or environment).");
            return;
        }

        // --- 2. Datenbankverbindung (MurmelAPI) ---
        Properties dbProperties = new Properties();
        String propertiesFileName = "database.properties";

        try (InputStream in = LythrionBot.class.getClassLoader().getResourceAsStream(propertiesFileName)) {
            if (in == null) {
                System.err.println("Die Datei '" + propertiesFileName + "' wurde nicht im Classpath gefunden.");
                return;
            }
            dbProperties.load(in);

            if (!dbProperties.containsKey("jdbcUrl")) {
                System.err.println("Die database.properties muss den Schlüssel 'jdbcUrl' enthalten.");
                return;
            }

            dbProperties.setProperty("maximumPoolSize", "10");
            dbProperties.setProperty("minimumIdle", "5");

        } catch (Exception e) {
            System.err.println("Fehler beim Laden oder Parsen der database.properties.");
            e.printStackTrace();
            return;
        }

        try {
            // Verbindung herstellen
            MurmelAPI.connect(dbProperties);
            System.out.println("MurmelAPI initialized successfully with properties from " + propertiesFileName + ".");

            // WICHTIG: Tabellenstruktur initialisieren
            MurmelAPI.setup();
            System.out.println("MurmelAPI setup completed: Tables and default data initialized.");

        } catch (Exception e) {
            System.err.println("Failed to initialize MurmelAPI! Check database connection details.");
            e.printStackTrace();
            return;
        }

        // --- 3. Abhängigkeiten (Services & Repositories) ---

        Database murmelDatabase = MurmelAPI.getDatabase();
        DatabaseManager databaseManager = new DatabaseManager(); // Delegate

        MaintenanceManager maintenanceManager = new MaintenanceManager();
        StatusService statusService = new StatusService(maintenanceManager);

        // MurmelAPI Providers
        UserProvider userProvider = MurmelAPI.getUserProvider();
        PunishmentCurrentUserProvider punishmentCurrentUserProvider = MurmelAPI.getPunishmentCurrentUserProvider();

        // Custom Services
        UserProfileRepository userRepo = new UserProfileRepository(murmelDatabase);
        GameScoreRepository gameScoreRepo = new GameScoreRepository(murmelDatabase);
        LanguageService languageService = new LanguageService(userProvider);
        GameService gameService = new GameService(gameScoreRepo, userProvider);

        TicketService ticketService = new TicketService(
                ConfigManager.getString("tickets.category_id", "0"),
                ConfigManager.getString("tickets.staff_role_id", "0"),
                ConfigManager.getString("tickets.log_channel_id", "0")
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

        // Command Data... (verkürzt für das finale Output)
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
        CommandData profileCmd = Commands.slash("profile", "Show a MurmelAPI player profile")
                .addOptions(new OptionData(OptionType.STRING, "input", "Minecraft name or UUID", true));
        CommandData latencyCmd = Commands.slash("latency", "Show gateway, database and API latency");
        CommandData ticketPanelCmd = Commands.slash("ticketpanel", "Post the ticket panel message (admin only)");
        CommandData rpsCmd = Commands.slash("rps", "Rock Paper Scissors")
                .addSubcommands(
                        new SubcommandData("play", "Play a round against the bot")
                                .addOptions(new OptionData(OptionType.STRING, "choice", "Your move", true).addChoice("Rock", "rock").addChoice("Paper", "paper").addChoice("Scissors", "scissors")),
                        new SubcommandData("stats", "Show your RPS statistics"),
                        new SubcommandData("top", "Show the RPS leaderboard")
                );
        CommandData rollCmd = Commands.slash("roll", "Roll a dice and bet against the bot").addOptions(new OptionData(OptionType.INTEGER, "betrag", "The amount you want to bet", true));
        CommandData groupCmd = Commands.slash("group", "Show MurmelAPI group information").addOptions(new OptionData(OptionType.STRING, "name", "The group name", true));
        CommandData punishmentCmd = Commands.slash("punishment", "Admin command for punishment history/management")
                .addSubcommands(new SubcommandData("history", "Show a user's punishment history (last 10 logs)").addOptions(new OptionData(OptionType.STRING, "query", "Minecraft name or UUID", true)));
        CommandData languageCmd = Commands.slash("language", "Change bot language")
                .addOptions(new OptionData(OptionType.STRING, "choice", "Select language", true).addChoice("English", "english").addChoice("Deutsch", "deutsch"));


        jda.updateCommands()
                .addCommands(
                        statusCmd,
                        maintenanceCmd,
                        botInfoCmd,
                        profileCmd,
                        latencyCmd,
                        ticketPanelCmd,
                        rpsCmd,
                        rollCmd,
                        groupCmd,
                        punishmentCmd,
                        languageCmd
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
                MurmelAPI.getPunishmentService(), // PunishmentService
                MurmelAPI.getGroupProvider(),      // GroupProvider
                MurmelAPI.getPunishmentLogProvider(), // PunishmentLogProvider
                userProvider,
                MurmelAPI.getUserService(),      // UserService
                MurmelAPI.getUserPlayTimeProvider(), // PlayTimeProvider
                punishmentCurrentUserProvider,
                languageService
        ));

        System.out.println("Lythrion main bot is running.");
    }
}