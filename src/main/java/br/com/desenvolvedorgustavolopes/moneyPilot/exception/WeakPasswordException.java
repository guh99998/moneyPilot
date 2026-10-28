package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

import lombok.Getter;

@Getter
public class WeakPasswordException extends RuntimeException {

    private final String field;

    public WeakPasswordException(String field, String message) {
        super(message);
        this.field = field;
    }
}
