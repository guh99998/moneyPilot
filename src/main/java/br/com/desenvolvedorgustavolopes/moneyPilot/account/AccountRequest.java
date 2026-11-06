package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AccountRequest(
        @Schema(description = "Nome da conta", example = "Carteira") @NotBlank String name,
        @Schema(description = "Tipo da conta", enumAsRef = true) @NotNull AccountType accountType,
        @Schema(description = "Saldo inicial. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "1500.00") @NotNull @Digits(integer = 17, fraction = 2) BigDecimal initialBalance,
        @Schema(description = "Se a conta conta no saldo disponível. Opcional: na criação, omitido usa o padrão do tipo (investimento fica de fora); na edição, omitido mantém o valor atual, ou usa o padrão do novo tipo se o tipo mudou. Ao informar fechamento e vencimento pela primeira vez, o cartão sai do disponível, salvo escolha explícita aqui.", example = "true", nullable = true) Boolean countsAsAvailable,
        @Schema(description = "Dia do mês em que a fatura do cartão fecha (1 a 31; em mês mais curto vale o último dia). Só para cartão de crédito e sempre junto com dueDay. Na edição, omitido mantém os dias atuais.", example = "10", nullable = true) @Min(1) @Max(31) Integer closingDay,
        @Schema(description = "Dia do mês em que a fatura do cartão vence (1 a 31; em mês mais curto vale o último dia). Só para cartão de crédito e sempre junto com closingDay.", example = "17", nullable = true) @Min(1) @Max(31) Integer dueDay
) {
    public AccountRequest(String name, AccountType accountType, BigDecimal initialBalance, Boolean countsAsAvailable) {
        this(name, accountType, initialBalance, countsAsAvailable, null, null);
    }

    public AccountRequest(String name, AccountType accountType, BigDecimal initialBalance) {
        this(name, accountType, initialBalance, null, null, null);
    }

}
