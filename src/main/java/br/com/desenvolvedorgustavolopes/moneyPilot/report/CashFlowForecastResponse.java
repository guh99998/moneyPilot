package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CashFlowForecastResponse(
        BigDecimal currentBalance,
        LocalDate from,
        LocalDate to,
        OverdueSummaryResponse overdue,
        List<ForecastBucketResponse> buckets
) {
}
