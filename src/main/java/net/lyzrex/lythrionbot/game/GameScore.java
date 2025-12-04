package net.lyzrex.lythrionbot.game;

public class GameScore {
    private final long userId;
    private final String game;
    private final int wins;
    private final int losses;
    private final int draws;

    public GameScore(long userId, String game, int wins, int losses, int draws) {
        this.userId = userId;
        this.game = game;
        this.wins = wins;
        this.losses = losses;
        this.draws = draws;
    }

    public long getUserId() {
        return userId;
    }

    public String getGame() {
        return game;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public int getDraws() {
        return draws;
    }

    public int getTotalGames() {
        return wins + losses + draws;
    }
}
