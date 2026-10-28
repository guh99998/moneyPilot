package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
        @Schema(description = "Identificador da conta", example = "1") @NotNull Long accountId,
        @Schema(description = "Identificador da categoria", example = "3") @NotNull Long categoryId,
        @Schema(description = "Valor em reais, número com 2 casas decimais e ponto decimal.", example = "50.00") @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @Schema(description = "Receita ou despesa", enumAsRef = true) @NotNull TransactionType type,
        @Schema(description = "Descrição livre", example = "Compras da semana") String description,
        @Schema(description = "Data, no formato aaaa-mm-dd", example = "2026-10-28") @NotNull LocalDate date
        ) {
}
