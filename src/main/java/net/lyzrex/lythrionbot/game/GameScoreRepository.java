package net.lyzrex.lythrionbot.game;

import net.lyzrex.lythrionbot.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GameScoreRepository {

    public enum Outcome {
        WIN, LOSS, DRAW
    }

    private final DatabaseManager databaseManager;

    public GameScoreRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        initTable();
    }

    private void initTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS bot_game_scores (
                  user_id    BIGINT      NOT NULL,
                  game       VARCHAR(32) NOT NULL,
                  wins       INT         NOT NULL DEFAULT 0,
                  losses     INT         NOT NULL DEFAULT 0,
                  draws      INT         NOT NULL DEFAULT 0,
                  updated_at TIMESTAMP   NOT NULL
                              DEFAULT CURRENT_TIMESTAMP
                              ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (user_id, game)
                )
                """;

        try (Connection con = databaseManager.getConnection();
             Statement st = con.createStatement()) {
            st.executeUpdate(sql);
        } catch (SQLException e) {
            System.err.println("[GameScoreRepository] Failed to init table: " + e.getMessage());
        }
    }

    public void addRpsResult(long userId, Outcome outcome) {
        String sql = """
                INSERT INTO bot_game_scores (user_id, game, wins, losses, draws)
                VALUES (?, 'RPS', ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  wins   = wins   + VALUES(wins),
                  losses = losses + VALUES(losses),
                  draws  = draws  + VALUES(draws)
                """;

        int win = 0, loss = 0, draw = 0;
        switch (outcome) {
            case WIN -> win = 1;
            case LOSS -> loss = 1;
            case DRAW -> draw = 1;
        }

        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, win);
            ps.setInt(3, loss);
            ps.setInt(4, draw);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[GameScoreRepository] Failed to update RPS score: " + e.getMessage());
        }
    }

    public List<GameScore> getTopRps(int limit) {
        String sql = """
                SELECT user_id, game, wins, losses, draws
                FROM bot_game_scores
                WHERE game = 'RPS'
                ORDER BY (wins - losses) DESC, wins DESC
                LIMIT ?
                """;

        List<GameScore> list = new ArrayList<>();

        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new GameScore(
                            rs.getLong("user_id"),
                            rs.getString("game"),
                            rs.getInt("wins"),
                            rs.getInt("losses"),
                            rs.getInt("draws")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[GameScoreRepository] Failed to load RPS leaderboard: " + e.getMessage());
        }

        return list;
    }

    public GameScore getRpsScore(long userId) {
        String sql = """
                SELECT user_id, game, wins, losses, draws
                FROM bot_game_scores
                WHERE game = 'RPS' AND user_id = ?
                """;

        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new GameScore(
                            rs.getLong("user_id"),
                            rs.getString("game"),
                            rs.getInt("wins"),
                            rs.getInt("losses"),
                            rs.getInt("draws")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[GameScoreRepository] Failed to load RPS score: " + e.getMessage());
        }

        return null;
    }
}
