package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BillRecurrenceResponse(
        Long id,
        Long userId,
        Long accountId,
        Long categoryId,
        String description,
        BigDecimal amount,
        BillType type,
        Integer dayOfMonth,
        LocalDate startDate,
        LocalDate endDate,
        Boolean active,
        Instant createdAt,
        Instant updatedAt,
        String accountName,
        String categoryName
) {
    public BillRecurrenceResponse(BillRecurrence billRecurrence, String accountName, String categoryName) {
        this(
                billRecurrence.getId(),
                billRecurrence.getUserId(),
                billRecurrence.getAccountId(),
                billRecurrence.getCategoryId(),
                billRecurrence.getDescription(),
                billRecurrence.getAmount(),
                billRecurrence.getType(),
                billRecurrence.getDayOfMonth(),
                billRecurrence.getStartDate(),
                billRecurrence.getEndDate(),
                billRecurrence.getActive(),
                billRecurrence.getCreatedAt(),
                billRecurrence.getUpdatedAt(),
                accountName,
                categoryName
        );
    }
}
