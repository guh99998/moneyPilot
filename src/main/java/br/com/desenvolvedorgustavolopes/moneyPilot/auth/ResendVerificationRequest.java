package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequest(@Schema(description = "E-mail", example = "ana@exemplo.com") @NotBlank @Email String email) {

    public ResendVerificationRequest {
        email = EmailAddress.normalize(email);
    }
}
