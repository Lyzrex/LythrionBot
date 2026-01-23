package net.lyzrex.lythrionbot.language;

import de.murmelmeister.murmelapi.user.User;
import de.murmelmeister.murmelapi.user.UserProvider;
import net.lyzrex.lythrionbot.i18n.Language;

/**
 * Service zur Verwaltung der Sprachpräferenz eines Benutzers, gespeichert in der MurmelAPI.
 */
public class LanguageService {

    private UserProvider userProvider = null;

    public LanguageService() {
        this.userProvider = userProvider;
    }

    /**
     * Setzt die Sprache für einen Benutzer in der MurmelAPI.
     * @param userId MurmelAPI User ID (int)
     * @param language Die zu setzende Sprache
     * @return True, wenn die Sprache erfolgreich aktualisiert wurde.
     */
    public boolean setLanguage(int userId, Language language) {
        if (userId <= 0 || language == null) {
            return false;
        }

        User user = userProvider.findById(userId);
        if (user == null) {
            return false;
        }

        // Aktualisiert die languageId des MurmelAPI Users
        User updatedUser = userProvider.update(
                user.id(),
                user.username(),
                user.firstLogin(),
                user.debugUser(),
                user.debugEnabled(),
                language.getId() // Setzt die korrekte ID (1 für EN, 2 für DE)
        );

        return updatedUser != null;
    }
}