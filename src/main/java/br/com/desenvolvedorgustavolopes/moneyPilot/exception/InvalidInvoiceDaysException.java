package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class InvalidInvoiceDaysException extends RuntimeException {
    private final String field;

    public InvalidInvoiceDaysException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
