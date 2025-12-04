package net.lyzrex.lythrionbot.game;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;


import java.time.Instant;
import java.util.List;
import java.util.Random;

public class GameService {

    private final GameScoreRepository repo;
    private final Random random = new Random();

    public GameService(GameScoreRepository repo) {
        this.repo = repo;
    }

    // /rps play choice:<rock|paper|scissors>
    public void handleRpsPlay(SlashCommandInteractionEvent event) {
        String choice = event.getOption("choice").getAsString().toLowerCase();
        Member member = event.getMember();
        User user = event.getUser();

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

        String title = switch (result) {
            case WIN -> "🎉 You win!";
            case LOSS -> "😢 You lose!";
            case DRAW -> "🤝 It's a draw!";
        };

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
        List<GameScore> top = repo.findTop(10);

        if (top.isEmpty()) {
            event.reply("📊 No game data yet. Play some `/rps play` rounds first!")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        int rank = 1;
        for (GameScore s : top) {
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
