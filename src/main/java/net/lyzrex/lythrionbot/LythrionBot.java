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
import net.lyzrex.lythrionbot.command.SlashCommandListener;
import net.lyzrex.lythrionbot.db.DatabaseManager;
import net.lyzrex.lythrionbot.game.GameScoreRepository;
import net.lyzrex.lythrionbot.game.GameService;
import net.lyzrex.lythrionbot.i18n.Messages;
import net.lyzrex.lythrionbot.profile.UserProfileRepository;
import net.lyzrex.lythrionbot.status.MaintenanceManager;
import net.lyzrex.lythrionbot.status.StatusService;
import net.lyzrex.lythrionbot.ticket.TicketService;

import java.util.List;

public final class LythrionBot {

    private LythrionBot() {
    }

    public static void main(String[] args) throws Exception {
        // ---- Config + messages ----
        ConfigManager.load();
        Messages.load(); // lädt message_en.properties als Default

        // ---- Token aus ENV /.env ----
        String token = Env.get("BOT_TOKEN");
        if (token == null || token.isBlank() || token.equalsIgnoreCase("dein_discord_token")) {
            System.err.println("BOT_TOKEN missing or invalid (check .env or environment).");
            return;
        }

        // ---- DB ----
        DatabaseManager databaseManager = new DatabaseManager(); // liest database.properties o.ä.

        // ---- Maintenance-Flags aus config.yml ----
        boolean mainMaint = ConfigManager.getBoolean("maintenance.main", false);
        boolean lobbyMaint = ConfigManager.getBoolean("maintenance.lobby", false);
        boolean cbMaint = ConfigManager.getBoolean("maintenance.citybuild", false);

        MaintenanceManager maintenanceManager = new MaintenanceManager(mainMaint, lobbyMaint, cbMaint);

        // ---- Services ----
        StatusService statusService = new StatusService(maintenanceManager);
        UserProfileRepository userRepo = new UserProfileRepository(databaseManager);

        GameScoreRepository gameScoreRepo = new GameScoreRepository(databaseManager);
        GameService gameService = new GameService(gameScoreRepo);

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

        jda.updateCommands()
                .addCommands(
                        statusCmd,
                        maintenanceCmd,
                        botInfoCmd,
                        profileCmd,
                        latencyCmd,
                        ticketPanelCmd,
                        rpsCmd
                )
                .queue();

        // ---- Listener ----
        jda.addEventListener(new SlashCommandListener(
                jda,
                statusService,
                maintenanceManager,
                userRepo,
                ticketService,
                databaseManager,
                gameService
        ));

        System.out.println("Lythrion main bot is running.");
    }
}
