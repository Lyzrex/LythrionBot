package net.lyzrex.lythrionbot.language;

import de.murmelmeister.murmelapi.user.User;
import de.murmelmeister.murmelapi.user.UserProvider;
import net.lyzrex.lythrionbot.i18n.Language;

public class LanguageService {

    private final UserProvider userProvider;

    // Dieser Konstruktor hat gefehlt oder war falsch
    public LanguageService(UserProvider userProvider) {
        this.userProvider = userProvider;
    }

    /**
     * Setzt die Sprache für einen Benutzer in der MurmelAPI.
     */
    public boolean setLanguage(int userId, Language language) {
        if (userId <= 0 || language == null) {
            return false;
        }

        User user = userProvider.findById(userId);
        if (user == null) {
            return false;
        }

        // Aktualisiert die languageId des MurmelAPI Users (1=EN, 2=DE)
        User updatedUser = userProvider.update(
                user.id(),
                user.username(),
                user.firstLogin(),
                user.debugUser(),
                user.debugEnabled(),
                language.getId()
        );

        return updatedUser != null;
    }
}