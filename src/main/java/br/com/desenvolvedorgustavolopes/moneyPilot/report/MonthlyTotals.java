package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import java.math.BigDecimal;

public interface MonthlyTotals {
    BigDecimal getTotalIncome();
    BigDecimal getTotalExpense();
}
