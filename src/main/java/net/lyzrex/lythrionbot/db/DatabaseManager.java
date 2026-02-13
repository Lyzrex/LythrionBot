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
     * Gibt die zentrale MurmelLib Database Instanz zurück.
     */
    public Database getDatabase() {
        return MurmelAPI.getDatabase();
    }

    // --- Legacy-Methoden ---

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
            // FIX: "exists" statt "update" verwenden, da SELECT kein Update ist!
            // Das behebt den "Timeout" Fehler im /latency Command.
            MurmelAPI.getDatabase().exists("SELECT 1", null);
            return System.currentTimeMillis() - start;
        } catch (Exception e) {
            e.printStackTrace(); // Zeigt Fehler in der Konsole, falls es immer noch fehlschlägt
            return -1L;
        }
    }
}