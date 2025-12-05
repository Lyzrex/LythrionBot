package net.lyzrex.lythrionbot.profile;

import net.lyzrex.lythrionbot.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Vollständige Zugriffsschicht für Spielerprofile aus den Tabellen
 * {@code users}, {@code user_playtime} und {@code languages}. Stellt
 * zusammengesetzte Objekte bereit, die Sprache, Playtime, Logins sowie
 * Debug- und First-Login-Informationen enthalten und vollständig aus der
 * Datenbank bezogen werden.
 */
public class UserProfileRepository {

    private static final String USERS_TABLE = "users";
    private static final String PLAYTIME_TABLE = "user_playtime";
    private static final String LANG_TABLE = "languages";

    private final DatabaseManager databaseManager;

    public UserProfileRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Optional<UserProfile> findByNameOrUuid(String input) {
        String sql = """
                SELECT u.id,
                       u.uuid,
                       u.username,
                       u.language_id,
                       l.name AS language_name,
                       COALESCE(p.play_time, 0) AS playtime_seconds,
                       COALESCE(p.login_count, 0) AS login_count,
                       u.debug_enabled,
                       u.first_login
                FROM %s u
                LEFT JOIN %s p ON p.user_id = u.id
                LEFT JOIN %s l ON l.id = u.language_id
                WHERE u.uuid = ? OR u.username = ?
                LIMIT 1
                """.formatted(USERS_TABLE, PLAYTIME_TABLE, LANG_TABLE);

        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, input);
            ps.setString(2, input);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong("id");
                    String uuid = rs.getString("uuid");
                    String username = rs.getString("username");
                    int lang = rs.getInt("language_id");
                    String langName = rs.getString("language_name");
                    long play = rs.getLong("playtime_seconds");
                    int logins = rs.getInt("login_count");
                    boolean debug = rs.getBoolean("debug_enabled");
                    Timestamp firstLogin = rs.getTimestamp("first_login");

                    return Optional.of(new UserProfile(
                            id,
                            uuid,
                            username,
                            lang,
                            langName,
                            play,
                            logins,
                            debug,
                            firstLogin != null ? firstLogin.toInstant() : null
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserProfileRepository] findByNameOrUuid failed: " + e.getMessage());
        }

        return Optional.empty();
    }

    public List<UserProfile> findTopByPlaytime(int limit) {
        String sql = """
                SELECT u.id,
                       u.uuid,
                       u.username,
                       u.language_id,
                       l.name AS language_name,
                       COALESCE(p.play_time, 0) AS playtime_seconds,
                       COALESCE(p.login_count, 0) AS login_count,
                       u.debug_enabled,
                       u.first_login
                FROM %s u
                LEFT JOIN %s p ON p.user_id = u.id
                LEFT JOIN %s l ON l.id = u.language_id
                ORDER BY playtime_seconds DESC
                LIMIT ?
                """.formatted(USERS_TABLE, PLAYTIME_TABLE, LANG_TABLE);

        List<UserProfile> list = new ArrayList<>();

        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new UserProfile(
                            rs.getLong("id"),
                            rs.getString("uuid"),
                            rs.getString("username"),
                            rs.getInt("language_id"),
                            rs.getString("language_name"),
                            rs.getLong("playtime_seconds"),
                            rs.getInt("login_count"),
                            rs.getBoolean("debug_enabled"),
                            rs.getTimestamp("first_login") != null ? rs.getTimestamp("first_login").toInstant() : null
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserProfileRepository] findTopByPlaytime failed: " + e.getMessage());
        }

        return list;
    }

    public List<UserProfile> findTopByLoginCount(int limit) {
        String sql = """
                SELECT u.id,
                       u.uuid,
                       u.username,
                       u.language_id,
                       l.name AS language_name,
                       COALESCE(p.play_time, 0) AS playtime_seconds,
                       COALESCE(p.login_count, 0) AS login_count,
                       u.debug_enabled,
                       u.first_login
                FROM %s u
                LEFT JOIN %s p ON p.user_id = u.id
                LEFT JOIN %s l ON l.id = u.language_id
                ORDER BY login_count DESC
                LIMIT ?
                """.formatted(USERS_TABLE, PLAYTIME_TABLE, LANG_TABLE);

        List<UserProfile> list = new ArrayList<>();

        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new UserProfile(
                            rs.getLong("id"),
                            rs.getString("uuid"),
                            rs.getString("username"),
                            rs.getInt("language_id"),
                            rs.getString("language_name"),
                            rs.getLong("playtime_seconds"),
                            rs.getInt("login_count"),
                            rs.getBoolean("debug_enabled"),
                            rs.getTimestamp("first_login") != null ? rs.getTimestamp("first_login").toInstant() : null
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserProfileRepository] findTopByLoginCount failed: " + e.getMessage());
        }

        return list;
    }
}
