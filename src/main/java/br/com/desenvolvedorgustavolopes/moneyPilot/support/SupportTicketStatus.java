package br.com.desenvolvedorgustavolopes.moneyPilot.support;

public enum SupportTicketStatus {
    OPEN("Aberto"),
    IN_PROGRESS("Em andamento"),
    RESOLVED("Resolvido");

    private final String label;

    SupportTicketStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
