package br.com.desenvolvedorgustavolopes.moneyPilot.budget;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record BudgetResponse(
        @Schema(description = "Identificador", example = "1") Long id,
        @Schema(description = "Identificador da pessoa dona do registro", example = "1") Long userId,
        @Schema(description = "Identificador da categoria", example = "3") Long categoryId,
        @Schema(description = "Limite do mês. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "800.00") BigDecimal amountLimit,
        @Schema(description = "Mês, de 1 a 12", example = "10") Integer month,
        @Schema(description = "Ano", example = "2026") Integer year,
        @Schema(description = "Nome da categoria", example = "Mercado") String categoryName
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
