package br.com.desenvolvedorgustavolopes.moneyPilot.account;

public enum AccountType {
    CHECKING,
    SAVING,
    CREDIT_CARD,
    CASH,
    INVESTMENT;

    /** Padrão da opção "contar no saldo disponível" (spec 015): dinheiro investido começa fora do disponível. */
    public boolean countsAsAvailableByDefault() {
        return this != INVESTMENT;
    }
}
