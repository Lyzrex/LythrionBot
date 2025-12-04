package net.lyzrex.lythrionbot.status;

import net.lyzrex.lythrionbot.ServiceType;
import net.lyzrex.lythrionbot.ConfigManager;

import java.util.EnumMap;
import java.util.Map;

/**
 * Zentraler Maintenance-Manager für alle Services.
 * Konfiguration kommt aus config.yml:
 *
 * maintenance:
 *   main: false
 *   lobby: false
 *   citybuild: false
 */
public final class MaintenanceManager {

    private final Map<ServiceType, Boolean> flags = new EnumMap<>(ServiceType.class);

    public MaintenanceManager() {
        // Defaults aus config.yml laden
        flags.put(ServiceType.MAIN,      ConfigManager.getBoolean("maintenance.main", false));
        flags.put(ServiceType.LOBBY,     ConfigManager.getBoolean("maintenance.lobby", false));
        flags.put(ServiceType.CITYBUILD, ConfigManager.getBoolean("maintenance.citybuild", false));
    }

    /* --------- zentrale API --------- */

    public boolean isMaintenance(ServiceType type) {
        return flags.getOrDefault(type, false);
    }

    public void setMaintenance(ServiceType type, boolean enabled) {
        flags.put(type, enabled);
        // Optional: zurück in config.yml schreiben, wenn du persistieren willst
        // z.B. ConfigManager.setBoolean("maintenance." + key, enabled);
    }

    /* --------- Convenience-Methoden --------- */

    public boolean isMain() {
        return isMaintenance(ServiceType.MAIN);
    }

    public boolean isLobby() {
        return isMaintenance(ServiceType.LOBBY);
    }

    public boolean isCitybuild() {
        return isMaintenance(ServiceType.CITYBUILD);
    }

    public void setMain(boolean enabled) {
        setMaintenance(ServiceType.MAIN, enabled);
    }

    public void setLobby(boolean enabled) {
        setMaintenance(ServiceType.LOBBY, enabled);
    }

    public void setCitybuild(boolean enabled) {
        setMaintenance(ServiceType.CITYBUILD, enabled);
    }
}
