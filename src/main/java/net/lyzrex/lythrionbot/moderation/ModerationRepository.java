package net.lyzrex.lythrionbot.moderation;

import de.murmelmeister.library.database.Database;
import de.murmelmeister.library.database.ResultSetProcessor;

import java.sql.Types;
import java.time.Instant;
import java.util.List;

public class ModerationRepository {

    private final Database database; // Korrigierte Abhängigkeit
    private static final String TABLE_NAME = "bot_moderation_logs";

    public ModerationRepository(Database database) { // Korrigierter Konstruktor
        this.database = database;
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

        // Nutzt database.update() zur Erstellung der Tabelle
        try {
            database.update(sql);
        } catch (Exception e) {
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
                VALUES (?, ?, ?, ?, ?, ?, FROM_UNIXTIME(?))
                """;

        // Nutzt database.update() zur transaktionalen Ausführung
        try {
            database.update(sql, ps -> {
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
                // Speichert den Zeitstempel als Epoch-Sekunden
                ps.setLong(7, Instant.now().getEpochSecond());
            });
        } catch (Exception e) {
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

        // Definiere den Prozessor, um die Ergebnisse in ModerationEntry zu mappen
        ResultSetProcessor<ModerationEntry> processor = rs -> {
            long dur = rs.getLong("duration_seconds");
            Long durObj = rs.wasNull() ? null : dur;

            return new ModerationEntry(
                    rs.getLong("id"),
                    rs.getLong("guild_id"),
                    rs.getLong("user_id"),
                    rs.getLong("moderator_id"),
                    rs.getString("action"),
                    rs.getString("reason"),
                    durObj,
                    // Nutzt getTimestamp und Instant für saubere Konvertierung zu Epoch-Sekunden
                    rs.getTimestamp("created_at").toInstant().getEpochSecond()
            );
        };

        // Nutzt database.queryList()
        return database.queryList(
                sql,
                processor,
                ps -> {
                    ps.setLong(1, guildId);
                    ps.setLong(2, userId);
                    ps.setInt(3, limit);
                }
        );
    }
}