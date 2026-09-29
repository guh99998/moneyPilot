package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkSettleRequest(
        @NotEmpty @Size(max = 200) List<@NotNull Long> billIds,
        @NotNull Long accountId
        ) {
}
