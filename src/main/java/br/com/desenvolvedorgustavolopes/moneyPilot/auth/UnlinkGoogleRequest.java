package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UnlinkGoogleRequest(
        @Schema(description = "Senha atual", example = "minha frase secreta") @NotBlank
        String currentPassword) {
}
