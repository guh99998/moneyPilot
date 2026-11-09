package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class RecurringPurchaseNotFoundException extends RuntimeException {
    public RecurringPurchaseNotFoundException(Long id) {
        super("Recurring purchase " + id + " is not found");
    }
}
