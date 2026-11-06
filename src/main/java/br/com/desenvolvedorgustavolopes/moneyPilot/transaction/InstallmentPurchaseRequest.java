package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InstallmentPurchaseRequest(
        @Schema(description = "Identificador do cartão de crédito", example = "1") @NotNull Long accountId,
        @Schema(description = "Identificador da categoria (de despesa)", example = "3") @NotNull Long categoryId,
        @Schema(description = "Descrição livre", example = "Notebook") String description,
        @Schema(description = "Valor total da compra. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "1200.00") @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal totalAmount,
        @Schema(description = "Número de parcelas, de 2 a 60", example = "6") @NotNull @Min(2) @Max(60) Integer installments,
        @Schema(description = "Data da compra e da primeira parcela, aaaa-mm-dd. As demais caem no mesmo dia dos meses seguintes (no último dia, se o mês for mais curto).", example = "2026-11-06") @NotNull LocalDate date
) {
}
