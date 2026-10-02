package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

import java.time.LocalDate;

public class InvalidRecurrenceDateRangeException extends RuntimeException {
    public InvalidRecurrenceDateRangeException(LocalDate endDate, LocalDate startDate) {
        super("End date " + endDate + " must be after start date " + startDate);
    }
}
