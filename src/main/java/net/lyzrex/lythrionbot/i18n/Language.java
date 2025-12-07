package net.lyzrex.lythrionbot.i18n;

import java.util.Locale;
import java.util.Optional;

public enum Language {
    EN(1, "english", Locale.ENGLISH),
    DE(2, "deutsch", Locale.GERMAN);

    private final int id;
    private final String commandAlias;
    private final Locale locale;

    Language(int id, String commandAlias, Locale locale) {
        this.id = id;
        this.commandAlias = commandAlias;
        this.locale = locale;
    }

    public int getId() {
        return id;
    }

    public String getCommandAlias() {
        return commandAlias;
    }

    public Locale getLocale() {
        return locale;
    }

    public static Language fromId(int id) {
        for (Language l : values()) {
            if (l.id == id) return l;
        }
        return EN;
    }

    /**
     * Ruft die Language-Enum anhand des Slash-Command-Arguments (z.B. "english" oder "deutsch") ab.
     */
    public static Optional<Language> fromString(String alias) {
        if (alias == null) return Optional.empty();
        for (Language l : values()) {
            if (l.commandAlias.equalsIgnoreCase(alias)) {
                return Optional.of(l);
            }
        }
        return Optional.empty();
    }
}