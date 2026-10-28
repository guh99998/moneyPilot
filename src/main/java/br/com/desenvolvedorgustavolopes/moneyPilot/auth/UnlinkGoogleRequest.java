package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import jakarta.validation.constraints.NotBlank;

public record UnlinkGoogleRequest(
        @NotBlank
        String currentPassword) {
}
