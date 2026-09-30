package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InstallmentPlanRequest(
        Long accountId,
        @NotNull Long categoryId,
        String description,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal totalAmount,
        @NotNull @Min(2) @Max(360) Integer installments,
        @NotNull LocalDate firstDueDate,
        @NotNull BillType type
        ) {
}
