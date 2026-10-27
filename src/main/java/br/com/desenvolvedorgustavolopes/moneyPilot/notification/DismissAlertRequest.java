package br.com.desenvolvedorgustavolopes.moneyPilot.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DismissAlertRequest(
        @NotBlank
        @Size(max = 100)
        String key) {
}
