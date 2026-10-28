package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record BudgetVsActualResponse(
        @Schema(description = "Identificador da categoria", example = "3") Long categoryId,
        @Schema(description = "Nome da categoria", example = "Mercado") String categoryName,
        @Schema(description = "Valor orçado. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "800.00") BigDecimal budgetedAmount,
        @Schema(description = "Valor gasto. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "640.00") BigDecimal spentAmount,
        @Schema(description = "Quanto ainda cabe no orçamento (negativo se estourou). Valor em reais, número com 2 casas decimais e ponto decimal.", example = "160.00") BigDecimal remainingAmount
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
