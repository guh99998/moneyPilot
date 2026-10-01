package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface BillDueTotals {
    LocalDate getDueDate();
    Long getBillCount();
    BigDecimal getTotalPayable();
    BigDecimal getTotalReceivable();
}
