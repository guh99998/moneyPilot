package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class BillNotSettledException extends RuntimeException {
    public BillNotSettledException(Long id) {
        super("Bill " + id + " is not settled");
    }
}
