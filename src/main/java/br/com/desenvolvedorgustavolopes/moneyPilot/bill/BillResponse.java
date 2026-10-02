package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BillResponse(
        Long id,
        Long userId,
        Long accountId,
        Long categoryId,
        String description,
        BigDecimal amount,
        LocalDate dueDate,
        BillType type,
        BillStatus status,
        Instant settledAt,
        BigDecimal settledAmount,
        Long transactionId,
        UUID installmentGroupId,
        Integer installmentNumber,
        Integer installmentTotal,
        Long recurrenceId,
        Instant createdAt,
        Instant updatedAt,
        String accountName,
        String categoryName
) {
    public BillResponse(Bill bill, String accountName, String categoryName) {
        this(
                bill.getId(),
                bill.getUserId(),
                bill.getAccountId(),
                bill.getCategoryId(),
                bill.getDescription(),
                bill.getAmount(),
                bill.getDueDate(),
                bill.getType(),
                bill.getStatus(),
                bill.getSettledAt(),
                bill.getSettledAmount(),
                bill.getTransactionId(),
                bill.getInstallmentGroupId(),
                bill.getInstallmentNumber(),
                bill.getInstallmentTotal(),
                bill.getRecurrenceId(),
                bill.getCreatedAt(),
                bill.getUpdatedAt(),
                accountName,
                categoryName
        );
    }
}
