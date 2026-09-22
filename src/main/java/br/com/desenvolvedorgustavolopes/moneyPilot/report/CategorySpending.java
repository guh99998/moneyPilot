package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import java.math.BigDecimal;

public interface CategorySpending {
    Long getCategoryId();
    String getCategoryName();
    BigDecimal getTotal();
}
