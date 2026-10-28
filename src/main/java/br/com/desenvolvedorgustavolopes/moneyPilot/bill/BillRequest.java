package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BillRequest(
        Long accountId,
        @NotNull Long categoryId,
        String description,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotNull LocalDate dueDate,
        @NotNull BillType type
        ) {
}
