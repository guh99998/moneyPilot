package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record RecurringPurchaseResponse(
        @Schema(description = "Identificador", example = "1") Long id,
        @Schema(description = "Identificador do cartão", example = "1") Long accountId,
        @Schema(description = "Nome do cartão", example = "Cartão Roxo") String accountName,
        @Schema(description = "Identificador da categoria", example = "3") Long categoryId,
        @Schema(description = "Nome da categoria", example = "Lazer") String categoryName,
        @Schema(description = "Descrição livre", example = "Streaming de vídeo", nullable = true) String description,
        @Schema(description = "Valor de cada cobrança em reais, número com 2 casas decimais e ponto decimal.", example = "39.90") BigDecimal amount,
        @Schema(description = "Dia do mês da cobrança, de 1 a 31", example = "20") Integer dayOfMonth,
        @Schema(description = "Início, no formato aaaa-mm-dd", example = "2026-11-01") LocalDate startDate,
        @Schema(description = "Fim, no formato aaaa-mm-dd; vazio se não termina", example = "2027-10-31", nullable = true) LocalDate endDate,
        @Schema(description = "Se a compra ainda gera e projeta cobranças", example = "true") Boolean active,
        @Schema(description = "Data da última cobrança gerada; vazio se nenhuma foi gerada ainda", example = "2026-11-20", nullable = true) LocalDate generatedUntil,
        @Schema(description = "Criado em (UTC)", example = "2026-11-09T12:00:00Z") Instant createdAt,
        @Schema(description = "Atualizado em (UTC)", example = "2026-11-09T12:00:00Z") Instant updatedAt
) {
    public RecurringPurchaseResponse(RecurringPurchase purchase, String accountName, String categoryName) {
        this(purchase.getId(), purchase.getAccountId(), accountName, purchase.getCategoryId(), categoryName,
                purchase.getDescription(), purchase.getAmount(), purchase.getDayOfMonth(), purchase.getStartDate(),
                purchase.getEndDate(), purchase.getActive(), purchase.getGeneratedUntil(),
                purchase.getCreatedAt(), purchase.getUpdatedAt());
    }
}
