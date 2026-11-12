package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class TransactionNotRefundableException extends RuntimeException {
    public TransactionNotRefundableException(Long id) {
        super("Transaction " + id + " cannot be refunded: only credit card expenses that are not transfers, bill payments or refunds");
    }
}
