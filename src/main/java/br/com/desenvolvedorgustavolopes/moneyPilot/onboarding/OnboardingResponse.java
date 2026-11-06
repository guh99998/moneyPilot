package br.com.desenvolvedorgustavolopes.moneyPilot.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record OnboardingResponse(
        @Schema(description = "Se o pop-up de primeiro contato deve abrir sozinho no login: elegível e com \"Mostrar ao iniciar\" marcado", example = "true") boolean visible,
        @Schema(description = "Se a conta ainda é de primeiros passos: tem registro das boas-vindas, não dispensou o cartão antigo e ainda há passo pendente. Decide se a caixinha \"Mostrar ao iniciar\" aparece", example = "true") boolean eligible,
        @Schema(description = "Valor da caixinha \"Mostrar ao iniciar\". Só tem efeito quando eligible é true", example = "true") boolean showOnStart,
        @Schema(description = "Os três passos, na ordem de exibição, sempre presentes") List<OnboardingStepResponse> steps
) {
}
