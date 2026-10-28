package br.com.desenvolvedorgustavolopes.moneyPilot.category;

import io.swagger.v3.oas.annotations.media.Schema;

public record CategoryResponse(
        @Schema(description = "Identificador", example = "1") Long id,
        @Schema(description = "Dono da categoria; vazio nas categorias padrão", example = "1") Long userId,
        @Schema(description = "Nome da categoria", example = "Mercado") String name,
        @Schema(description = "Despesa ou receita", enumAsRef = true) CategoryType type
) {
    public CategoryResponse(Category category) {
        this(
                category.getId(),
                category.getUserId(),
                category.getName(),
                category.getType()
        );
    }
}
