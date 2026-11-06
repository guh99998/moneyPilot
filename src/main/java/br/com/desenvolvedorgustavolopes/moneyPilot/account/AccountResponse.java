package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AccountResponse(
        @Schema(description = "Identificador", example = "1") Long id,
        @Schema(description = "Identificador da pessoa dona do registro", example = "1") Long userId,
        @Schema(description = "Nome da conta", example = "Carteira") String name,
        @Schema(description = "Tipo da conta", enumAsRef = true) AccountType type,
        @Schema(description = "Saldo inicial. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "1500.00") BigDecimal initialBalance,
        @Schema(description = "Se a conta conta no saldo disponível. O valor das contas fora dele aparece como guardado.", example = "true") boolean countsAsAvailable,
        @Schema(description = "Dia em que a fatura do cartão fecha. Nulo em conta sem fatura.", example = "10", nullable = true) Integer closingDay,
        @Schema(description = "Dia em que a fatura do cartão vence. Nulo em conta sem fatura.", example = "17", nullable = true) Integer dueDay,
        @Schema(description = "Início do ciclo a partir do qual há fatura; lançamentos anteriores não entram em fatura. Nulo em conta sem fatura.", example = "2026-10-10", nullable = true) LocalDate invoicesFrom,
        @Schema(description = "Criado em (UTC)", example = "2026-10-28T12:00:00Z") Instant createdAt,
        @Schema(description = "Atualizado em (UTC)", example = "2026-10-28T12:00:00Z") Instant updatedAt
) {
    public AccountResponse(Account account) {
        this(
                account.getId(),
                account.getUserId(),
                account.getName(),
                account.getType(),
                account.getInitialBalance(),
                account.isCountsAsAvailable(),
                account.getClosingDay(),
                account.getDueDay(),
                account.getInvoicesFrom(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
