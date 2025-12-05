package net.lyzrex.lythrionbot.murmel;

import net.lyzrex.lythrionbot.ConfigManager;
import net.lyzrex.lythrionbot.Env;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class MurmelApiClient {

    private final HttpClient httpClient;
    private final String healthUrl;

    public MurmelApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        String baseUrl = firstNonBlank(
                Env.get("MURMELAPI_BASE_URL"),
                ConfigManager.getString(
                        "murmelapi.base_url",
                        "https://murmelmeister.github.io/MurmelAPI"
                )
        );
        String healthPath = firstNonBlank(
                Env.get("MURMELAPI_HEALTH_PATH"),
                ConfigManager.getString(
                        "murmelapi.health_path",
                        "/health"
                )
        );

        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (!healthPath.startsWith("/")) {
            healthPath = "/" + healthPath;
        }

        this.healthUrl = baseUrl + healthPath;
    }

    public String getHealthUrl() {
        return healthUrl;
    }

    public MurmelApiStatus checkHealth() {
        return checkHealthAsync().orTimeout(7, TimeUnit.SECONDS).join();
    }

    public CompletableFuture<MurmelApiStatus> checkHealthAsync() {
        long start = System.currentTimeMillis();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(healthUrl))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    long latency = System.currentTimeMillis() - start;

                    boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
                    String message = safeTrimBody(response.body());

                    return new MurmelApiStatus(
                            ok,
                            latency,
                            response.statusCode(),
                            ok ? (message == null || message.isBlank() ? "OK" : message)
                                    : "Unexpected status code: " + response.statusCode() +
                                    (message != null && !message.isBlank() ? " • " + message : "")
                    );
                })
                .exceptionally(ex -> new MurmelApiStatus(
                        false,
                        -1L,
                        0,
                        "Request failed: " + ex.getClass().getSimpleName()
                ));
    }

    private String safeTrimBody(String body) {
        if (body == null) return null;
        String trimmed = body.trim();
        if (trimmed.length() > 200) {
            return trimmed.substring(0, 200) + "...";
        }
        return trimmed;
    }

    private String firstNonBlank(String a, String fallback) {
        return (a != null && !a.isBlank()) ? a : fallback;
    }
}