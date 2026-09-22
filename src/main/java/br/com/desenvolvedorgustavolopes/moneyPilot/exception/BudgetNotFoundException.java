package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class BudgetNotFoundException extends RuntimeException {
    public BudgetNotFoundException(Long budgetId) {
        super("Can't find budget " + budgetId);
    }
}
