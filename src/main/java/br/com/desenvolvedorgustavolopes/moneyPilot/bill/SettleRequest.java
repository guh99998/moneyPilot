package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record SettleRequest(
        @NotNull Long accountId,
        @NotNull @Positive BigDecimal amount
        ) {
}
