package br.com.desenvolvedorgustavolopes.moneyPilot.support;

import io.swagger.v3.oas.annotations.media.Schema;

public record SupportTicketResponse(@Schema(description = "Referência da mensagem, para citar ao falar com o suporte", example = "K7M2QX") String reference, @Schema(description = "E-mail para onde a resposta vai", example = "ana@exemplo.com") String replyTo) {
}
