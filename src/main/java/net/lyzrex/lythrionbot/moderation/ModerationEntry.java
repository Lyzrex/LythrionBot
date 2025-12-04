package net.lyzrex.lythrionbot.moderation;

public class ModerationEntry {

    private final long id;
    private final long guildId;
    private final long userId;
    private final long moderatorId;
    private final String action;
    private final String reason;
    private final Long durationSeconds;
    private final long createdAtEpoch;

    public ModerationEntry(long id,
                           long guildId,
                           long userId,
                           long moderatorId,
                           String action,
                           String reason,
                           Long durationSeconds,
                           long createdAtEpoch) {
        this.id = id;
        this.guildId = guildId;
        this.userId = userId;
        this.moderatorId = moderatorId;
        this.action = action;
        this.reason = reason;
        this.durationSeconds = durationSeconds;
        this.createdAtEpoch = createdAtEpoch;
    }

    public long getId() {
        return id;
    }

    public long getGuildId() {
        return guildId;
    }

    public long getUserId() {
        return userId;
    }

    public long getModeratorId() {
        return moderatorId;
    }

    public String getAction() {
        return action;
    }

    public String getReason() {
        return reason;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public long getCreatedAtEpoch() {
        return createdAtEpoch;
    }
}
