package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BillRequest(
        @Schema(description = "Conta onde a quitação vai cair; opcional", example = "1") Long accountId,
        @Schema(description = "Identificador da categoria", example = "3") @NotNull Long categoryId,
        @Schema(description = "Descrição livre", example = "Compras da semana") String description,
        @Schema(description = "Valor em reais, número com 2 casas decimais e ponto decimal.", example = "50.00") @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @Schema(description = "Vencimento, no formato aaaa-mm-dd", example = "2026-11-10") @NotNull LocalDate dueDate,
        @Schema(description = "A pagar ou a receber", enumAsRef = true) @NotNull BillType type
        ) {
}
