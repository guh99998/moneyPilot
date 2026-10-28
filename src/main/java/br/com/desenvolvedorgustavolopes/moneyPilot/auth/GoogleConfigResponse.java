package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

/** {@code clientId} vazio significa que o login com Google está desligado. */
public record GoogleConfigResponse(String clientId) {
}
