package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

/** A confirmação da exclusão veio incompleta ou do tipo errado para a conta: 400 no campo indicado. */
public class DeletionConfirmationException extends RuntimeException {

    private final String field;

    public DeletionConfirmationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
