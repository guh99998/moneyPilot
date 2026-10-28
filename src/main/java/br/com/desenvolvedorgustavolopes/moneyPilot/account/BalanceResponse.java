package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record BalanceResponse(
        @Schema(description = "Identificador da conta", example = "1") Long accountId,
        @Schema(description = "Saldo inicial. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "1500.00") BigDecimal initialBalance,
        @Schema(description = "Saldo atual. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "1450.00") BigDecimal currentBalance,
        @Schema(description = "Quantidade de lançamentos da conta", example = "12") Long transactionCount
) {
}
