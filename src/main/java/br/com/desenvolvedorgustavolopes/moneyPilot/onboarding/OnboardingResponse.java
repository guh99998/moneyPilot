package br.com.desenvolvedorgustavolopes.moneyPilot.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record OnboardingResponse(
        @Schema(description = "Se o cartão de primeiros passos deve aparecer: só em conta nova, até a pessoa dispensar ou concluir os três passos", example = "true") boolean visible,
        @Schema(description = "Os passos, na ordem de exibição. Vazio quando o cartão não é visível") List<OnboardingStepResponse> steps
) {
    public static OnboardingResponse hidden() {
        return new OnboardingResponse(false, List.of());
    }
}
