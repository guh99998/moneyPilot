package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import java.time.Instant;

public record ProfileResponse(
        String name,
        String email,
        Instant createdAt,
        String pendingEmail,
        boolean hasPassword,
        String googleEmail,
        String avatarUrl,
        boolean avatarHidden
) {
    public ProfileResponse(User user, String pendingEmail) {
        this(
                user.getName(),
                user.getEmail(),
                user.getCreatedAt(),
                pendingEmail,
                user.hasPassword(),
                user.getGoogleEmail(),
                user.isAvatarHidden() ? null : user.getAvatarUrl(),
                user.isAvatarHidden()
        );
    }
}
