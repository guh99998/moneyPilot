package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import java.math.BigDecimal;

public record CategorySpendingResponse(
        Long categoryId,
        String categoryName,
        BigDecimal total
) {
    public CategorySpendingResponse(CategorySpending spending) {
        this(
                spending.getCategoryId(),
                spending.getCategoryName(),
                spending.getTotal()
        );
    }
}
