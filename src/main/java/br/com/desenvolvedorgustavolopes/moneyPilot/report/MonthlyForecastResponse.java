package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MonthlyForecastResponse(
        @Schema(description = "Saldo atual. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "1450.00") BigDecimal currentBalance,
        @Schema(description = "Início do período, aaaa-mm-dd", example = "2026-10-28") LocalDate from,
        @Schema(description = "Fim do período, aaaa-mm-dd", example = "2026-11-27") LocalDate to,
        @Schema(description = "Contas vencidas e ainda em aberto") OverdueSummaryResponse overdue,
        @Schema(description = "Meses da previsão") List<MonthlyForecastBucketResponse> months
) {
}
