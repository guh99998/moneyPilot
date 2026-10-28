package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LinkGoogleRequest(
        @Schema(description = "Credencial (ID token) devolvida pelo Entrar com Google", example = "eyJhbGciOiJSUzI1NiJ9.exemplo.assinatura") @NotBlank
        @Size(max = 4096)
        String credential,

        @Schema(description = "Senha atual", example = "minha frase secreta") @NotBlank
        String currentPassword) {
}
