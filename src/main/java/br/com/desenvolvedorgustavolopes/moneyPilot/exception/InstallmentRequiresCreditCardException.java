package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class InstallmentRequiresCreditCardException extends RuntimeException {
    public InstallmentRequiresCreditCardException(Long accountId) {
        super("Account " + accountId + " is not a credit card, so it cannot hold an installment purchase");
    }
}
