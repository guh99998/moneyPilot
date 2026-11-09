package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class InvalidInstallmentRequestException extends RuntimeException {
    private final String field;

    public InvalidInstallmentRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
