package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        Long id,
        Long accountId,
        Long categoryId,
        BigDecimal amount,
        TransactionType type,
        String description,
        LocalDate date,
        UUID transferGroupId,
        Instant createdAt,
        Instant updatedAt,
        String accountName,
        String categoryName
) {
        public TransactionResponse(Transaction transaction, String accountName, String categoryName) {
                this(
                        transaction.getId(),
                        transaction.getAccountId(),
                        transaction.getCategoryId(),
                        transaction.getAmount(),
                        transaction.getType(),
                        transaction.getDescription(),
                        transaction.getDate(),
                        transaction.getTransferGroupId(),
                        transaction.getCreatedAt(),
                        transaction.getUpdatedAt(),
                        accountName,
                        categoryName
                );
        }
}
