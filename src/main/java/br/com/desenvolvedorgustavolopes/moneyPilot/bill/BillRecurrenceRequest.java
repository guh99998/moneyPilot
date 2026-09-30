package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BillRecurrenceRequest(
        Long accountId,
        @NotNull Long categoryId,
        String description,
        @NotNull @Positive BigDecimal amount,
        @NotNull BillType type,
        @NotNull @Min(1) @Max(31) Integer dayOfMonth,
        @NotNull LocalDate startDate,
        LocalDate endDate
        ) {

}
