package net.lyzrex.lythrionbot.db;

import de.murmelmeister.library.database.Database;
import de.murmelmeister.murmelapi.MurmelAPI;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {

    public DatabaseManager() {
        // Die Datenbankverbindung wird von MurmelAPI verwaltet.
    }

    /**
     * Gibt die zentrale MurmelLib Database Instanz zurück, die mit HikariCP verbunden ist.
     */
    public Database getDatabase() {
        return MurmelAPI.getDatabase();
    }

    // --- Legacy-Methoden, die nun eine Ausnahme werfen oder delegieren ---

    @Deprecated
    public Connection getConnection() throws SQLException {
        throw new UnsupportedOperationException("Raw java.sql.Connection access is deprecated. Use DatabaseManager.getDatabase() for transactional operations.");
    }

    @Deprecated
    public String getJdbcUrl() {
        return "N/A - Use MurmelAPI.getDatabase()";
    }

    @Deprecated
    public String getUser() {
        return "N/A";
    }

    public long ping() {
        long start = System.currentTimeMillis();
        try {
            // Führt eine einfache, transaktionale Abfrage über MurmelLib Database durch.
            MurmelAPI.getDatabase().update("SELECT 1");
            return System.currentTimeMillis() - start;
        } catch (Exception ignored) {
            return -1L; // Fehler bei Verbindung/Abfrage
        }
    }
}