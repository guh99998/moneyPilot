package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

public interface GoogleTokenVerifier {

    /** Valida o ID token e devolve a identidade; qualquer falha lança {@code InvalidGoogleTokenException}. */
    GoogleIdentity verify(String credential);
}
