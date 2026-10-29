package br.com.desenvolvedorgustavolopes.moneyPilot.support;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record MySupportTicketDetailResponse(
        @Schema(description = "Número de referência, o mesmo do e-mail ao suporte", example = "K7P3XN") String reference,
        @Schema(description = "Assunto do chamado", enumAsRef = true) SupportCategory category,
        @Schema(description = "Situação do chamado", enumAsRef = true) SupportTicketStatus status,
        @Schema(description = "Aberto em (UTC)", example = "2026-10-28T12:00:00Z") Instant createdAt,
        @Schema(description = "Última mudança de status (UTC); vazio se o status nunca mudou", example = "2026-10-28T13:00:00Z") Instant statusChangedAt,
        @Schema(description = "Mensagem enviada ao suporte, texto puro", example = "Não consigo exportar meus lançamentos.") String message,
        @Schema(description = "E-mail que recebe a resposta", example = "ana@exemplo.com") String replyTo
) {
    public MySupportTicketDetailResponse(SupportTicket ticket) {
        this(ticket.getReference(), ticket.getCategory(), ticket.getStatus(), ticket.getCreatedAt(),
                ticket.getStatusChangedAt(), ticket.getMessage(), ticket.getReplyTo());
    }
}
