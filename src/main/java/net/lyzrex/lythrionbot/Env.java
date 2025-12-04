package net.lyzrex.lythrionbot;

import io.github.cdimascio.dotenv.Dotenv;

public final class Env {

    private static final Dotenv DOTENV = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    private Env() {
    }

    public static String get(String key) {
        String v = System.getenv(key);
        if (v != null && !v.isBlank()) {
            return v;
        }
        return DOTENV.get(key);
    }
}
