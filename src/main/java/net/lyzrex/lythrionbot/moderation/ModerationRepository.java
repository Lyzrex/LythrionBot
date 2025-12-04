package net.lyzrex.lythrionbot.moderation;

import net.lyzrex.lythrionbot.db.DatabaseManager;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ModerationRepository {

    private final DatabaseManager databaseManager;

    public ModerationRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        initTable();
    }

    private void initTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS bot_moderation_logs (
                  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
                  guild_id         BIGINT      NOT NULL,
                  user_id          BIGINT      NOT NULL,
                  moderator_id     BIGINT      NOT NULL,
                  action           VARCHAR(16) NOT NULL,
                  reason           TEXT,
                  duration_seconds BIGINT      NULL,
                  created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_mod_user (guild_id, user_id)
                )
                """;

        try (Connection con = databaseManager.getConnection();
             Statement st = con.createStatement()) {
            st.executeUpdate(sql);
        } catch (SQLException e) {
            System.err.println("[ModerationRepository] Failed to init table: " + e.getMessage());
        }
    }

    public void logWarn(long guildId, long userId, long moderatorId, String reason) {
        insertLog(guildId, userId, moderatorId, "WARN", reason, null);
    }

    public void logBan(long guildId, long userId, long moderatorId, String reason) {
        insertLog(guildId, userId, moderatorId, "BAN", reason, null);
    }

    public void logTimeout(long guildId, long userId, long moderatorId, String reason, long durationSeconds) {
        insertLog(guildId, userId, moderatorId, "TIMEOUT", reason, durationSeconds);
    }

    private void insertLog(long guildId,
                           long userId,
                           long moderatorId,
                           String action,
                           String reason,
                           Long durationSeconds) {
        String sql = """
                INSERT INTO bot_moderation_logs
                  (guild_id, user_id, moderator_id, action, reason, duration_seconds, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, guildId);
            ps.setLong(2, userId);
            ps.setLong(3, moderatorId);
            ps.setString(4, action);
            ps.setString(5, reason);
            if (durationSeconds == null) {
                ps.setNull(6, Types.BIGINT);
            } else {
                ps.setLong(6, durationSeconds);
            }
            ps.setTimestamp(7, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[ModerationRepository] Failed to insert log: " + e.getMessage());
        }
    }

    public List<ModerationEntry> getHistory(long guildId, long userId, int limit) {
        String sql = """
                SELECT id, guild_id, user_id, moderator_id, action, reason, duration_seconds, created_at
                FROM bot_moderation_logs
                WHERE guild_id = ? AND user_id = ?
                ORDER BY id DESC
                LIMIT ?
                """;

        List<ModerationEntry> list = new ArrayList<>();

        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, guildId);
            ps.setLong(2, userId);
            ps.setInt(3, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    long gId = rs.getLong("guild_id");
                    long uId = rs.getLong("user_id");
                    long mId = rs.getLong("moderator_id");
                    String action = rs.getString("action");
                    String reason = rs.getString("reason");
                    Timestamp ts = rs.getTimestamp("created_at");
                    long createdEpoch = ts.toInstant().getEpochSecond();
                    long dur = rs.getLong("duration_seconds");
                    Long durObj = rs.wasNull() ? null : dur;

                    list.add(new ModerationEntry(
                            id, gId, uId, mId, action, reason, durObj, createdEpoch
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[ModerationRepository] Failed to load history: " + e.getMessage());
        }

        return list;
    }
}
