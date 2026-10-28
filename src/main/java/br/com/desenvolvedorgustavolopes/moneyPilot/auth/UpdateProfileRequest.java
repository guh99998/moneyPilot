package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Schema(description = "Nome exibido", example = "Ana Souza") @NotBlank
        @Size(max = 255)
        String name) {

    public UpdateProfileRequest {
        name = name == null ? null : name.strip();
    }
}
