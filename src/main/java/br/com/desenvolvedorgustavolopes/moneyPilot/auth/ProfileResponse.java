package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import java.time.Instant;

public record ProfileResponse(
        String name,
        String email,
        Instant createdAt,
        String pendingEmail
) {
    public ProfileResponse(User user, String pendingEmail) {
        this(
                user.getName(),
                user.getEmail(),
                user.getCreatedAt(),
                pendingEmail
        );
    }
}
