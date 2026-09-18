package br.com.desenvolvedorgustavolopes.moneyPilot.category;

import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.CategoryCantChangeException;
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
        return repository.findVisibleToUser(userId, pageable).map(CategoryResponse::new);
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

    private Category findVisibleCategory(Long categoryId) {
        Long userId = userProvider.getCurrentUserId();

        Category category = repository.findById(categoryId).orElseThrow(() -> new CategoryNotFoundException(categoryId));

        if (category.getUserId() != null && !(category.getUserId().equals(userId)))
            throw new CategoryNotFoundException(categoryId);

        return category;
    }

    private Category findEditableCategory(Long categoryId) {
        Category category = this.findVisibleCategory(categoryId);

        if (category.getUserId() == null) {
            throw new CategoryCantChangeException(categoryId);
        }

        return category;
    }

    public CategoryResponse getCategoryById(Long categoryId) {
        Category category = this.findVisibleCategory(categoryId);

        return new CategoryResponse(category);
    }

    public CategoryResponse updateOwnedCategory(Long categoryId, CategoryRequest request) {
        Category category = this.findEditableCategory(categoryId);

        category.setName(request.name());
        category.setType(request.type());
        category.setUpdatedAt(Instant.now());

        return new CategoryResponse(repository.save(category));
    }

    public void deleteUserCategory(Long id) {
        Category category = this.findEditableCategory(id);

        repository.delete(category);
    }
}
