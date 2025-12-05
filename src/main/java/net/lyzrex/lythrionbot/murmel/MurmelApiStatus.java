package net.lyzrex.lythrionbot.murmel;

public record MurmelApiStatus(boolean online, long latencyMs, int statusCode, String message) {
    public String describe() {
        String base = online ? "🟢 Online" : "🔴 Offline";
        String latency = latencyMs >= 0 ? latencyMs + "ms" : "N/A";
        return base + " • Latency: " + latency + " • Status: " + statusCode +
                (message != null && !message.isBlank() ? "\n" + message : "");
    }

    public String describeBrief() {
        String latency = latencyMs >= 0 ? latencyMs + "ms" : "N/A";
        String status = statusCode > 0 ? "HTTP " + statusCode : "no response";
        String suffix = !online && message != null && !message.isBlank()
                ? " • " + message
                : "";
        return (online ? "🟢" : "🔴") + " " + latency + " (" + status + ")" + suffix;
    }
}