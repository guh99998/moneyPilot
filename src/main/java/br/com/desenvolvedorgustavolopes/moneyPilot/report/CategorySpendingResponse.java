package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record CategorySpendingResponse(
        @Schema(description = "Identificador da categoria", example = "3") Long categoryId,
        @Schema(description = "Nome da categoria", example = "Mercado") String categoryName,
        @Schema(description = "Total gasto na categoria no mês. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "640.00") BigDecimal total
) {
    public CategorySpendingResponse(CategorySpending spending) {
        this(
                spending.getCategoryId(),
                spending.getCategoryName(),
                spending.getTotal()
        );
    }
}
