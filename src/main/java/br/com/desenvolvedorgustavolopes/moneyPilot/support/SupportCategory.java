package br.com.desenvolvedorgustavolopes.moneyPilot.support;

public enum SupportCategory {
    DUVIDA("Dúvida"),
    PROBLEMA("Problema"),
    SUGESTAO("Sugestão"),
    OUTRO("Outro");

    private final String label;

    SupportCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
