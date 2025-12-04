package net.lyzrex.lythrionbot.status;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

public class StatusService {

    private static final String MAIN_IP = "lythrion.net";
    private static final String MCSTATUS_URL = "https://api.mcstatus.io/v2/status/java/" + MAIN_IP;

    private static final String LOBBY_URL =
            "http://138.201.19.210:8765/status?token=ServiceLobbyStatus";
    private static final String CITYBUILD_URL =
            "http://138.201.19.210:8766/status?token=ServiceCBStatus";

    private final HttpClient httpClient;
    private final MaintenanceManager maintenanceManager;

    public record StatusTriple(ServiceStatus main, ServiceStatus lobby, ServiceStatus citybuild) {
    }

    public StatusService(MaintenanceManager maintenanceManager) {
        this.maintenanceManager = maintenanceManager;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    // ------------------------------------------------------------------------
    // Fetch
    // ------------------------------------------------------------------------

    public StatusTriple fetchAllFast() {
        ServiceStatus main = fetchMainStatus();
        ServiceStatus lobby = fetchServiceStatus(net.lyzrex.lythrionbot.status.ServiceType.LOBBY, LOBBY_URL);
        ServiceStatus citybuild = fetchServiceStatus(net.lyzrex.lythrionbot.status.ServiceType.CITYBUILD, CITYBUILD_URL);
        return new StatusTriple(main, lobby, citybuild);
    }

    private ServiceStatus fetchMainStatus() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MCSTATUS_URL))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            long start = System.nanoTime();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long pingMs = (System.nanoTime() - start) / 1_000_000L;

            if (response.statusCode() != 200) {
                return new ServiceStatus(net.lyzrex.lythrionbot.status.ServiceType.MAIN, false, 0, 0, "Unknown", -1);
            }

            JSONObject json = new JSONObject(response.body());
            boolean online = json.optBoolean("online", false);

            int playersOnline = 0;
            int playersMax = 0;
            if (online && json.has("players")) {
                JSONObject players = json.optJSONObject("players");
                if (players != null) {
                    playersOnline = players.optInt("online", 0);
                    playersMax = players.optInt("max", 0);
                }
            }

            String version = "Unknown";
            if (online && json.has("version")) {
                JSONObject ver = json.optJSONObject("version");
                if (ver != null) {
                    version = ver.optString("name_raw",
                            ver.optString("name", "Unknown"));
                }
            }

            // clamp main ping visually to 2–23ms as gewünscht
            if (pingMs < 2) pingMs = 2;
            if (pingMs > 23) pingMs = 23;

            return new ServiceStatus(net.lyzrex.lythrionbot.status.ServiceType.MAIN, online, playersOnline, playersMax, version, pingMs);
        } catch (Exception e) {
            System.err.println("[StatusService] Failed to fetch main status: " + e.getMessage());
            return new ServiceStatus(net.lyzrex.lythrionbot.status.ServiceType.MAIN, false, 0, 0, "Unknown", -1);
        }
    }

    private ServiceStatus fetchServiceStatus(net.lyzrex.lythrionbot.status.ServiceType type, String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            long start = System.nanoTime();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long pingMs = (System.nanoTime() - start) / 1_000_000L;

            boolean online = response.statusCode() == 200;
            int playersOnline = 0;
            int playersMax = 0;
            String version = "Unknown";

            if (online) {
                JSONObject json = new JSONObject(response.body());
                online = json.optBoolean("online", online);

                if (json.has("players")) {
                    JSONObject players = json.optJSONObject("players");
                    if (players != null) {
                        playersOnline = players.optInt("online", 0);
                        playersMax = players.optInt("max", 0);
                    }
                }

                version = json.optString("version", "Unknown");
            }

            return new ServiceStatus(type, online, playersOnline, playersMax, version, pingMs);
        } catch (Exception e) {
            System.err.println("[StatusService] Failed to fetch service status (" + type + "): " + e.getMessage());
            return new ServiceStatus(type, false, 0, 0, "Unknown", -1);
        }
    }

    public long pingApi(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            long start = System.nanoTime();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            long pingMs = (System.nanoTime() - start) / 1_000_000L;

            if (response.statusCode() != 200) return -1;
            return pingMs;
        } catch (Exception e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------------
    // Presence
    // ------------------------------------------------------------------------

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
            totalOnline =
                    (lobby.isOnline() ? lobby.getPlayersOnline() : 0) +
                            (citybuild.isOnline() ? citybuild.getPlayersOnline() : 0);
            totalMax =
                    (lobby.isOnline() ? lobby.getPlayersMax() : 0) +
                            (citybuild.isOnline() ? citybuild.getPlayersMax() : 0);
        }

        String presenceText;
        if (totalMax > 0) {
            presenceText = "on Lythrion.net (" + totalOnline + " of " + totalMax + ")";
        } else {
            presenceText = "on Lythrion.net";
        }

        jda.getPresence().setActivity(Activity.playing(presenceText));
    }

    // ------------------------------------------------------------------------
    // Embed
    // ------------------------------------------------------------------------

    public MessageEmbed buildStatusEmbed(ServiceStatus main,
                                         ServiceStatus lobby,
                                         ServiceStatus citybuild) {

        String lastCheck = formatTimestamp();

        // service states with maintenance
        ServiceView mainView = toServiceView(main, maintenanceManager.isMaintenance(net.lyzrex.lythrionbot.status.ServiceType.MAIN));
        ServiceView lobbyView = toServiceView(lobby, maintenanceManager.isMaintenance(net.lyzrex.lythrionbot.status.ServiceType.LOBBY));
        ServiceView cbView = toServiceView(citybuild, maintenanceManager.isMaintenance(net.lyzrex.lythrionbot.status.ServiceType.CITYBUILD));

        int offlineCount =
                (mainView.state.equals("offline") ? 1 : 0) +
                        (lobbyView.state.equals("offline") ? 1 : 0) +
                        (cbView.state.equals("offline") ? 1 : 0);

        int maintCount =
                (mainView.state.equals("maintenance") ? 1 : 0) +
                        (lobbyView.state.equals("maintenance") ? 1 : 0) +
                        (cbView.state.equals("maintenance") ? 1 : 0);

        String healthLabel = "🟢 Stable";
        String healthText = "All core services are online.";
        int color = 0x22c55e;
        if (offlineCount > 0) {
            healthLabel = "🔴 Critical";
            healthText = "One or more core services are currently unavailable.";
            color = 0xef4444;
        } else if (maintCount > 0) {
            healthLabel = "🟠 Degraded";
            healthText = "Maintenance is in progress on at least one core service.";
            color = 0xfacc15;
        }

        // network load (no double count)
        int totalOnline;
        int totalMax;
        if (mainView.state.equals("online") && main.getPlayersMax() > 0) {
            totalOnline = main.getPlayersOnline();
            totalMax = main.getPlayersMax();
        } else {
            totalOnline =
                    (lobbyView.state.equals("online") ? lobby.getPlayersOnline() : 0) +
                            (cbView.state.equals("online") ? citybuild.getPlayersOnline() : 0);
            totalMax =
                    (lobbyView.state.equals("online") ? lobby.getPlayersMax() : 0) +
                            (cbView.state.equals("online") ? citybuild.getPlayersMax() : 0);
        }

        int loadPercent = 0;
        if (totalMax > 0) {
            loadPercent = Math.toIntExact(Math.max(0, Math.min(100,
                    Math.round((totalOnline / (double) totalMax) * 100))));
        }
        String loadBar = buildLoadBar(loadPercent);
        String playabilityLabel = buildPlayabilityLabel(healthLabel, loadPercent);

        // per-service latency labels
        String mainLatency = buildLatencyLabel(main.getPingMs());
        String lobbyLatency = buildLatencyLabel(lobby.getPingMs());
        String cbLatency = buildLatencyLabel(citybuild.getPingMs());

        String mainPing = main.getPingMs() >= 0 ? main.getPingMs() + "ms" : "N/A";
        String lobbyPing = lobby.getPingMs() >= 0 ? lobby.getPingMs() + "ms" : "N/A";
        String cbPing = citybuild.getPingMs() >= 0 ? citybuild.getPingMs() + "ms" : "N/A";

        String mainValue = String.join("\n",
                "> **IP:** `Lythrion.net`",
                "> **Status:** " + mainView.label,
                "> **Version:** `" + main.getVersion() + "`",
                "> **Players:** " + (main.isOnline()
                        ? main.getPlayersOnline() + "/" + main.getPlayersMax()
                        : "0/0"),
                "> **Ping:** " + mainPing + " • " + mainLatency,
                "> **Load:** " + buildServiceLoadLabel(mainView.state.equals("online"),
                        main.getPlayersOnline(), main.getPlayersMax()),
                "> **Uptime:** " + mainView.uptime + "%",
                "> " + buildUptimeBar(mainView.uptime)
        );

        String lobbyValue = String.join("\n",
                "> **Status:** " + lobbyView.label,
                "> **Version:** `" + lobby.getVersion() + "`",
                "> **Players:** " + (lobby.isOnline()
                        ? lobby.getPlayersOnline() + "/" + lobby.getPlayersMax()
                        : "0/0"),
                "> **Ping:** " + lobbyPing + " • " + lobbyLatency,
                "> **Load:** " + buildServiceLoadLabel(lobbyView.state.equals("online"),
                        lobby.getPlayersOnline(), lobby.getPlayersMax()),
                "> **Uptime:** " + lobbyView.uptime + "%",
                "> " + buildUptimeBar(lobbyView.uptime)
        );

        String cbValue = String.join("\n",
                "> **Status:** " + cbView.label,
                "> **Version:** `" + citybuild.getVersion() + "`",
                "> **Players:** " + (citybuild.isOnline()
                        ? citybuild.getPlayersOnline() + "/" + citybuild.getPlayersMax()
                        : "0/0"),
                "> **Ping:** " + cbPing + " • " + cbLatency,
                "> **Load:** " + buildServiceLoadLabel(cbView.state.equals("online"),
                        citybuild.getPlayersOnline(), citybuild.getPlayersMax()),
                "> **Uptime:** " + cbView.uptime + "%",
                "> " + buildUptimeBar(cbView.uptime)
        );

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("⚡ Lythrion Network Status")
                .setColor(color)
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
                .addField("🚀 Velocity Core (Java Main)", mainValue, false)
                .addField("🏠 Lobby Service", lobbyValue, true)
                .addField("🏙️ Citybuild Service", cbValue, true)
                .setFooter("Lythrion Status Dashboard")
                .setTimestamp(Instant.now());

        return eb.build();
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private record ServiceView(String label, int uptime, String state) {
    }

    private ServiceView toServiceView(ServiceStatus status, boolean maintenance) {
        if (maintenance) {
            return new ServiceView("🟠 Maintenance", 75, "maintenance");
        }
        if (status.isOnline()) {
            return new ServiceView("🟢 Online", 100, "online");
        }
        return new ServiceView("🔴 Offline", 0, "offline");
    }

    private String formatTimestamp() {
        Instant now = Instant.now();
        return java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withZone(java.time.ZoneId.systemDefault())
                .format(now);
    }

    private String buildUptimeBar(int percent) {
        int totalBlocks = 9;
        int clamped = Math.max(0, Math.min(100, percent));
        int filled = Math.max(0, Math.min(totalBlocks, Math.round(clamped / 9f)));
        String green = "🟩";
        String red = "🟥";
        return green.repeat(filled) + red.repeat(totalBlocks - filled);
    }

    private String buildLoadBar(int percent) {
        int totalBlocks = 9;
        int clamped = Math.max(0, Math.min(100, percent));
        int redBlocks = Math.max(0, Math.min(totalBlocks, Math.round(clamped / 9f)));
        int greenBlocks = totalBlocks - redBlocks;
        String green = "🟩";
        String red = "🟥";
        return green.repeat(greenBlocks) + red.repeat(redBlocks);
    }

    private String buildLatencyLabel(long pingMs) {
        if (pingMs < 0) return "Unknown";
        if (pingMs <= 60) return "⚡ Stable";
        if (pingMs <= 120) return "⭐ Playable";
        return "🐢 High latency";
    }

    private String buildServiceLoadLabel(boolean online, int playersOnline, int playersMax) {
        if (!online || playersMax <= 0) return "N/A";
        double percent = (playersOnline / (double) playersMax) * 100.0;
        int p = (int) Math.round(percent);
        if (percent <= 40) return "Low (" + p + "%)";
        if (percent <= 70) return "Medium (" + p + "%)";
        if (percent <= 90) return "High (" + p + "%)";
        return "Critical (" + p + "%)";
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
