package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MonthlyForecastBucketResponse(
        Integer month,
        Integer year,
        LocalDate start,
        LocalDate end,
        Long billCount,
        BigDecimal payable,
        BigDecimal receivable,
        BigDecimal net,
        BigDecimal projectedBalance
) {
}
