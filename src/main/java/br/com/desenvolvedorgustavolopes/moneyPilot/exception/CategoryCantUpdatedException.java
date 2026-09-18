package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class CategoryCantUpdatedException extends RuntimeException {
    public CategoryCantUpdatedException(Long id) {
        super("Category " + id + " cannot be updated; it is part of system");
    }
}
