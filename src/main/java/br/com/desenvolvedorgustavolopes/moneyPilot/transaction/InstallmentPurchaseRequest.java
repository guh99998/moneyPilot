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
        @Schema(description = "Valor total da compra, em reais, com 2 casas decimais e ponto decimal. Informe este ou installmentAmount, nunca os dois. O centavo que sobra da divisão fica na primeira parcela.", example = "1200.00", nullable = true) @Positive @Digits(integer = 17, fraction = 2) BigDecimal totalAmount,
        @Schema(description = "Valor de cada parcela, em reais, com 2 casas decimais e ponto decimal. Informe este ou totalAmount, nunca os dois. As parcelas saem iguais, sem centavo sobrando.", example = "83.33", nullable = true) @Positive @Digits(integer = 17, fraction = 2) BigDecimal installmentAmount,
        @Schema(description = "Número de parcelas da compra, de 2 a 60", example = "6") @NotNull @Min(2) @Max(60) Integer installments,
        @Schema(description = "Parcela em que a compra está, de 1 ao número de parcelas; padrão 1. Só vale com installmentAmount: lança apenas da parcela atual até a última, numeradas como na compra original.", example = "4", nullable = true) @Min(1) Integer currentInstallment,
        @Schema(description = "Data da primeira parcela lançada, aaaa-mm-dd (da compra, ou da parcela atual quando currentInstallment é informada). As demais caem no mesmo dia dos meses seguintes (no último dia, se o mês for mais curto).", example = "2026-11-06") @NotNull LocalDate date
) {
}
