package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

/** O Google usado para confirmar a exclusão não é o vinculado à conta: 400 no campo {@code credential}. */
public class GoogleAccountMismatchException extends RuntimeException {
    public GoogleAccountMismatchException() {
        super("This is not the Google account linked to your account");
    }
}
