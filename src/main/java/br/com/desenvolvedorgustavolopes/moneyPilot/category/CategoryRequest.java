package br.com.desenvolvedorgustavolopes.moneyPilot.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CategoryRequest(
        @Schema(description = "Nome da categoria", example = "Mercado") @NotBlank String name,
        @Schema(description = "Despesa ou receita", enumAsRef = true) @NotNull CategoryType type
) {
}
