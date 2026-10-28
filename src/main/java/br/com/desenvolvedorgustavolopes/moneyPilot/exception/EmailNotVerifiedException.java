package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException() {
        super("E-mail not verified");
    }
}
