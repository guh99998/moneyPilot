package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class InvalidEmailChangeTokenException extends RuntimeException {
    public InvalidEmailChangeTokenException() {
        super("Invalid or expired e-mail change link");
    }
}
