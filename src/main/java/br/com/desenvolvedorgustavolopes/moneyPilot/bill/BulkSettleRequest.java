package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** settledOn vale para todos os títulos do lote; vazio significa hoje. */
public record BulkSettleRequest(
        @NotEmpty @Size(max = 200) List<@NotNull Long> billIds,
        @NotNull Long accountId,
        LocalDate settledOn
        ) {
    public BulkSettleRequest(List<Long> billIds, Long accountId) {
        this(billIds, accountId, null);
    }
}
