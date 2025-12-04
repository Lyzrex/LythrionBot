package net.lyzrex.lythrionbot.game;

public class GameScore {

    private final long userId;
    private int wins;
    private int losses;
    private int draws;
    private long lastPlayed; // epoch millis

    public GameScore(long userId, int wins, int losses, int draws, long lastPlayed) {
        this.userId = userId;
        this.wins = wins;
        this.losses = losses;
        this.draws = draws;
        this.lastPlayed = lastPlayed;
    }

    public long getUserId() {
        return userId;
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

    public long getLastPlayed() {
        return lastPlayed;
    }

    public void addWin() {
        wins++;
        lastPlayed = System.currentTimeMillis();
    }

    public void addLoss() {
        losses++;
        lastPlayed = System.currentTimeMillis();
    }

    public void addDraw() {
        draws++;
        lastPlayed = System.currentTimeMillis();
    }

    public int getTotalGames() {
        return wins + losses + draws;
    }

    public double getWinRate() {
        int total = getTotalGames();
        if (total == 0) return 0.0;
        return (wins * 100.0) / total;
    }
}
