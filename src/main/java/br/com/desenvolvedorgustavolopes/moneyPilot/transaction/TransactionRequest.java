package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
        @NotNull Long accountId,
        @NotNull Long categoryId,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotNull TransactionType type,
        String description,
        @NotNull LocalDate date
        ) {
}
