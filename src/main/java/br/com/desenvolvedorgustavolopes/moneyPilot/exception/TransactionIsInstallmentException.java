package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class TransactionIsInstallmentException extends RuntimeException {
    public TransactionIsInstallmentException(Long id) {
        super("Transaction " + id + " is an installment and cannot be edited directly");
    }
}
