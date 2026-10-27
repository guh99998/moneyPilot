package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class AlertNotFoundException extends RuntimeException {
    public AlertNotFoundException(String key) {
        super("Can't find active alert " + key);
    }
}
