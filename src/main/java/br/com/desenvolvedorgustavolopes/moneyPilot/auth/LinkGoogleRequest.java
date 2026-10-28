package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LinkGoogleRequest(
        @NotBlank
        @Size(max = 4096)
        String credential,

        @NotBlank
        String currentPassword) {
}
