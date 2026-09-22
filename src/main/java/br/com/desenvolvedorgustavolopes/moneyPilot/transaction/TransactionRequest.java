package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
        @NotNull Long accountId,
        @NotNull Long categoryId,
        @NotNull @Positive BigDecimal amount,
        @NotNull TransactionType type,
        String description,
        @NotNull LocalDate date
        ) {
}
