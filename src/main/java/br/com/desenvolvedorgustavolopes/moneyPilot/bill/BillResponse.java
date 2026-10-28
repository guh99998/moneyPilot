package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BillResponse(
        @Schema(description = "Identificador", example = "1") Long id,
        @Schema(description = "Identificador da pessoa dona do registro", example = "1") Long userId,
        @Schema(description = "Identificador da conta", example = "1") Long accountId,
        @Schema(description = "Identificador da categoria", example = "3") Long categoryId,
        @Schema(description = "Descrição livre", example = "Compras da semana") String description,
        @Schema(description = "Valor em reais, número com 2 casas decimais e ponto decimal.", example = "50.00") BigDecimal amount,
        @Schema(description = "Vencimento, no formato aaaa-mm-dd", example = "2026-11-10") LocalDate dueDate,
        @Schema(description = "Se é a pagar ou a receber", enumAsRef = true) BillType type,
        @Schema(description = "Situação da conta", enumAsRef = true) BillStatus status,
        @Schema(description = "Quitada em (UTC), se quitada", example = "2026-10-28T12:00:00Z") Instant settledAt,
        @Schema(description = "Valor pago na quitação, se quitada. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "50.00") BigDecimal settledAmount,
        @Schema(description = "Lançamento gerado pela quitação, se houver", example = "10") Long transactionId,
        @Schema(description = "Grupo do parcelamento, se for parcelada", example = "3f2b8c1e-5d4a-4e7b-9a11-2c6d8e0f1a23") UUID installmentGroupId,
        @Schema(description = "Número desta parcela, se for parcelada", example = "3") Integer installmentNumber,
        @Schema(description = "Total de parcelas, se for parcelada", example = "12") Integer installmentTotal,
        @Schema(description = "Recorrência que gerou a conta, se houver", example = "4") Long recurrenceId,
        @Schema(description = "Criado em (UTC)", example = "2026-10-28T12:00:00Z") Instant createdAt,
        @Schema(description = "Atualizado em (UTC)", example = "2026-10-28T12:00:00Z") Instant updatedAt,
        @Schema(description = "Nome da conta", example = "Carteira") String accountName,
        @Schema(description = "Nome da categoria", example = "Mercado") String categoryName
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
