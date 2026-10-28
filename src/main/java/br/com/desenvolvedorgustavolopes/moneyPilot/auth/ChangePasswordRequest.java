package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank
        String currentPassword,

        @NotBlank
        @NewPassword
        String newPassword) {
}
