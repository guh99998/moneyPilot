package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BillRecurrenceRequest(
        @Schema(description = "Conta onde as quitações vão cair; opcional", example = "1") Long accountId,
        @Schema(description = "Identificador da categoria", example = "3") @NotNull Long categoryId,
        @Schema(description = "Descrição livre", example = "Compras da semana") String description,
        @Schema(description = "Valor em reais, número com 2 casas decimais e ponto decimal.", example = "50.00") @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @Schema(description = "A pagar ou a receber", enumAsRef = true) @NotNull BillType type,
        @Schema(description = "Dia do mês do vencimento, de 1 a 31", example = "10") @NotNull @Min(1) @Max(31) Integer dayOfMonth,
        @Schema(description = "Início, no formato aaaa-mm-dd", example = "2026-11-01") @NotNull LocalDate startDate,
        @Schema(description = "Fim, no formato aaaa-mm-dd; vazio se não termina", example = "2027-10-31") LocalDate endDate
        ) {

}
