package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

import lombok.Getter;

import java.time.Duration;

@Getter
public class TooManyAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyAttemptsException(Duration retryAfter) {
        super("Too many attempts. Try again in " + minutes(retryAfter) + (minutes(retryAfter) == 1 ? " minute." : " minutes."));
        this.retryAfter = retryAfter;
    }

    private static long minutes(Duration duration) {
        return Math.max(1, (duration.toSeconds() + 59) / 60);
    }
}
