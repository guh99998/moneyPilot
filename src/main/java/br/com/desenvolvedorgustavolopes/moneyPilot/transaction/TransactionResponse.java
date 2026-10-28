package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.Bill;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.BillType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        @Schema(description = "Identificador", example = "1") Long id,
        @Schema(description = "Identificador da conta", example = "1") Long accountId,
        @Schema(description = "Identificador da categoria", example = "3") Long categoryId,
        @Schema(description = "Valor em reais, número com 2 casas decimais e ponto decimal.", example = "50.00") BigDecimal amount,
        @Schema(description = "Receita ou despesa", enumAsRef = true) TransactionType type,
        @Schema(description = "Descrição livre", example = "Compras da semana") String description,
        @Schema(description = "Data, no formato aaaa-mm-dd", example = "2026-10-28") LocalDate date,
        @Schema(description = "Grupo da transferência, se o lançamento for parte de uma", example = "3f2b8c1e-5d4a-4e7b-9a11-2c6d8e0f1a23") UUID transferGroupId,
        @Schema(description = "Criado em (UTC)", example = "2026-10-28T12:00:00Z") Instant createdAt,
        @Schema(description = "Atualizado em (UTC)", example = "2026-10-28T12:00:00Z") Instant updatedAt,
        @Schema(description = "Nome da conta", example = "Carteira") String accountName,
        @Schema(description = "Nome da categoria", example = "Mercado") String categoryName,
        @Schema(description = "Conta a pagar ou receber ligada, se houver", example = "7") Long billId,
        @Schema(description = "Tipo da conta ligada, se houver", enumAsRef = true) BillType billType
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
