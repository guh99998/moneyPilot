package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        @Schema(description = "Identificador", example = "1") Long id,
        @Schema(description = "Identificador da pessoa dona do registro", example = "1") Long userId,
        @Schema(description = "Nome da conta", example = "Carteira") String name,
        @Schema(description = "Tipo da conta", enumAsRef = true) AccountType type,
        @Schema(description = "Saldo inicial. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "1500.00") BigDecimal initialBalance,
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
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
