package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Uma fatura de cartão a pagar, no formato dos títulos em aberto, para entrar nas previsões (spec 016). */
public record InvoiceDueTotals(LocalDate dueDate, BigDecimal amount) implements BillDueTotals {

    @Override
    public LocalDate getDueDate() {
        return dueDate;
    }

    @Override
    public Long getBillCount() {
        return 1L;
    }

    @Override
    public BigDecimal getTotalPayable() {
        return amount;
    }

    @Override
    public BigDecimal getTotalReceivable() {
        return BigDecimal.ZERO;
    }
}
