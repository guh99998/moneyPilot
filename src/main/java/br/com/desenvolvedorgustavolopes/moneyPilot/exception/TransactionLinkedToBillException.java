package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class TransactionLinkedToBillException extends RuntimeException {
    public TransactionLinkedToBillException(Long id) {
        super("The transaction " + id + " is linked to a bill; unsettle the bill first");
    }
}
