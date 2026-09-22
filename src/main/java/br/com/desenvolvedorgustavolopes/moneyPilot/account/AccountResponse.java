package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        Long id,
        Long userId,
        String name,
        AccountType type,
        BigDecimal initialBalance,
        Instant createdAt,
        Instant updatedAt
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
