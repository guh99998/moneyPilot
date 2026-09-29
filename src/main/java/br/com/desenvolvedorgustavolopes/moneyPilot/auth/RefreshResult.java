package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

public record RefreshResult(
        Long userId,
        String refreshToken
) {
}
