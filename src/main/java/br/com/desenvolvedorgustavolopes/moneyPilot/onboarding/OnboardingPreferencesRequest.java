package br.com.desenvolvedorgustavolopes.moneyPilot.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record OnboardingPreferencesRequest(
        @Schema(description = "Se o pop-up de primeiros passos deve abrir sozinho a cada login", example = "false")
        @NotNull(message = "Informe se o pop-up deve abrir ao iniciar") Boolean showOnStart
) {
}
