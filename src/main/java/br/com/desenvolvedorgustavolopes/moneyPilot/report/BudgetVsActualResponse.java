package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import java.math.BigDecimal;

public record BudgetVsActualResponse(
        Long categoryId,
        String categoryName,
        BigDecimal budgetedAmount,
        BigDecimal spentAmount,
        BigDecimal remainingAmount
) {
    public BudgetVsActualResponse(BudgetVsActual budgetVsActual) {
        this(
                budgetVsActual.getCategoryId(),
                budgetVsActual.getCategoryName(),
                budgetVsActual.getBudgetedAmount(),
                budgetVsActual.getSpentAmount(),
                budgetVsActual.getBudgetedAmount().subtract(budgetVsActual.getSpentAmount())
        );
    }
}
