package br.com.desenvolvedorgustavolopes.moneyPilot.support;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record SupportTicketDetailResponse(
        @Schema(description = "Identificador", example = "1") Long id,
        @Schema(description = "Número de referência, o mesmo do e-mail ao suporte", example = "K7P3XN") String reference,
        @Schema(description = "Assunto do chamado", enumAsRef = true) SupportCategory category,
        @Schema(description = "Situação do chamado", enumAsRef = true) SupportTicketStatus status,
        @Schema(description = "E-mail para a resposta", example = "ana@exemplo.com") String replyTo,
        @Schema(description = "Se o e-mail ao suporte foi enviado; falso pede atenção do administrador", example = "true") boolean emailSent,
        @Schema(description = "Aberto em (UTC)", example = "2026-10-28T12:00:00Z") Instant createdAt,
        @Schema(description = "Mensagem da pessoa, texto puro", example = "Não consigo ver meu saldo.") String message,
        @Schema(description = "Última mudança de status (UTC), se houve", example = "2026-10-28T13:00:00Z") Instant statusChangedAt,
        @Schema(description = "Nome da conta que abriu o chamado, se estava logada", example = "Ana Souza") String accountName
) {
    public SupportTicketDetailResponse(SupportTicket ticket, String accountName) {
        this(ticket.getId(), ticket.getReference(), ticket.getCategory(), ticket.getStatus(), ticket.getReplyTo(),
                ticket.isEmailSent(), ticket.getCreatedAt(), ticket.getMessage(), ticket.getStatusChangedAt(), accountName);
    }
}
