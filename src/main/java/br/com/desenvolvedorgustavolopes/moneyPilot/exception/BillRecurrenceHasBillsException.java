package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class BillRecurrenceHasBillsException extends RuntimeException {
    public BillRecurrenceHasBillsException(Long id) {
        super("Recurrence " + id + " already generated bills; deactivate it instead of deleting");
    }
}
