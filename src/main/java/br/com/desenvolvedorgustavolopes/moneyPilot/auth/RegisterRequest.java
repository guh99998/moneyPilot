package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank
        @Email
        String email,

        @NotBlank
        @NewPassword
        String password,

        @NotBlank
        String name) {

    public RegisterRequest {
        email = EmailAddress.normalize(email);
    }
}
