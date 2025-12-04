package net.lyzrex.lythrionbot;


import net.lyzrex.lythrionbot.status.ServiceType;

public class ServiceStatus {

    private final ServiceType type;
    private final String identifier;
    private final boolean online;
    private final int playersOnline;
    private final int playersMax;
    private final String version;
    private final Long pingMs;

    public ServiceStatus(ServiceType type,
                         String identifier,
                         boolean online,
                         int playersOnline,
                         int playersMax,
                         String version,
                         Long pingMs) {
        this.type = type;
        this.identifier = identifier;
        this.online = online;
        this.playersOnline = playersOnline;
        this.playersMax = playersMax;
        this.version = version;
        this.pingMs = pingMs;
    }

    public ServiceType getType() {
        return type;
    }

    public String getIdentifier() {
        return identifier;
    }

    public boolean isOnline() {
        return online;
    }

    public int getPlayersOnline() {
        return playersOnline;
    }

    public int getPlayersMax() {
        return playersMax;
    }

    public String getVersion() {
        return version;
    }

    public Long getPingMs() {
        return pingMs;
    }
}
