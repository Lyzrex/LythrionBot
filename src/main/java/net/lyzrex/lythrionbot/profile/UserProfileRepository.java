package net.lyzrex.lythrionbot.profile;

import net.lyzrex.lythrionbot.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserProfileRepository {

    // TODO: change to your real table name + columns
    private static final String TABLE_NAME = "murmel_users";

    private final DatabaseManager databaseManager;

    public UserProfileRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Optional<UserProfile> findByNameOrUuid(String input) {
        String sql = "SELECT id, uuid, username, language_id, playtime_seconds, login_count, debug_enabled " +
                "FROM " + TABLE_NAME + " " +
                "WHERE uuid = ? OR username = ? " +
                "LIMIT 1";

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
                    long play = rs.getLong("playtime_seconds");
                    int logins = rs.getInt("login_count");
                    boolean debug = rs.getBoolean("debug_enabled");

                    return Optional.of(new UserProfile(id, uuid, username, lang, play, logins, debug));
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserProfileRepository] findByNameOrUuid failed: " + e.getMessage());
        }

        return Optional.empty();
    }

    public List<UserProfile> findTopByPlaytime(int limit) {
        String sql = "SELECT id, uuid, username, language_id, playtime_seconds, login_count, debug_enabled " +
                "FROM " + TABLE_NAME + " " +
                "ORDER BY playtime_seconds DESC " +
                "LIMIT ?";

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
                            rs.getLong("playtime_seconds"),
                            rs.getInt("login_count"),
                            rs.getBoolean("debug_enabled")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserProfileRepository] findTopByPlaytime failed: " + e.getMessage());
        }

        return list;
    }

    public List<UserProfile> findTopByLoginCount(int limit) {
        String sql = "SELECT id, uuid, username, language_id, playtime_seconds, login_count, debug_enabled " +
                "FROM " + TABLE_NAME + " " +
                "ORDER BY login_count DESC " +
                "LIMIT ?";

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
                            rs.getLong("playtime_seconds"),
                            rs.getInt("login_count"),
                            rs.getBoolean("debug_enabled")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserProfileRepository] findTopByLoginCount failed: " + e.getMessage());
        }

        return list;
    }
}
