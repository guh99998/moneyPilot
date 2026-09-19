package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class EmailAlreadyRegistredException extends RuntimeException {
    public EmailAlreadyRegistredException() {
        super("E-mail already registered");
    }
}
