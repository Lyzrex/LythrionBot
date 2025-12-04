package net.lyzrex.lythrionbot.game;

import net.lyzrex.lythrionbot.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class GameScoreRepository {

    private final DatabaseManager db;

    public GameScoreRepository(DatabaseManager db) {
        this.db = db;
    }

    public GameScore getOrCreate(long userId) {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT wins, losses, draws, last_played FROM game_scores WHERE user_id = ?"
             )) {

            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new GameScore(
                            userId,
                            rs.getInt("wins"),
                            rs.getInt("losses"),
                            rs.getInt("draws"),
                            rs.getLong("last_played")
                    );
                }
            }

            // not found -> create default
            GameScore score = new GameScore(userId, 0, 0, 0, 0L);
            save(score);
            return score;

        } catch (Exception e) {
            e.printStackTrace();
            // on error: still return in-memory default, so command works
            return new GameScore(userId, 0, 0, 0, 0L);
        }
    }

    public void save(GameScore score) {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO game_scores (user_id, wins, losses, draws, last_played) " +
                             "VALUES (?, ?, ?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE " +
                             "wins = VALUES(wins), " +
                             "losses = VALUES(losses), " +
                             "draws = VALUES(draws), " +
                             "last_played = VALUES(last_played)"
             )) {

            ps.setLong(1, score.getUserId());
            ps.setInt(2, score.getWins());
            ps.setInt(3, score.getLosses());
            ps.setInt(4, score.getDraws());
            ps.setLong(5, score.getLastPlayed());

            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<GameScore> findTop(int limit) {
        List<GameScore> list = new ArrayList<>();

        String sql = """
                SELECT user_id, wins, losses, draws, last_played
                FROM game_scores
                ORDER BY (wins - losses) DESC, wins DESC
                LIMIT ?
                """;

        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long userId = rs.getLong("user_id");
                    GameScore score = new GameScore(
                            userId,
                            rs.getInt("wins"),
                            rs.getInt("losses"),
                            rs.getInt("draws"),
                            rs.getLong("last_played")
                    );
                    list.add(score);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }
}
