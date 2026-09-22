package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class CategoryCantChangeException extends RuntimeException {
    public CategoryCantChangeException(Long id) {
        super("Category " + id + " cannot be changed; it is only reading");
    }
}
