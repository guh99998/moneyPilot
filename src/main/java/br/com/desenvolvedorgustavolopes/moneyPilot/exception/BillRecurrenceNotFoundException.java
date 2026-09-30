package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class BillRecurrenceNotFoundException extends RuntimeException {
    public BillRecurrenceNotFoundException(Long id) {
        super("Bill recurrence " + id + " is not found");
    }
}
