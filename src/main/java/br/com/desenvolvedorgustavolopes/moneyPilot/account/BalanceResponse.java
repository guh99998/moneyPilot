package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import java.math.BigDecimal;

public record BalanceResponse(
        Long accountId,
        BigDecimal initialBalance,
        BigDecimal currentBalance,
        Long transactionCount
) {
}
