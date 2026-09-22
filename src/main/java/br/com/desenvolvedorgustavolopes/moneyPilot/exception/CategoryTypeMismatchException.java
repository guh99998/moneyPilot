package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

import br.com.desenvolvedorgustavolopes.moneyPilot.category.CategoryType;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransactionType;

public class CategoryTypeMismatchException extends RuntimeException {
    public CategoryTypeMismatchException(Long categoryId, TransactionType type) {
        super("Category " + categoryId + " type does not match transaction type " + type);
    }

    public CategoryTypeMismatchException(Long categoryId, CategoryType type) {
        super("Category " + categoryId + " type " + type + " cannot be used in budgets");
    }
}
