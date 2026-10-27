package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import java.time.Instant;

public record ProfileResponse(
        String name,
        String email,
        Instant createdAt
) {
    public ProfileResponse(User user) {
        this(
                user.getName(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }
}
