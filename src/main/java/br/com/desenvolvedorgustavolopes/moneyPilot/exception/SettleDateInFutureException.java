package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

import java.time.LocalDate;

public class SettleDateInFutureException extends RuntimeException {
    public SettleDateInFutureException(LocalDate settledOn, LocalDate today) {
        super("Settlement date " + settledOn + " cannot be after today (" + today + ")");
    }
}
