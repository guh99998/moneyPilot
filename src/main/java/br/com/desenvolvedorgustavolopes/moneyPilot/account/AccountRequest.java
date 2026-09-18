package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AccountRequest(
        @NotBlank String name,
        @NotNull AccountType accountType,
        @NotNull BigDecimal initialBalance
) {

}
