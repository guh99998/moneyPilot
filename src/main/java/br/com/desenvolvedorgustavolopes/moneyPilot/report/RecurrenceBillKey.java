package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import java.time.LocalDate;

public interface RecurrenceBillKey {
    Long getRecurrenceId();
    LocalDate getDueDate();
}
