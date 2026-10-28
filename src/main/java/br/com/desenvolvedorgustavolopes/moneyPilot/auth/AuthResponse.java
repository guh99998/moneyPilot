package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;

/** O navegador recebe só {@code token}; {@code refreshToken} e {@code expiresIn} saem apenas no modo corpo. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(@Schema(description = "Access token (JWT) para o cabeçalho Authorization: Bearer", example = "eyJhbGciOiJIUzI1NiJ9.exemplo.assinatura") String token, @Schema(description = "Refresh token: vale 30 dias, guarde como uma senha. Cada uso gera um novo", example = "q3F9x2Zk1mT7vB8nR4sW6yH0aD5cE2gJ") String refreshToken, @Schema(description = "Segundos até o access token vencer", example = "900") Long expiresIn) {

    public AuthResponse(String token) {
        this(token, null, null);
    }
}
