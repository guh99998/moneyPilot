package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record MonthlySummaryResponse(
        @Schema(description = "Total de receitas do mês. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "5000.00") BigDecimal totalIncome,
        @Schema(description = "Total de despesas do mês. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "3200.00") BigDecimal totalExpense,
        @Schema(description = "Receitas menos despesas. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "1800.00") BigDecimal balance
) {
}
