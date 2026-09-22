package br.com.desenvolvedorgustavolopes.moneyPilot.budget;

import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import br.com.desenvolvedorgustavolopes.moneyPilot.category.Category;
import br.com.desenvolvedorgustavolopes.moneyPilot.category.CategoryRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.category.CategoryService;
import br.com.desenvolvedorgustavolopes.moneyPilot.category.CategoryType;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.BudgetNotFoundException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.CategoryTypeMismatchException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final AuthenticatedUserProvider userProvider;
    private final CategoryService categoryService;
    private final CategoryRepository categoryRepository;
    private final BudgetRepository repository;

    public Page<BudgetResponse> getAllBudgets(Long categoryId, Integer month, Integer year, Pageable pageable) {
        Long userId = userProvider.getCurrentUserId();

        Page<Budget> budgets = repository.findAllFiltered(userId, categoryId, month, year, pageable);

        List<Long> categoryIds = budgets.getContent().stream()
                .map(Budget::getCategoryId)
                .distinct()
                .toList();

        Map<Long, String> categoryNames = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        return budgets.map(budget -> new BudgetResponse(budget, categoryNames.get(budget.getCategoryId())));
    }

    public BudgetResponse createBudget(BudgetRequest request) {
        Long userId = userProvider.getCurrentUserId();
        Category category = categoryService.findVisibleCategory(request.categoryId());

        if(category.getType() != CategoryType.EXPENSE)
            throw new CategoryTypeMismatchException(category.getId(), category.getType());

        Budget budget = new Budget();

        budget.setUserId(userId);
        budget.setCategoryId(request.categoryId());
        budget.setAmountLimit(request.amountLimit());
        budget.setMonth(request.month());
        budget.setYear(request.year());
        budget.setCreatedAt(Instant.now());
        budget.setUpdatedAt(Instant.now());

        return new BudgetResponse(repository.save(budget), category.getName());
    }

    private Budget findOwnedBudget(Long budgetId) {
        Long userId = userProvider.getCurrentUserId();

        return repository.findByIdAndUserId(budgetId, userId).orElseThrow(() -> new BudgetNotFoundException(budgetId));
    }

    public BudgetResponse getBudgetById(Long id) {
        Budget budget = this.findOwnedBudget(id);
        Category category = categoryService.findVisibleCategory(budget.getCategoryId());

        return new BudgetResponse(budget, category.getName());
    }

    public BudgetResponse updateBudget(Long id, BudgetRequest request) {
        Budget budget = this.findOwnedBudget(id);
        Category category = categoryService.findVisibleCategory(request.categoryId());

        if(category.getType() != CategoryType.EXPENSE)
            throw new CategoryTypeMismatchException(category.getId(), category.getType());

        budget.setCategoryId(category.getId());
        budget.setAmountLimit(request.amountLimit());
        budget.setMonth(request.month());
        budget.setYear(request.year());
        budget.setUpdatedAt(Instant.now());

        return new BudgetResponse(repository.save(budget), category.getName());
    }

    public void deleteBudget(Long id) {
        Budget budget = this.findOwnedBudget(id);

        repository.delete(budget);
    }
}
