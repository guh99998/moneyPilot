package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailChangeRequest(
        @Schema(description = "Novo e-mail", example = "ana.nova@exemplo.com") @NotBlank
        @Email
        @Size(max = 255)
        String newEmail,

        @Schema(description = "Senha atual", example = "minha frase secreta") @NotBlank
        String currentPassword) {

    public EmailChangeRequest {
        newEmail = EmailAddress.normalize(newEmail);
    }
}
