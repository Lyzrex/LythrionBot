package net.lyzrex.lythrionbot.ticket;

public enum TicketCategory {

    SUPPORT("support", "General Support", "Help with anything related to Lythrion."),
    UNBAN("unban", "Unban Request", "Request an unban or appeal a punishment."),
    RANK("rank", "Rank / Purchase", "Issues with ranks, purchases or store."),
    REPORT("report", "Player Report", "Report a player for breaking the rules.");

    private final String id;
    private final String label;
    private final String description;

    TicketCategory(String id, String label, String description) {
        this.id = id;
        this.label = label;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public static TicketCategory fromId(String id) {
        if (id == null) return null;
        for (TicketCategory c : values()) {
            if (c.id.equalsIgnoreCase(id)) {
                return c;
            }
        }
        return null;
    }
}
