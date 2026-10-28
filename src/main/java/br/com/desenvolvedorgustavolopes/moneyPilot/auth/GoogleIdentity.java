package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

/**
 * O que o Google afirma sobre a pessoa num ID token já validado. {@code subject} é o identificador estável
 * da conta Google; o e-mail pode mudar.
 */
public record GoogleIdentity(String subject, String email, String name, String picture) {
}
