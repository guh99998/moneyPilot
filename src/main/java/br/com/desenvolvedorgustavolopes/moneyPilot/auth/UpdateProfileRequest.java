package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank
        @Size(max = 255)
        String name) {

    public UpdateProfileRequest {
        name = name == null ? null : name.strip();
    }
}
