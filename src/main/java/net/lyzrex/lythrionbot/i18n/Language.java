package net.lyzrex.lythrionbot.i18n;

import java.util.Locale;

public enum Language {
    EN(1, Locale.ENGLISH),
    DE(2, Locale.GERMAN);

    private final int id;
    private final Locale locale;

    Language(int id, Locale locale) {
        this.id = id;
        this.locale = locale;
    }

    public int getId() {
        return id;
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
}
