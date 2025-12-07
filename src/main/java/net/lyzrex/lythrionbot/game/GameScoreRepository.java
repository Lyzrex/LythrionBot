package net.lyzrex.lythrionbot.game;

import de.murmelmeister.library.database.Database;
import de.murmelmeister.library.database.ResultSetProcessor;

import java.util.List;
import java.util.Optional;

public class GameScoreRepository {

    private final Database database; // Korrigierte Abhängigkeit
    private static final String TABLE_NAME = "game_scores";

    // Reusable processor to map SQL result to a GameScore object
    private static final ResultSetProcessor<GameScore> SCORE_PROCESSOR = rs -> {
        return new GameScore(
                rs.getLong("user_id"),
                rs.getInt("wins"),
                rs.getInt("losses"),
                rs.getInt("draws"),
                rs.getLong("last_played")
        );
    };

    public GameScoreRepository(Database database) { // Korrigierter Konstruktor
        this.database = database;
    }

    /**
     * Retrieves an existing GameScore or creates a new default one, saving it to the database.
     */
    public GameScore getOrCreate(long userId) {
        String selectSql = "SELECT wins, losses, draws, last_played FROM " + TABLE_NAME + " WHERE user_id = ?";

        // 1. Attempt to fetch existing score using database.query()
        GameScore existing = database.query(
                selectSql,
                null,
                SCORE_PROCESSOR,
                stmt -> stmt.setLong(1, userId)
        );

        if (existing != null) {
            return existing;
        }

        // 2. If not found, create default and save
        GameScore newScore = new GameScore(userId, 0, 0, 0, 0L);
        save(newScore);
        return newScore;
    }

    /**
     * Saves or updates the GameScore in the database using ON DUPLICATE KEY UPDATE.
     */
    public void save(GameScore score) {
        String sql = "INSERT INTO " + TABLE_NAME + " (user_id, wins, losses, draws, last_played) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "wins = VALUES(wins), " +
                "losses = VALUES(losses), " +
                "draws = VALUES(draws), " +
                "last_played = VALUES(last_played)";

        // Nutzt database.update()
        database.update(
                sql,
                stmt -> {
                    stmt.setLong(1, score.getUserId());
                    stmt.setInt(2, score.getWins());
                    stmt.setInt(3, score.getLosses());
                    stmt.setInt(4, score.getDraws());
                    stmt.setLong(5, score.getLastPlayed());
                }
        );
    }

    /**
     * Finds the top N scores based on (wins - losses) and then by wins.
     */
    public List<GameScore> findTop(int limit) {
        String sql = "SELECT user_id, wins, losses, draws, last_played " +
                "FROM " + TABLE_NAME + " " +
                "ORDER BY (wins - losses) DESC, wins DESC " +
                "LIMIT ?";

        // Nutzt database.queryList()
        return database.queryList(
                sql,
                SCORE_PROCESSOR,
                stmt -> stmt.setInt(1, limit)
        );
    }
}