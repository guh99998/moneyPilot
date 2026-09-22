package br.com.desenvolvedorgustavolopes.moneyPilot.budget;

import java.math.BigDecimal;

public record BudgetResponse(
        Long id,
        Long userId,
        Long categoryId,
        BigDecimal amountLimit,
        Integer month,
        Integer year,
        String categoryName
) {
    public BudgetResponse(Budget budget, String categoryName) {
        this(
                budget.getId(),
                budget.getUserId(),
                budget.getCategoryId(),
                budget.getAmountLimit(),
                budget.getMonth(),
                budget.getYear(),
                categoryName
        );
    }
}
