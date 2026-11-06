package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class AccountWithoutInvoicesException extends RuntimeException {
    public AccountWithoutInvoicesException(Long id) {
        super("Account " + id + " has no closing and due days, so it has no invoices");
    }
}
