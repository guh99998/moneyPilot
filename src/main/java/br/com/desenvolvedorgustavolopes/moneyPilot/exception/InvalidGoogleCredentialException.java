package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

/** O ID token enviado a uma rota autenticada é inválido: 400 no campo, para não parecer sessão expirada. */
public class InvalidGoogleCredentialException extends RuntimeException {
    public InvalidGoogleCredentialException() {
        super("Invalid Google credential");
    }
}
