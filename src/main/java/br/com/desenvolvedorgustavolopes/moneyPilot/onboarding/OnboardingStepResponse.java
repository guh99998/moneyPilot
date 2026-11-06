package br.com.desenvolvedorgustavolopes.moneyPilot.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;

public record OnboardingStepResponse(
        @Schema(description = "Qual é o passo", implementation = OnboardingStep.class, example = "ACCOUNT") OnboardingStep key,
        @Schema(description = "Se o passo já foi feito. Passo feito continua feito, mesmo que a pessoa apague depois o que o cumpriu", example = "false") boolean done
) {
}
