package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(Long id) {
        super("Can't find account " + id);
    }
}
