package br.com.desenvolvedorgustavolopes.moneyPilot.budget;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record BudgetRequest(
        @Schema(description = "Identificador da categoria", example = "3") @NotNull Long categoryId,
        @Schema(description = "Limite do mês. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "800.00") @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amountLimit,
        @Schema(description = "Mês, de 1 a 12", example = "10") @NotNull @Min(1) @Max(12) Integer month,
        @Schema(description = "Ano", example = "2026") @NotNull Integer year
) {
}
