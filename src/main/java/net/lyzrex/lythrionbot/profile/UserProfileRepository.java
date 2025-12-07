package net.lyzrex.lythrionbot.profile;

import de.murmelmeister.library.database.Database;
import de.murmelmeister.library.database.ResultSetProcessor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class UserProfileRepository {

    // TODO: change to your real table name + columns
    private static final String TABLE_NAME = "murmel_users";

    private final Database database; // Geändert von DatabaseManager

    // Privat, statisch, um den ResultSet in ein UserProfile-Objekt zu mappen
    private static final ResultSetProcessor<UserProfile> PROFILE_PROCESSOR = rs -> {
        // Konvertiert SQL DATETIME sicher in LocalDateTime und berücksichtigt NULL-Werte
        LocalDateTime firstLogin = rs.getObject("first_login") != null ?
                rs.getTimestamp("first_login").toLocalDateTime() :
                null;

        return new UserProfile(
                rs.getLong("id"),
                rs.getString("uuid"),
                rs.getString("username"),
                rs.getInt("language_id"),
                rs.getLong("playtime_seconds"),
                rs.getInt("login_count"),
                rs.getBoolean("debug_enabled"),
                firstLogin
        );
    };

    public UserProfileRepository(Database database) { // Geänderter Konstruktor
        this.database = database;
    }

    public Optional<UserProfile> findByNameOrUuid(String input) {
        String sql = "SELECT id, uuid, username, language_id, playtime_seconds, login_count, debug_enabled, first_login " +
                "FROM " + TABLE_NAME + " " +
                "WHERE uuid = ? OR username = ? " +
                "LIMIT 1";

        // Nutzt database.query() für eine einzelne Zeile
        UserProfile profile = database.query(
                sql,
                null, // Fallback-Wert ist null
                PROFILE_PROCESSOR,
                stmt -> {
                    stmt.setString(1, input);
                    stmt.setString(2, input);
                }
        );

        return Optional.ofNullable(profile);
    }

    public List<UserProfile> findTopByPlaytime(int limit) {
        String sql = "SELECT id, uuid, username, language_id, playtime_seconds, login_count, debug_enabled, first_login " +
                "FROM " + TABLE_NAME + " " +
                "ORDER BY playtime_seconds DESC " +
                "LIMIT ?";

        // Nutzt database.queryList() für Listen
        return database.queryList(
                sql,
                PROFILE_PROCESSOR,
                stmt -> stmt.setInt(1, limit)
        );
    }

    public List<UserProfile> findTopByLoginCount(int limit) {
        String sql = "SELECT id, uuid, username, language_id, playtime_seconds, login_count, debug_enabled, first_login " +
                "FROM " + TABLE_NAME + " " +
                "ORDER BY login_count DESC " +
                "LIMIT ?";

        // Nutzt database.queryList() für Listen
        return database.queryList(
                sql,
                PROFILE_PROCESSOR,
                stmt -> stmt.setInt(1, limit)
        );
    }
}