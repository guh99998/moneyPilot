package br.com.desenvolvedorgustavolopes.moneyPilot.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CategoryRequest(
        @NotBlank String name,
        @NotNull CategoryType type
) {
}
