package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record BillTypeSummaryResponse(
        @Schema(description = "Quantidade de contas em aberto", example = "3") Long count,
        @Schema(description = "Total em aberto. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "320.00") BigDecimal total,
        @Schema(description = "Quantidade vencida", example = "1") Long overdueCount,
        @Schema(description = "Total vencido. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "80.00") BigDecimal overdueTotal
) {
}
