package net.lyzrex.lythrionbot.game;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.lyzrex.lythrionbot.i18n.Messages;

import de.murmelmeister.murmelapi.MurmelAPI;
import de.murmelmeister.murmelapi.language.message.MessageService;
import de.murmelmeister.murmelapi.user.UserProvider;

import java.time.Instant;
import java.util.List;
import java.util.Random;

public class GameService {

    private final GameScoreRepository repo;
    private final UserProvider userProvider;
    private final Random random = new Random();
    private long virtualBalance = 1000; // SIMULIERTE BALANCE FÜR DAS WÜRFELSPIEL

    public GameService(GameScoreRepository repo, UserProvider userProvider) {
        this.repo = repo;
        this.userProvider = userProvider;
    }

    // Hilfsfunktion zum Abrufen von MurmelAPI MessageService
    private MessageService getMessageService() {
        return MurmelAPI.getMessageService();
    }

    // Hilfsfunktion zur Ermittlung der Sprach-ID des Spielers
    private int getPlayerLanguageId(long discordUserId) {
        // HINWEIS: Dies ist ein Placeholder. Echte Implementierung benötigt User/Discord-Mapping.
        // Wir verwenden MurmelAPI-Standard: ID 1 (EN)
        return 1;
    }


    // /rps play choice:<rock|paper|scissors>
    public void handleRpsPlay(SlashCommandInteractionEvent event) {
        String choice = event.getOption("choice").getAsString().toLowerCase();
        User user = event.getUser();

        // 1. Sprache und Service abrufen
        int langId = getPlayerLanguageId(user.getIdLong());
        MessageService msgService = getMessageService();

        RpsMove playerMove = RpsMove.fromId(choice);
        if (playerMove == null) {
            event.reply("❌ Invalid choice. Use `rock`, `paper` or `scissors`.").setEphemeral(true).queue();
            return;
        }

        RpsMove botMove = RpsMove.values()[random.nextInt(RpsMove.values().length)];
        Result result = evaluate(playerMove, botMove);

        GameScore score = repo.getOrCreate(user.getIdLong());
        switch (result) {
            case WIN -> score.addWin();
            case LOSS -> score.addLoss();
            case DRAW -> score.addDraw();
        }
        repo.save(score);

        // 2. KORRIGIERT: Titel über MurmelAPI MessageService abrufen (mit Fallback)
        String titleKey = switch (result) {
            case WIN -> "rps.result.win";
            case LOSS -> "rps.result.loss";
            case DRAW -> "rps.result.draw";
        };

        String defaultMsg = switch (result) {
            case WIN -> "🎉 You win!";
            case LOSS -> "😢 You lose!";
            case DRAW -> "🤝 It's a draw!";
        };

        String title = msgService.getMessage(titleKey, langId);
        if (title == null) {
            title = defaultMsg;
        }


        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🪨📄✂ Rock Paper Scissors")
                .setColor(result == Result.WIN ? 0x22c55e : (result == Result.LOSS ? 0xef4444 : 0xfacc15))
                .setDescription(title)
                .addField("Your move", emoji(playerMove) + " " + playerMove.getLabel(), true)
                .addField("Bot move", emoji(botMove) + " " + botMove.getLabel(), true)
                .addField("Stats",
                        "Wins: **" + score.getWins() + "**\n" +
                                "Losses: **" + score.getLosses() + "**\n" +
                                "Draws: **" + score.getDraws() + "**\n" +
                                "Win rate: **" + String.format("%.1f", score.getWinRate()) + "%**",
                        false)
                .setFooter("Requested by " + user.getAsTag())
                .setTimestamp(Instant.now());

        event.replyEmbeds(eb.build()).queue();
    }

    // /rps stats
    public void handleRpsStats(SlashCommandInteractionEvent event) {
        User user = event.getUser();
        GameScore score = repo.getOrCreate(user.getIdLong());

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("📊 RPS stats for " + user.getAsTag())
                .setColor(0x22c55e)
                .addField("Wins", String.valueOf(score.getWins()), true)
                .addField("Losses", String.valueOf(score.getLosses()), true)
                .addField("Draws", String.valueOf(score.getDraws()), true)
                .addField("Total games", String.valueOf(score.getTotalGames()), true)
                .addField("Win rate", String.format("%.1f%%", score.getWinRate()), true)
                .setTimestamp(Instant.now());

        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    // /rps top
    public void handleRpsTop(SlashCommandInteractionEvent event) {
        // KORRIGIERT: Verwende den einfachen Klassennamen GameScore, da er im selben Paket liegt
        List<GameScore> top = repo.findTop(10);

        if (top.isEmpty()) {
            event.reply("📊 No game data yet. Play some `/rps play` rounds first!")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        int rank = 1;
        for (GameScore s : top) { // KORRIGIERT: Verwende GameScore
            sb.append("`#").append(rank++).append("` ")
                    .append("<@").append(s.getUserId()).append("> – ")
                    .append("W: ").append(s.getWins())
                    .append(" / L: ").append(s.getLosses())
                    .append(" / D: ").append(s.getDraws())
                    .append(" (Win rate: ")
                    .append(String.format("%.1f%%", s.getWinRate()))
                    .append(")\n");
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🏆 RPS Leaderboard")
                .setColor(0x22c55e)
                .setDescription(sb.toString())
                .setTimestamp(Instant.now());

        event.replyEmbeds(eb.build()).queue();
    }


    // NEU: Würfelspiel Logik
    public void handleDiceRoll(SlashCommandInteractionEvent event) {
        long betAmount;
        try {
            betAmount = event.getOption("betrag").getAsLong();
        } catch (Exception e) {
            event.reply("❌ Ungültiger Wettbetrag.").setEphemeral(true).queue();
            return;
        }

        if (betAmount <= 0) {
            event.reply("❌ Der Wetteinsatz muss positiv sein.").setEphemeral(true).queue();
            return;
        }
        if (betAmount > virtualBalance) {
            event.reply("❌ Du hast nicht genug Guthaben (Aktuell: " + virtualBalance + ").").setEphemeral(true).queue();
            return;
        }

        // Würfel den Bot-Wurf (Zahl 1-6)
        int botRoll = random.nextInt(6) + 1;
        // Gewinnen bei 5 oder 6
        boolean win = botRoll >= 5;

        String resultEmoji = win ? "🎉" : "😢";
        String resultText = win ? "GEWONNEN" : "VERLOREN";
        long change = win ? betAmount : -betAmount;
        virtualBalance += change;

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(resultEmoji + " Würfelspiel: Du hast " + resultText + "!")
                .setColor(win ? 0x22c55e : 0xef4444)
                .addField("Dein Wurf (Bot)", String.valueOf(botRoll), true)
                .addField("Ziel", "5 oder höher", true)
                .addField("Gewinn/Verlust", (change > 0 ? "+" : "") + change, true)
                .addField("Neues Guthaben (Simuliert)", String.valueOf(virtualBalance), false)
                .setFooter("Requested by " + event.getUser().getAsTag())
                .setTimestamp(Instant.now());

        event.replyEmbeds(eb.build()).queue();
    }


    // ---------- intern ----------

    private Result evaluate(RpsMove player, RpsMove bot) {
        if (player == bot) return Result.DRAW;
        return switch (player) {
            case ROCK -> (bot == RpsMove.SCISSORS) ? Result.WIN : Result.LOSS;
            case PAPER -> (bot == RpsMove.ROCK) ? Result.WIN : Result.LOSS;
            case SCISSORS -> (bot == RpsMove.PAPER) ? Result.WIN : Result.LOSS;
        };
    }

    private String emoji(RpsMove move) {
        return switch (move) {
            case ROCK -> "🪨";
            case PAPER -> "📄";
            case SCISSORS -> "✂️";
        };
    }

    private enum Result {
        WIN, LOSS, DRAW
    }

    public enum RpsMove {
        ROCK("rock", "Rock"),
        PAPER("paper", "Paper"),
        SCISSORS("scissors", "Scissors");

        private final String id;
        private final String label;

        RpsMove(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        public static RpsMove fromId(String id) {
            for (RpsMove m : values()) {
                if (m.id.equalsIgnoreCase(id)) return m;
            }
            return null;
        }
    }
}