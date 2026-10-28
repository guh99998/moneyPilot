package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class InvalidVerificationTokenException extends RuntimeException {
    public InvalidVerificationTokenException() {
        super("Invalid or expired verification link");
    }
}
