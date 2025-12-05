package net.lyzrex.lythrionbot.profile;

import java.time.Instant;

/**
 * Vollständige, immutable Datenklasse für Benutzerprofile, befüllt aus den
 * Kern-MurmelAPI-Tabellen. Diese Klasse wird direkt in Embeds ausgegeben und
 * bündelt alle Felder, die aus der Datenbank gelesen werden.
 */
public class UserProfile {

    private final long id;
    private final String uuid;
    private final String username;
    private final int languageId;
    private final String languageName;
    private final long playTimeSeconds;
    private final int loginCount;
    private final boolean debugEnabled;
    private final Instant firstLogin;

    public UserProfile(long id,
                       String uuid,
                       String username,
                       int languageId,
                       String languageName,
                       long playTimeSeconds,
                       int loginCount,
                       boolean debugEnabled,
                       Instant firstLogin) {
        this.id = id;
        this.uuid = uuid;
        this.username = username;
        this.languageId = languageId;
        this.languageName = languageName;
        this.playTimeSeconds = playTimeSeconds;
        this.loginCount = loginCount;
        this.debugEnabled = debugEnabled;
        this.firstLogin = firstLogin;
    }

    public long getId() {
        return id;
    }

    public String getUuid() {
        return uuid;
    }

    public String getUsername() {
        return username;
    }

    public int getLanguageId() {
        return languageId;
    }

    public String getLanguageName() {
        return languageName;
    }

    public long getPlayTimeSeconds() {
        return playTimeSeconds;
    }

    public int getLoginCount() {
        return loginCount;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public long getPlaytimeSeconds() {
        return playTimeSeconds;
    }

    public Instant getFirstLogin() {
        return firstLogin;
    }
}
