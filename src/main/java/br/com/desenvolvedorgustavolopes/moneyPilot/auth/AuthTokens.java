package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

public record AuthTokens(
        String accessToken,
        String refreshToken
) {
}
