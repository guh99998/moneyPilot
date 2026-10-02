package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

import br.com.desenvolvedorgustavolopes.moneyPilot.bill.Bill;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.BillType;
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
        String categoryName,
        Long billId,
        BillType billType
) {
        public TransactionResponse(Transaction transaction, String accountName, String categoryName) {
                this(transaction, accountName, categoryName, null);
        }

        /** bill: o título cuja baixa gerou este lançamento, quando houver. */
        public TransactionResponse(Transaction transaction, String accountName, String categoryName, Bill bill) {
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
                        categoryName,
                        bill != null ? bill.getId() : null,
                        bill != null ? bill.getType() : null
                );
        }
}
