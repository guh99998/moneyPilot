package br.com.desenvolvedorgustavolopes.moneyPilot.category;

import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.CategoryCantUpdatedException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.CategoryNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository repository;
    private final AuthenticatedUserProvider userProvider;

    public Page<CategoryResponse> getAllCategories(Pageable pageable) {
        Long userId = userProvider.getCurrentUserId();
        return repository.findByUserIdIsNullOrUserId(userId, pageable).map(CategoryResponse::new);
    }

    public CategoryResponse createCategory(CategoryRequest request) {
        Category category = new Category();

        category.setUserId(userProvider.getCurrentUserId());
        category.setName(request.name());
        category.setType(request.type());
        category.setCreatedAt(Instant.now());
        category.setUpdatedAt(Instant.now());

        return new CategoryResponse(repository.save(category));
    }

    private Category findOwnedCategories(Long categoryId) {
        Long userId = userProvider.getCurrentUserId();

        return repository.findByIdAndUserId(categoryId, userId).orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    public CategoryResponse getCategoryById(Long categoryId) {
        Category category = this.findOwnedCategories(categoryId);

        return new CategoryResponse(category);
    }

    public CategoryResponse updateOwnedCategory(Long categoryId, CategoryRequest request) {
        Category category = this.findOwnedCategories(categoryId);

        if (category.getUserId() == null) {
            throw new CategoryCantUpdatedException(categoryId);
        }

        category.setName(request.name());
        category.setType(request.type());
        category.setUpdatedAt(Instant.now());

        return new CategoryResponse(repository.save(category));
    }

    public void deleteUserCategory(Long id) {
        Category category = this.findOwnedCategories(id);

        repository.delete(category);
    }
}
