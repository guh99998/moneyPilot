package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class InvalidGoogleTokenException extends RuntimeException {
    public InvalidGoogleTokenException() {
        super("Invalid Google credential");
    }
}
