package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import java.math.BigDecimal;

public record SettledSummaryResponse(
        Long count,
        BigDecimal payable,
        BigDecimal receivable
) {
}
