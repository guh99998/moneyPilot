package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class SameEmailException extends RuntimeException {
    public SameEmailException() {
        super("New e-mail must be different from the current one");
    }
}
