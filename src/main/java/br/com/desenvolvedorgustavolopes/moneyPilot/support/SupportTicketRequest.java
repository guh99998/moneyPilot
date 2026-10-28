package br.com.desenvolvedorgustavolopes.moneyPilot.support;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * {@code email} só vale sem login; logado, o serviço usa o e-mail da conta. A mensagem é medida e guardada sem os
 * espaços das pontas, mas o miolo (quebras de linha, acentos, HTML) segue como foi digitado.
 */
public record SupportTicketRequest(
        @NotNull(message = "Escolha uma categoria.") SupportCategory category,
        @Size(min = 10, max = 2000, message = "A mensagem deve ter de 10 a 2.000 caracteres.") String message,
        @Email(message = "Informe um e-mail válido.") String email
) {

    public SupportTicketRequest {
        message = message == null ? "" : message.strip();
        email = email == null || email.isBlank() ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
