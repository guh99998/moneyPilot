package br.com.desenvolvedorgustavolopes.moneyPilot.budget;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record BudgetRequest(
        @NotNull Long categoryId,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amountLimit,
        @NotNull @Min(1) @Max(12) Integer month,
        @NotNull Integer year
) {
}
