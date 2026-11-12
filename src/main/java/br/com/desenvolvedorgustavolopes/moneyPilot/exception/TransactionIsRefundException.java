package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class TransactionIsRefundException extends RuntimeException {
    public TransactionIsRefundException(Long id) {
        super("Transaction " + id + " is a refund and cannot be edited; delete it to undo the refund");
    }
}
