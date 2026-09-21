package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(Long id) {
        super("Can't find transaction " + id);
    }
}
