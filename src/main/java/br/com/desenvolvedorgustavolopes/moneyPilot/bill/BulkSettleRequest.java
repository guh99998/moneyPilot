package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** settledOn vale para todos os títulos do lote; vazio significa hoje. */
public record BulkSettleRequest(
        @Schema(description = "Identificadores das contas a quitar") @NotEmpty @Size(max = 200) List<@NotNull Long> billIds,
        @Schema(description = "Identificador da conta", example = "1") @NotNull Long accountId,
        @Schema(description = "Data da quitação, aaaa-mm-dd; vazio usa hoje. Não pode ser futura", example = "2026-10-28") LocalDate settledOn
        ) {
    public BulkSettleRequest(List<Long> billIds, Long accountId) {
        this(billIds, accountId, null);
    }
}
