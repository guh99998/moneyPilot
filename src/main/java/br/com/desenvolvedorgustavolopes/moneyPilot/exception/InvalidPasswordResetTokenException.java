package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class InvalidPasswordResetTokenException extends RuntimeException {
    public InvalidPasswordResetTokenException() {
        super("Invalid or expired password reset link");
    }
}
