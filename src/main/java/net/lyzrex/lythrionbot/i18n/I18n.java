package net.lyzrex.lythrionbot.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

public final class I18n {

    private I18n() {}

    private static ResourceBundle getBundle(Language lang) {
        Locale locale = lang.getLocale();
        return ResourceBundle.getBundle("lang.message", locale);
    }

    public static String tr(Language lang, String key, Object... args) {
        try {
            ResourceBundle bundle = getBundle(lang);
            String pattern = bundle.getString(key);
            if (args.length == 0) return pattern;
            return MessageFormat.format(pattern, args);
        } catch (Exception e) {
            // Fallback
            if (args.length == 0) return key;
            return key + " " + java.util.Arrays.toString(args);
        }
    }
}
