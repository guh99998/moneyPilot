package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MonthlyForecastResponse(
        BigDecimal currentBalance,
        LocalDate from,
        LocalDate to,
        OverdueSummaryResponse overdue,
        List<MonthlyForecastBucketResponse> months
) {
}
