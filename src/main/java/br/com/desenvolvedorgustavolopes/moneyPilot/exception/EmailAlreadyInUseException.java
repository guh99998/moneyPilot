package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class EmailAlreadyInUseException extends RuntimeException {
    public EmailAlreadyInUseException() {
        super("E-mail already in use");
    }
}
