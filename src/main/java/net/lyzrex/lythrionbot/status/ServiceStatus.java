package net.lyzrex.lythrionbot.status;

public class ServiceStatus {

    private final ServiceType type;
    private final boolean online;
    private final int playersOnline;
    private final int playersMax;
    private final String version;
    private final long pingMs;

    public ServiceStatus(ServiceType type,
                         boolean online,
                         int playersOnline,
                         int playersMax,
                         String version,
                         long pingMs) {
        this.type = type;
        this.online = online;
        this.playersOnline = playersOnline;
        this.playersMax = playersMax;
        this.version = version;
        this.pingMs = pingMs;
    }

    public ServiceType getType() {
        return type;
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

    public long getPingMs() {
        return pingMs;
    }
}
