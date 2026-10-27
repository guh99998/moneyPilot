package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface BillAlertTotals {
    BillType getType();
    Long getBillCount();
    BigDecimal getTotal();
    LocalDate getLastDueDate();
}
