package net.lyzrex.lythrionbot.db;

import de.murmelmeister.library.database.Database;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SyntrixRepository {

    private final Database database;

    public SyntrixRepository(Database database) {
        this.database = database;
        initTables();
    }

    private void initTables() { /* (Unverändert, siehe vorher) */ }

    // --- ECONOMY ---
    public double getBalance(int userId) {
        Double val = database.query("SELECT balance FROM economy_balances WHERE user_id = ?", 0.0, rs -> rs.getDouble("balance"), s -> s.setInt(1, userId));
        return val != null ? val : 0.0;
    }

    // --- BANK DETAILS ---
    public BankDetails getBankDetails(int userId) {
        String sql = """
            SELECT b.account_number, b.balance, b.level, b.is_frozen, l.loan_amount 
            FROM bank_accounts b 
            LEFT JOIN bank_loans l ON b.id = l.bank_id 
            WHERE b.owner_id = ?
        """;
        return database.query(sql, null, rs -> new BankDetails(
                rs.getString("account_number"),
                rs.getDouble("balance"),
                rs.getInt("level"),
                rs.getBoolean("is_frozen"),
                rs.getObject("loan_amount") != null ? rs.getDouble("loan_amount") : 0.0
        ), s -> s.setInt(1, userId));
    }

    // --- SKILLS (Alle einzeln) ---
    public SkillStats getSkillStats(int userId) {
        String sql = "SELECT * FROM player_skills WHERE user_id = ?";
        return database.query(sql, new SkillStats(0,0,0,0,0,0,0,0), rs -> new SkillStats(
                rs.getDouble("global_level"),
                rs.getDouble("combat_xp"),
                rs.getDouble("mining_xp"),
                rs.getDouble("farming_xp"),
                rs.getDouble("foraging_xp"),
                rs.getDouble("fishing_xp"),
                rs.getDouble("enchanting_xp"),
                rs.getDouble("archery_xp")
        ), s -> s.setInt(1, userId));
    }

    // --- PLAYER STATS ---
    public PlayerStats getStats(int userId) {
        String sql = "SELECT kills, deaths, mob_kills FROM player_stats WHERE user_id = ?";
        return database.query(sql, new PlayerStats(0, 0, 0), rs -> new PlayerStats(
                rs.getInt("kills"),
                rs.getInt("deaths"),
                rs.getInt("mob_kills")
        ), s -> s.setInt(1, userId));
    }

    public List<LeaderboardEntry> getGlobalLevelLeaderboard(int limit) {
        String sql = "SELECT u.username, s.global_level FROM player_skills s JOIN users u ON s.user_id = u.id ORDER BY s.global_level DESC LIMIT ?";
        return database.queryList(sql, rs -> new LeaderboardEntry(rs.getString("username"), rs.getDouble("global_level")), s -> s.setInt(1, limit));
    }

    // --- RECORDS ---
    public record BankDetails(String accountNumber, double balance, int level, boolean frozen, double loan) {}
    public record SkillStats(double global, double combat, double mining, double farming, double foraging, double fishing, double enchanting, double archery) {}
    public record PlayerStats(int kills, int deaths, int mobKills) {}
    public record LeaderboardEntry(String username, double value) {}
}