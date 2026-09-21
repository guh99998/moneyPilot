package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class TransactionIsTransferException extends RuntimeException {
    public TransactionIsTransferException(Long id) {
        super("Transaction " + id + " is part of a transfer and cannot be edited directly");
    }
}
