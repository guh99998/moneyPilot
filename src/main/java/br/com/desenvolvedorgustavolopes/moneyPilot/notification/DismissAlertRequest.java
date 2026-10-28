package br.com.desenvolvedorgustavolopes.moneyPilot.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DismissAlertRequest(
        @Schema(description = "Chave do alerta", example = "PAYABLE_OVERDUE") @NotBlank
        @Size(max = 100)
        String key) {
}
