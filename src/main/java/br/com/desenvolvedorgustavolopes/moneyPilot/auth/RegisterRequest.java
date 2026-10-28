package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @Schema(description = "E-mail", example = "ana@exemplo.com") @NotBlank
        @Email
        String email,

        @Schema(description = "Senha", example = "minha frase secreta") @NotBlank
        @NewPassword
        String password,

        @Schema(description = "Nome exibido", example = "Ana Souza") @NotBlank
        String name) {

    public RegisterRequest {
        email = EmailAddress.normalize(email);
    }
}
