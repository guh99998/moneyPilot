package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class InvalidForecastPeriodException extends RuntimeException {
    public InvalidForecastPeriodException(Integer days) {
        super("Forecast period must be 30, 60 or 90 days, got " + days);
    }
}
