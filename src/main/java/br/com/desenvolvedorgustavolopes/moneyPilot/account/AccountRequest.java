package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AccountRequest(
        @Schema(description = "Nome da conta", example = "Carteira") @NotBlank String name,
        @Schema(description = "Tipo da conta", enumAsRef = true) @NotNull AccountType accountType,
        @Schema(description = "Saldo inicial. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "1500.00") @NotNull @Digits(integer = 17, fraction = 2) BigDecimal initialBalance
) {

}
