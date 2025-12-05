package net.lyzrex.lythrionbot;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;


public final class ConfigManager {

    private static Map<String, Object> root;

    private ConfigManager() {
    }

    public static void load() throws Exception {
        File file = new File("config.yml");
        if (!file.exists()) {
            writeDefaultConfig(file);
        }

        try (FileInputStream in = new FileInputStream(file)) {
            Yaml yaml = new Yaml();
            root = yaml.load(in);
        }
    }

    private static void writeDefaultConfig(File file) throws Exception {
        String yaml = """
                bot:
                  version: "0.0.2-beta1.13"

                network:
                  name: "Lythrion Network"
                  ip: "Lythrion.net"
                  icon: "https://api.mcstatus.io/v2/icon/lythrion.net"
                  discord: "https://discord.gg/yourInvite"
                  website: "https://example.com"
                  store: "https://store.example.com"
                  docs: "https://docs.example.com"

                maintenance:
                  main: false
                  lobby: false
                  citybuild: false


                murmelapi:
                  base_url: "https://murmelmeister.github.io/MurmelAPI"
                  health_path: "/health"

                tickets:
                  categoryId: "0"
                  staffRoleId: "0"
                  logChannelId: "0"

                moderation:
                  logChannelId: "0"

                games:
                  enableRps: true
                """;

        try (FileWriter fw = new FileWriter(file, StandardCharsets.UTF_8)) {
            fw.write(yaml);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object getPath(String path) {
        if (root == null) return null;
        String[] parts = path.split("\\.");
        Object current = root;
        for (String p : parts) {
            if (!(current instanceof Map<?, ?> m)) return null;
            current = m.get(p);
            if (current == null) return null;
        }
        return current;
    }

    public static String getString(String path, String def) {
        Object o = getPath(path);
        return (o instanceof String s) ? s : def;
    }

    public static boolean getBoolean(String path, boolean def) {
        Object o = getPath(path);
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) return Boolean.parseBoolean(s);
        return def;
    }

    public static int getInt(String path, int def) {
        Object o = getPath(path);
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public static long getLong(String path, long def) {
        Object o = getPath(path);
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }
}
