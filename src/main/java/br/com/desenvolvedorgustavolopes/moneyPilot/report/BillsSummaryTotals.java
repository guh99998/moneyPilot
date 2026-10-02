package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import java.math.BigDecimal;

public interface BillsSummaryTotals {
    Long getPayableCount();
    BigDecimal getPayableTotal();
    Long getPayableOverdueCount();
    BigDecimal getPayableOverdueTotal();
    Long getReceivableCount();
    BigDecimal getReceivableTotal();
    Long getReceivableOverdueCount();
    BigDecimal getReceivableOverdueTotal();
    Long getSettledCount();
    BigDecimal getSettledPayable();
    BigDecimal getSettledReceivable();
}
