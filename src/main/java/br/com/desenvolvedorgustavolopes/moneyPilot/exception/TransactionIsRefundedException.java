package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class TransactionIsRefundedException extends RuntimeException {
    public TransactionIsRefundedException(Long id) {
        super("Transaction " + id + " is refunded (or has nothing left to refund) and cannot be changed");
    }
}
