package br.com.desenvolvedorgustavolopes.moneyPilot.account;

/** Situação da fatura do cartão (spec 016, regra 7). */
public enum InvoiceStatus {
    /** Ciclo atual, ainda recebendo compras. */
    OPEN,
    /** Fechada, a pagar. */
    CLOSED,
    /** Venceu sem ser quitada. */
    OVERDUE,
    /** Fechada e quitada. */
    PAID,
    /** Ciclo que ainda não começou (só parcelas futuras). */
    FUTURE
}
