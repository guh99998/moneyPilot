package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import java.math.BigDecimal;

public interface BudgetVsActual {
    Long getCategoryId();
    String getCategoryName();
    BigDecimal getBudgetedAmount();
    BigDecimal getSpentAmount();
}
