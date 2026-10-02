package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import java.math.BigDecimal;

public record BillTypeSummaryResponse(
        Long count,
        BigDecimal total,
        Long overdueCount,
        BigDecimal overdueTotal
) {
}
