package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        Boolean refreshTokenInBody
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
