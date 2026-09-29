package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class BillNotFoundException extends RuntimeException {
    public BillNotFoundException(Long id) {
        super("Can't find bill " + id);
    }
}
