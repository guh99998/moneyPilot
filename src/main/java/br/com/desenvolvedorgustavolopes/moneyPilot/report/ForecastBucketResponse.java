package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ForecastBucketResponse(
        @Schema(description = "Início do período, aaaa-mm-dd", example = "2026-11-01") LocalDate start,
        @Schema(description = "Fim do período, aaaa-mm-dd", example = "2026-11-30") LocalDate end,
        @Schema(description = "Dias do período", example = "7") Integer days,
        @Schema(description = "A pagar. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "400.00") BigDecimal payable,
        @Schema(description = "A receber. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "900.00") BigDecimal receivable,
        @Schema(description = "A receber menos a pagar. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "500.00") BigDecimal net,
        @Schema(description = "Saldo projetado ao fim do período. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "1950.00") BigDecimal projectedBalance
) {
}
