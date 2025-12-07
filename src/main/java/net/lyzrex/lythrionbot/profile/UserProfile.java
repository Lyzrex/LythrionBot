package net.lyzrex.lythrionbot.profile;

import java.time.LocalDateTime;

public class UserProfile {

    private final long id;
    private final String uuid;
    private final String username;
    private final int languageId;
    private final long playTimeSeconds;
    private final int loginCount;
    private final boolean debugEnabled;
    private final LocalDateTime firstLogin; // Geändert zu LocalDateTime

    public UserProfile(long id,
                       String uuid,
                       String username,
                       int languageId,
                       long playTimeSeconds,
                       int loginCount,
                       boolean debugEnabled,
                       LocalDateTime firstLogin) { // Geändert zu LocalDateTime
        this.id = id;
        this.uuid = uuid;
        this.username = username;
        this.languageId = languageId;
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

    public long getPlayTimeSeconds() {
        return playTimeSeconds;
    }

    public int getLoginCount() {
        return loginCount;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public LocalDateTime getFirstLogin() {
        return firstLogin;
    }
}