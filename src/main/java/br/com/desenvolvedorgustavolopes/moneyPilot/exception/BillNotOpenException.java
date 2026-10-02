package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class BillNotOpenException extends RuntimeException {
    public BillNotOpenException(Long id) {
        super("Bill " + id + " is not open");
    }
}
