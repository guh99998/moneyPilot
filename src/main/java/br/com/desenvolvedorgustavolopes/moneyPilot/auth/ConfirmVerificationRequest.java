package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import jakarta.validation.constraints.NotBlank;

public record ConfirmVerificationRequest(@NotBlank String token) {
}
