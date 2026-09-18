package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException(Long id) {
        super("Can't find category " + id);
    }
}
