package br.com.desenvolvedorgustavolopes.moneyPilot.category;

public record CategoryResponse(
        Long id,
        Long userId,
        String name,
        CategoryType type
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
