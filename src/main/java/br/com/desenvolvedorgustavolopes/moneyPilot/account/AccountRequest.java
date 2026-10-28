package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AccountRequest(
        @NotBlank String name,
        @NotNull AccountType accountType,
        @NotNull @Digits(integer = 17, fraction = 2) BigDecimal initialBalance
) {

}
