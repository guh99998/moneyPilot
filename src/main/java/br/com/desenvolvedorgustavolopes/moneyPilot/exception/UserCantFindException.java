package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class UserCantFindException extends RuntimeException {
    public UserCantFindException(String email) {
        super("Can't find user with e-mail " + email);
    }
}
