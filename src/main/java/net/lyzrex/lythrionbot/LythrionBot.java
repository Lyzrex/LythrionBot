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
import net.lyzrex.lythrionbot.profile.UserProfileRepository;
import net.lyzrex.lythrionbot.status.MaintenanceManager;
import net.lyzrex.lythrionbot.status.StatusService;
import net.lyzrex.lythrionbot.ticket.TicketService;
import net.lyzrex.lythrionbot.language.LanguageService;

import de.murmelmeister.murmelapi.MurmelAPI;
import de.murmelmeister.murmelapi.punishment.PunishmentService;
import de.murmelmeister.murmelapi.punishment.audit.PunishmentLogProvider;
import de.murmelmeister.murmelapi.punishment.user.PunishmentCurrentUserProvider;
import de.murmelmeister.murmelapi.group.GroupProvider;
import de.murmelmeister.murmelapi.user.UserProvider;
import de.murmelmeister.murmelapi.user.UserService;
import de.murmelmeister.murmelapi.user.playtime.UserPlayTimeProvider;

import java.util.Properties;


public final class LythrionBot {

    private LythrionBot() {
    }

    public static void main(String[] args) throws Exception {
        // ---- Config + messages ----
        ConfigManager.load();
        Messages.load();

        // ---- Token aus ENV /.env ----
        String token = Env.get("BOT_TOKEN");
        if (token == null || token.isBlank() || token.equalsIgnoreCase("dein_discord_token")) {
            System.err.println("BOT_TOKEN missing or invalid (check .env or environment).");
            return;
        }


        try {

            MurmelAPI.connect("database.properties");


            MurmelAPI.initProviders();
            System.out.println("MurmelAPI initialized successfully from database.properties.");
        } catch (Exception e) {
            System.err.println("Failed to initialize MurmelAPI! Check database connection details in config.");
            e.printStackTrace();
            return;
        }

        // -------------------------------------------------------------

        // ---- DB (Wird für lokale Tabellen/Spiele/Tickets verwendet) ----
        DatabaseManager databaseManager = new DatabaseManager();

        // ---- Maintenance-Flags aus config.yml ----
        MaintenanceManager maintenanceManager = new MaintenanceManager();

        // ---- Services ----
        StatusService statusService = new StatusService(maintenanceManager);
        UserProfileRepository userRepo = new UserProfileRepository(databaseManager.getDatabase());

        GameScoreRepository gameScoreRepo = new GameScoreRepository(databaseManager.getDatabase());
        GameService gameService = new GameService(gameScoreRepo);

        // MurmelAPI Services abrufen
        PunishmentService punishmentService = MurmelAPI.getPunishmentService();
        GroupProvider groupProvider = MurmelAPI.getGroupProvider();
        PunishmentLogProvider punishmentLogProvider = MurmelAPI.getPunishmentLogProvider();
        PunishmentCurrentUserProvider punishmentCurrentUserProvider = MurmelAPI.getPunishmentCurrentUserProvider();
        UserProvider userProvider = MurmelAPI.getUserProvider();
        UserService userService = MurmelAPI.getUserService();
        UserPlayTimeProvider playTimeProvider = MurmelAPI.getUserPlayTimeProvider();

        // Lokaler Service, der MurmelAPI intern nutzen kann
        LanguageService languageService = new LanguageService();

        String ticketCategoryId = ConfigManager.getString("tickets.category_id", "0");
        String ticketStaffRoleId = ConfigManager.getString("tickets.staff_role_id", "0");
        String ticketLogChannelId = ConfigManager.getString("tickets.log_channel_id", "0");

        TicketService ticketService = new TicketService(
                ticketCategoryId,
                ticketStaffRoleId,
                ticketLogChannelId
        );

        // ---- JDA ----
        JDABuilder builder = JDABuilder.createDefault(token)
                .enableIntents(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.MESSAGE_CONTENT
                )
                .setStatus(OnlineStatus.ONLINE)
                .setActivity(Activity.playing("Lythrion.net"));

        JDA jda = builder.build().awaitReady();

        // ---- Slash-Commands registrieren ----

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
                .addOptions(
                        new OptionData(OptionType.STRING, "input", "Minecraft name or UUID", true)
                );

        CommandData latencyCmd = Commands.slash("latency", "Show gateway, database and API latency");

        CommandData ticketPanelCmd = Commands.slash("ticketpanel", "Post the ticket panel message (admin only)");

        CommandData rpsCmd = Commands.slash("rps", "Rock Paper Scissors")
                .addSubcommands(
                        new SubcommandData("play", "Play a round against the bot")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "choice", "Your move", true)
                                                .addChoice("Rock", "rock")
                                                .addChoice("Paper", "paper")
                                                .addChoice("Scissors", "scissors")
                                ),
                        new SubcommandData("stats", "Show your RPS statistics"),
                        new SubcommandData("top", "Show the RPS leaderboard")
                );

        CommandData rollCmd = Commands.slash("roll", "Roll a dice and bet against the bot")
                .addOptions(new OptionData(OptionType.INTEGER, "betrag", "The amount you want to bet", true));

        CommandData groupCmd = Commands.slash("group", "Show MurmelAPI group information")
                .addOptions(new OptionData(OptionType.STRING, "name", "The group name", true));

        CommandData punishmentCmd = Commands.slash("punishment", "Admin command for punishment history/management")
                .addSubcommands(
                        new SubcommandData("history", "Show a user's punishment history (last 10 logs)")
                                .addOptions(new OptionData(OptionType.STRING, "query", "Minecraft name or UUID", true)),
                        new SubcommandData("list", "List active punishments by type (ban/mute/warn)")
                                .addOptions(new OptionData(OptionType.STRING, "type", "Punishment type", true)
                                        .addChoice("BAN", "BAN")
                                        .addChoice("MUTE", "MUTE")
                                        .addChoice("WARN", "WARN"))
                );

        CommandData languageCmd = Commands.slash("language", "Set your primary language for server messages")
                .addOptions(new OptionData(OptionType.STRING, "choice", "Select your language", true)
                        .addChoice("Deutsch", "DE")
                        .addChoice("English", "EN"));


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

        // ---- Listener ----
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
                languageService
        ));

        System.out.println("Lythrion main bot is running.");
    }
}