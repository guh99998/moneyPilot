package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/** settledOn é o dia em que o pagamento aconteceu; vazio significa hoje. */
public record SettleRequest(
        @NotNull Long accountId,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
        LocalDate settledOn
        ) {
    public SettleRequest(Long accountId, BigDecimal amount) {
        this(accountId, amount, null);
    }
}
