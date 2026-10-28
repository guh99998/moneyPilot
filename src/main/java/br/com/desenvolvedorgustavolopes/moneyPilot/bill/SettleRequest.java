package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/** settledOn é o dia em que o pagamento aconteceu; vazio significa hoje. */
public record SettleRequest(
        @Schema(description = "Identificador da conta", example = "1") @NotNull Long accountId,
        @Schema(description = "Valor em reais, número com 2 casas decimais e ponto decimal.", example = "50.00") @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @Schema(description = "Data da quitação, aaaa-mm-dd; vazio usa hoje. Não pode ser futura", example = "2026-10-28") LocalDate settledOn
        ) {
    public SettleRequest(Long accountId, BigDecimal amount) {
        this(accountId, amount, null);
    }
}
