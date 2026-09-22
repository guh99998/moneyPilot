package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransferRequest(
        @NotNull Long fromAccountId,
        @NotNull Long toAccountId,
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate date,
        String description
        ) {
}
