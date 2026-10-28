package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(description = "E-mail", example = "ana@exemplo.com") @NotBlank @Email String email,
        @Schema(description = "Senha", example = "minha frase secreta") @NotBlank String password,
        @Schema(description = "Peça true para receber o refresh token no corpo (clientes fora do navegador); sem isso ele vai só em cookie", example = "true") Boolean refreshTokenInBody
) {

    public LoginRequest {
        email = EmailAddress.normalize(email);
    }

    public LoginRequest(String email, String password) {
        this(email, password, null);
    }

    public boolean wantsRefreshTokenInBody() {
        return Boolean.TRUE.equals(refreshTokenInBody);
    }
}
