package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

/** O navegador recebe só {@code token}; {@code refreshToken} e {@code expiresIn} saem apenas no modo corpo. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(String token, String refreshToken, Long expiresIn) {

    public AuthResponse(String token) {
        this(token, null, null);
    }
}
