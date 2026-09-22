package br.com.desenvolvedorgustavolopes.moneyPilot.budget;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService service;

    @GetMapping("")
    @ResponseStatus(HttpStatus.OK)
    public Page<BudgetResponse> getAllBudgets(@RequestParam(required = false) Long categoryId,
                                              @RequestParam(required = false) Integer month,
                                              @RequestParam(required = false) Integer year,
                                              @PageableDefault(size = 20, sort = {"year", "month"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return service.getAllBudgets(categoryId, month, year, pageable);
    }

    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public BudgetResponse createBudget(@RequestBody @Valid BudgetRequest request) {
        return service.createBudget(request);
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public BudgetResponse getBudgetById(@PathVariable Long id) {
        return service.getBudgetById(id);
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public BudgetResponse updateBudget(@PathVariable Long id, @RequestBody @Valid BudgetRequest request) {
        return service.updateBudget(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBudget(@PathVariable Long id) {
        service.deleteBudget(id);
    }
}
