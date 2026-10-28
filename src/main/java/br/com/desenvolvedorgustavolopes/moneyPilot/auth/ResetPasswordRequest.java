package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @Schema(description = "Token recebido por e-mail", example = "q3F9x2Zk1mT7vB8nR4sW6yH0aD5cE2gJ") @NotBlank
        String token,

        @Schema(description = "Nova senha", example = "outra frase secreta") @NotBlank
        @NewPassword
        String newPassword) {
}
