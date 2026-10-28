package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

/** Refresh token no corpo, para clientes que não são navegador. */
public record RefreshRequest(String refreshToken) {
}
