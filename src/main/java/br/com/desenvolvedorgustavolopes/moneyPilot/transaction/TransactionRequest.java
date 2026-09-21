package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionRequest(
        @NotNull Long accountId,
        Long categoryId,
        @NotNull BigDecimal amount,
        @NotNull TransactionType type,
        @NotBlank String description,
        @NotNull LocalDate date,
        UUID transferGroupId
        ) {
}
