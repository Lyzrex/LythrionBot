package net.lyzrex.lythrionbot.status;

import java.util.EnumMap;
import java.util.Map;

public class MaintenanceManager {

    private final Map<ServiceType, Boolean> maintenance = new EnumMap<>(ServiceType.class);

    public MaintenanceManager(boolean main, boolean lobby, boolean citybuild) {
        maintenance.put(ServiceType.MAIN, main);
        maintenance.put(ServiceType.LOBBY, lobby);
        maintenance.put(ServiceType.CITYBUILD, citybuild);
    }

    public boolean isMaintenance(ServiceType type) {
        return maintenance.getOrDefault(type, false);
    }

    public void setMaintenance(ServiceType type, boolean value) {
        maintenance.put(type, value);
    }
}
