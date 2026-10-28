package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

/** O e-mail é o da conta no app, que pode ser diferente do e-mail do Google. */
public record GoogleSignInResponse(String token, String email) {
}
