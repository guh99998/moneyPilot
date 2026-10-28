package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record OverdueSummaryResponse(
        @Schema(description = "Quantidade de contas vencidas", example = "2") Long count,
        @Schema(description = "A pagar. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "400.00") BigDecimal payable,
        @Schema(description = "A receber. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "900.00") BigDecimal receivable
) {
}
