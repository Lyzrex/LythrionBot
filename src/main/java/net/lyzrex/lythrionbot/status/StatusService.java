package net.lyzrex.lythrionbot.status;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.lyzrex.lythrionbot.ConfigManager;
import net.lyzrex.lythrionbot.ServiceType;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Kümmert sich um:
 * - HTTP-Abfragen (Velocity / Lobby / Citybuild)
 * - Caching dieser Abfragen
 * - Erzeugen des Status-Embeds für /status
 * - Aktualisieren der Bot-Presence
 * - API-Ping für /latency
 */
public class StatusService {

    private static final long CACHE_TTL_MS = 15_000L; // 15s

    private final HttpClient http;
    private final MaintenanceManager maintenanceManager;

    // einfacher Cache pro Service
    private volatile CacheEntry mainCache;
    private volatile CacheEntry lobbyCache;
    private volatile CacheEntry citybuildCache;

    private final DateTimeFormatter tsFormatter =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private record CacheEntry(ServiceStatus status, long timestamp) {}

    public StatusService(MaintenanceManager maintenanceManager) {
        this.maintenanceManager = maintenanceManager;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    // ========================================================================
    // Öffentliche API
    // ========================================================================

    public ServiceStatus fetchMainStatus() {
        long now = System.currentTimeMillis();
        CacheEntry cache = mainCache;
        if (cache != null && now - cache.timestamp <= CACHE_TTL_MS) {
            return cache.status;
        }
        ServiceStatus fresh = doFetchMainStatus();
        mainCache = new CacheEntry(fresh, now);
        return fresh;
    }

    public ServiceStatus fetchLobbyStatus() {
        long now = System.currentTimeMillis();
        CacheEntry cache = lobbyCache;
        if (cache != null && now - cache.timestamp <= CACHE_TTL_MS) {
            return cache.status;
        }
        ServiceStatus fresh = fetchLythCoreStatus(
                ServiceType.LOBBY,
                ConfigManager.getString(
                        "status.lobby_url",
                        "http://138.201.19.210:8765/status?token=ServiceLobbyStatus"
                )
        );
        lobbyCache = new CacheEntry(fresh, now);
        return fresh;
    }

    public ServiceStatus fetchCitybuildStatus() {
        long now = System.currentTimeMillis();
        CacheEntry cache = citybuildCache;
        if (cache != null && now - cache.timestamp <= CACHE_TTL_MS) {
            return cache.status;
        }
        ServiceStatus fresh = fetchLythCoreStatus(
                ServiceType.CITYBUILD,
                ConfigManager.getString(
                        "status.citybuild_url",
                        "http://138.201.19.210:8766/status?token=ServiceCBStatus"
                )
        );
        citybuildCache = new CacheEntry(fresh, now);
        return fresh;
    }

    /**
     * Wird von /latency verwendet: pingt deine Status-API.
     * Standard: Lobby-Endpoint, kann über config.yml überschrieben werden.
     *
     * @return Ping in ms oder -1 bei Fehler
     */
    public long pingStatusApi() {
        String url = ConfigManager.getString(
                "status.api_ping_url",
                ConfigManager.getString(
                        "status.lobby_url",
                        "http://138.201.19.210:8765/status?token=ServiceLobbyStatus"
                )
        );

        try {
            long start = System.currentTimeMillis();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
            long ping = System.currentTimeMillis() - start;

            if (res.statusCode() >= 200 && res.statusCode() < 500) {
                return ping;
            }
            return -1L;
        } catch (Exception ex) {
            ex.printStackTrace();
            return -1L;
        }
    }

    /**
     * NEU: Wird von /latency verwendet: pingt die externe mcstatus.io API.
     * Standard: Lythrion.net Java Status.
     *
     * @return Ping in ms oder -1 bei Fehler
     */
    public long pingExternalMcStatusApi() {
        String ip = ConfigManager.getString("status.main_ip", "lythrion.net");
        String type = ConfigManager.getString("status.main_type", "java");
        String url = "https://api.mcstatus.io/v2/status/" + type + "/" + ip; // Fester URL

        try {
            long start = System.currentTimeMillis();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
            long ping = System.currentTimeMillis() - start;

            if (res.statusCode() >= 200 && res.statusCode() < 500) {
                return ping;
            }
            return -1L;
        } catch (Exception ex) {
            return -1L;
        }
    }


    /**
     * Baut das große Network-Status-Embed für /status.
     */
    public MessageEmbed buildStatusEmbed(ServiceStatus main,
                                         ServiceStatus lobby,
                                         ServiceStatus citybuild) {

        String lastCheck = tsFormatter.format(Instant.now());

        boolean mainMaint = maintenanceManager.isMaintenance(ServiceType.MAIN);
        boolean lobbyMaint = maintenanceManager.isMaintenance(ServiceType.LOBBY);
        boolean cbMaint   = maintenanceManager.isMaintenance(ServiceType.CITYBUILD);

        String mainStatusLabel = statusLabel(main.isOnline(), mainMaint);
        String lobbyStatusLabel = statusLabel(lobby.isOnline(), lobbyMaint);
        String cbStatusLabel = statusLabel(citybuild.isOnline(), cbMaint);

        int mainUptime = calcUptimePercent(main.isOnline(), mainMaint);
        int lobbyUptime = calcUptimePercent(lobby.isOnline(), lobbyMaint);
        int cbUptime = calcUptimePercent(citybuild.isOnline(), cbMaint);

        int onlineCount = 0;
        int maintenanceCount = 0;
        int offlineCount = 0;

        for (String label : new String[]{mainStatusLabel, lobbyStatusLabel, cbStatusLabel}) {
            if (label.startsWith("🟢")) onlineCount++;
            else if (label.startsWith("🟠")) maintenanceCount++;
            else if (label.startsWith("🔴")) offlineCount++;
        }

        String healthLabel = "🟢 Stable";
        String healthText = "All core services are online.";
        if (offlineCount > 0) {
            healthLabel = "🔴 Critical";
            healthText = "One or more core services are unavailable.";
        } else if (maintenanceCount > 0) {
            healthLabel = "🟠 Degraded";
            healthText = "Maintenance in progress on at least one service.";
        }

        // Network load (keine Doppelzählung)
        int totalOnline;
        int totalMax;

        if (main.isOnline() && main.getPlayersMax() > 0) {
            totalOnline = main.getPlayersOnline();
            totalMax = main.getPlayersMax();
        } else {
            totalOnline = 0;
            totalMax = 0;
            if (lobby.isOnline()) {
                totalOnline += lobby.getPlayersOnline();
                totalMax += lobby.getPlayersMax();
            }
            if (citybuild.isOnline()) {
                totalOnline += citybuild.getPlayersOnline();
                totalMax += citybuild.getPlayersMax();
            }
        }

        int loadPercent = 0;
        if (totalMax > 0) {
            loadPercent = clampPercent(Math.round(totalOnline * 100f / totalMax));
        }

        String loadBar = buildLoadBar(loadPercent);
        String playabilityLabel = buildPlayabilityLabel(healthLabel, loadPercent);

        String mainLatencyLabel = buildLatencyLabel(main.getPingMs());
        String lobbyLatencyLabel = buildLatencyLabel(lobby.getPingMs());
        String cbLatencyLabel = buildLatencyLabel(citybuild.getPingMs());

        String mainLoadLabel = buildServiceLoadLabel(main.isOnline(), main.getPlayersOnline(), main.getPlayersMax());
        String lobbyLoadLabel = buildServiceLoadLabel(lobby.isOnline(), lobby.getPlayersOnline(), lobby.getPlayersMax());
        String cbLoadLabel = buildServiceLoadLabel(citybuild.isOnline(), citybuild.getPlayersOnline(), citybuild.getPlayersMax());

        int mainUptimePercent = mainUptime;
        int lobbyUptimePercent = lobbyUptime;
        int cbUptimePercent = cbUptime;

        int networkColor = 0x22c55e; // green
        if (offlineCount > 0) {
            networkColor = 0xef4444; // red
        } else if (maintenanceCount > 0) {
            networkColor = 0xfacc15; // yellow
        }

        String mainPingText = main.getPingMs() >= 0 ? main.getPingMs() + "ms" : "N/A";
        String lobbyPingText = lobby.getPingMs() >= 0 ? lobby.getPingMs() + "ms" : "N/A";
        String cbPingText = citybuild.getPingMs() >= 0 ? citybuild.getPingMs() + "ms" : "N/A";

        String mainValue = String.join("\n",
                "> **IP:** `Lythrion.net`",
                "> **Status:** " + mainStatusLabel,
                "> **Version:** `" + main.getVersion() + "`",
                "> **Players:** " + (main.isOnline()
                        ? main.getPlayersOnline() + "/" + main.getPlayersMax()
                        : "0/0"),
                "> **Ping:** " + mainPingText + " • " + mainLatencyLabel,
                "> **Load:** " + mainLoadLabel,
                "> **Uptime:** " + mainUptimePercent + "%",
                "> " + buildUptimeBar(mainUptimePercent)
        );

        String lobbyValue = String.join("\n",
                "> **Status:** " + lobbyStatusLabel,
                "> **Version:** `" + lobby.getVersion() + "`",
                "> **Players:** " + (lobby.isOnline()
                        ? lobby.getPlayersOnline() + "/" + lobby.getPlayersMax()
                        : "0/0"),
                "> **Ping:** " + lobbyPingText + " • " + lobbyLatencyLabel,
                "> **Load:** " + lobbyLoadLabel,
                "> **Uptime:** " + lobbyUptimePercent + "%",
                "> " + buildUptimeBar(lobbyUptimePercent)
        );

        String cbValue = String.join("\n",
                "> **Status:** " + cbStatusLabel,
                "> **Version:** `" + citybuild.getVersion() + "`",
                "> **Players:** " + (citybuild.isOnline()
                        ? citybuild.getPlayersOnline() + "/" + citybuild.getPlayersMax()
                        : "0/0"),
                "> **Ping:** " + cbPingText + " • " + cbLatencyLabel,
                "> **Load:** " + cbLoadLabel,
                "> **Uptime:** " + cbUptimePercent + "%",
                "> " + buildUptimeBar(cbUptimePercent)
        );

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("⚡ Lythrion Network Status")
                .setColor(networkColor)
                .setAuthor("Lythrion Monitoring", null, "https://api.mcstatus.io/v2/icon/lythrion.net")
                .setThumbnail("https://api.mcstatus.io/v2/icon/lythrion.net")
                .setDescription(String.join("\n",
                        "**Status Legend**",
                        "🟢 Online",
                        "🔴 Offline",
                        "🟠 Maintenance",
                        "",
                        "**Latency Legend**",
                        "⚡ Stable",
                        "⭐ Playable",
                        "🐢 High latency",
                        "",
                        "⏱️ **Last check:** `" + lastCheck + "`"
                ))
                .addField(
                        "📊 Network Health",
                        String.join("\n",
                                "**State:** " + healthLabel,
                                healthText,
                                "",
                                "**Network load:** " + loadPercent + "% (" +
                                        totalOnline + "/" + (totalMax == 0 ? 0 : totalMax) + " players)",
                                loadBar,
                                "",
                                "**Playability:** " + playabilityLabel
                        ),
                        false
                )
                .addField("🚀 Main Core (Velocity Java)", mainValue, false)
                .addField("🏠 Lobby Service", lobbyValue, true)
                .addField("🏙️ Citybuild Service", cbValue, true)
                .setFooter("Lythrion Status Dashboard")
                .setTimestamp(Instant.now());

        return eb.build();
    }

    /**
     * Setzt die Rich Presence des Bots basierend auf den Statusdaten.
     */
    public void updatePresenceFromData(JDA jda,
                                       ServiceStatus main,
                                       ServiceStatus lobby,
                                       ServiceStatus citybuild) {

        int totalOnline;
        int totalMax;

        if (main.isOnline() && main.getPlayersMax() > 0) {
            totalOnline = main.getPlayersOnline();
            totalMax = main.getPlayersMax();
        } else {
            totalOnline = 0;
            totalMax = 0;
            if (lobby.isOnline()) {
                totalOnline += lobby.getPlayersOnline();
                totalMax += lobby.getPlayersMax();
            }
            if (citybuild.isOnline()) {
                totalOnline += citybuild.getPlayersOnline();
                totalMax += citybuild.getPlayersMax();
            }
        }

        String presence;
        if (totalMax > 0) {
            presence = "Playing on Lythrion.net (" + totalOnline + "/" + totalMax + ")";
        } else if (main.isOnline() || lobby.isOnline() || citybuild.isOnline()) {
            presence = "Playing on Lythrion.net (online)";
        } else {
            presence = "Lythrion.net (offline)";
        }

        jda.getPresence().setActivity(Activity.playing(presence));
    }

    // ========================================================================
    // Interne HTTP-Implementierungen
    // ========================================================================

    private ServiceStatus doFetchMainStatus() {
        String ip = ConfigManager.getString("status.main_ip", "lythrion.net");
        String type = ConfigManager.getString("status.main_type", "java");
        String url = ConfigManager.getString(
                "status.main_url",
                "https://api.mcstatus.io/v2/status/" + type + "/" + ip
        );

        try {
            long start = System.currentTimeMillis();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            long ping = System.currentTimeMillis() - start;

            boolean online = res.statusCode() == 200;
            int playersOnline = 0;
            int playersMax = 0;
            String version = "Unknown";

            if (online) {
                JSONObject json = new JSONObject(res.body());
                online = json.optBoolean("online", false);

                JSONObject playersObj = json.optJSONObject("players");
                if (playersObj != null) {
                    playersOnline = playersObj.optInt("online", 0);
                    playersMax = playersObj.optInt("max", 0);
                }

                JSONObject verObj = json.optJSONObject("version");
                if (verObj != null) {
                    version = verObj.optString(
                            "name_raw",
                            verObj.optString("name", "Unknown")
                    );
                }
            }

            return new ServiceStatus(
                    ServiceType.MAIN,
                    online,
                    playersOnline,
                    playersMax,
                    version,
                    ping
            );
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ServiceStatus(
                    ServiceType.MAIN,
                    false,
                    0,
                    0,
                    "Unknown",
                    -1
            );
        }
    }

    private ServiceStatus fetchLythCoreStatus(ServiceType type, String url) {
        try {
            long start = System.currentTimeMillis();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            long ping = System.currentTimeMillis() - start;

            boolean online = res.statusCode() == 200;
            int playersOnline = 0;
            int playersMax = 0;
            String version = "Unknown";

            if (online) {
                JSONObject json = new JSONObject(res.body());

                online = json.optBoolean("online", online);

                JSONObject playersObj = json.optJSONObject("players");
                if (playersObj != null) {
                    playersOnline = playersObj.optInt("online", 0);
                    playersMax = playersObj.optInt("max", 0);
                } else {
                    playersOnline = json.optInt("playersOnline", 0);
                    playersMax = json.optInt("playersMax", 0);
                }

                version = json.optString("version", version);
            }

            return new ServiceStatus(
                    type,
                    online,
                    playersOnline,
                    playersMax,
                    version,
                    ping
            );
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ServiceStatus(
                    type,
                    false,
                    0,
                    0,
                    "Unknown",
                    -1
            );
        }
    }

    // ========================================================================
    // Helper-Methoden (Bar, Labels, etc.)
    // ========================================================================

    private String statusLabel(boolean online, boolean maintenance) {
        if (maintenance) return "🟠 Maintenance";
        return online ? "🟢 Online" : "🔴 Offline";
    }

    private int calcUptimePercent(boolean online, boolean maintenance) {
        if (maintenance) return 75;
        return online ? 100 : 0;
    }

    private int clampPercent(int p) {
        if (p < 0) return 0;
        if (p > 100) return 100;
        return p;
    }

    private String buildUptimeBar(int percent) {
        int totalBlocks = 9;
        int clamped = clampPercent(percent);
        int filled = Math.round(clamped / 100f * totalBlocks);

        String green = "🟩";
        String red = "🟥";
        return green.repeat(filled) + red.repeat(totalBlocks - filled);
    }

    private String buildLoadBar(int percent) {
        int totalBlocks = 9;
        int clamped = clampPercent(percent);
        int redBlocks = Math.round(clamped / 100f * totalBlocks);
        int greenBlocks = totalBlocks - redBlocks;

        String green = "🟩";
        String red = "🟥";
        return green.repeat(greenBlocks) + red.repeat(redBlocks);
    }

    private String buildLatencyLabel(long ping) {
        if (ping < 0) return "Unknown";
        if (ping <= 60) return "⚡ Stable";
        if (ping <= 120) return "⭐ Playable";
        return "🐢 High latency";
    }

    private String buildServiceLoadLabel(boolean online, int playersOnline, int playersMax) {
        if (!online || playersMax <= 0) return "N/A";
        int percent = clampPercent(Math.round(playersOnline * 100f / playersMax));
        if (percent <= 40) return "Low (" + percent + "%)";
        if (percent <= 70) return "Medium (" + percent + "%)";
        if (percent <= 90) return "High (" + percent + "%)";
        return "Critical (" + percent + "%)";
    }

    private String buildPlayabilityLabel(String healthLabel, int loadPercent) {
        if ("🔴 Critical".equals(healthLabel)) {
            return "❌ Not recommended (outages detected)";
        }
        if (loadPercent >= 90) {
            return "⚠️ Possible queues and lag";
        }
        if (loadPercent >= 60) {
            return "✅ Playable, but might be busy";
        }
        return "✅ Smooth experience expected";
    }
}