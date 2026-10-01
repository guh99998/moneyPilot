package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ForecastBucketResponse(
        LocalDate start,
        LocalDate end,
        Integer days,
        BigDecimal payable,
        BigDecimal receivable,
        BigDecimal net,
        BigDecimal projectedBalance
) {
}
