package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @Schema(description = "Senha atual", example = "minha frase secreta") @NotBlank
        String currentPassword,

        @Schema(description = "Nova senha", example = "outra frase secreta") @NotBlank
        @NewPassword
        String newPassword) {
}
