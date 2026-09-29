package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BillRequest(
        Long accountId,
        @NotNull Long categoryId,
        String description,
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate dueDate,
        @NotNull BillType type
        ) {
}
