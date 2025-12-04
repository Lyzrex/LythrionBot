package net.lyzrex.lythrionbot.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class Messages {

    private static final Properties PROPS = new Properties();

    private Messages() {
    }

    public static void load() {
        try (InputStream in = Messages.class.getClassLoader()
                .getResourceAsStream("messages.properties")) {
            if (in != null) {
                PROPS.load(in);
                System.out.println("Loaded messages.properties");
            } else {
                System.out.println("No messages.properties found, using defaults.");
            }
        } catch (IOException e) {
            System.err.println("Failed to load messages.properties: " + e.getMessage());
        }
    }

    public static String get(String key, String def) {
        return PROPS.getProperty(key, def);
    }

    public static String get(String key) {
        return get(key, key);
    }
}
