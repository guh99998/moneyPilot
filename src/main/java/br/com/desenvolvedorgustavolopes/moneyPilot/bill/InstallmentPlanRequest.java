package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InstallmentPlanRequest(
        @Schema(description = "Conta onde as quitações vão cair; opcional", example = "1") Long accountId,
        @Schema(description = "Identificador da categoria", example = "3") @NotNull Long categoryId,
        @Schema(description = "Descrição livre", example = "Compras da semana") String description,
        @Schema(description = "Valor total a parcelar. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "1200.00") @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal totalAmount,
        @Schema(description = "Número de parcelas", example = "12") @NotNull @Min(2) @Max(360) Integer installments,
        @Schema(description = "Vencimento da primeira parcela, aaaa-mm-dd", example = "2026-11-10") @NotNull LocalDate firstDueDate,
        @Schema(description = "A pagar ou a receber", enumAsRef = true) @NotNull BillType type
        ) {
}
