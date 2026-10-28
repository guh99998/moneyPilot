package br.com.desenvolvedorgustavolopes.moneyPilot.support;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateSupportTicketStatusRequest(
        @Schema(description = "Novo status do chamado", enumAsRef = true) @NotNull(message = "Escolha um status.") SupportTicketStatus status
) {
}
