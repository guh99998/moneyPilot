package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import java.math.BigDecimal;

public record OverdueSummaryResponse(
        Long count,
        BigDecimal payable,
        BigDecimal receivable
) {
}
