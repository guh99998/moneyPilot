package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();
    private static final String INVALID_TOKEN_MESSAGE = "Invalid refresh token";
    public static final Duration REFRESH_TTL = Duration.ofDays(30);

    private String generateRawToken() {
        byte[] rawToken = new byte[32];

        secureRandom.nextBytes(rawToken);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(rawToken);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytesToDigest = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytesToDigest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available in this JVM", e);
        }
    }

    public String issue(Long userId) {
        String value = this.generateRawToken();
        String token = this.hashToken(value);
        Instant now = Instant.now();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(userId);
        refreshToken.setTokenHash(token);
        refreshToken.setCreatedAt(now);
        refreshToken.setExpiresAt(now.plus(REFRESH_TTL));

        repository.save(refreshToken);

        return value;
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public RefreshResult rotate(String rawToken) {
        if(rawToken == null || rawToken.isBlank())
            throw new BadCredentialsException(INVALID_TOKEN_MESSAGE);
        RefreshToken refreshToken = repository.findByTokenHash(hashToken(rawToken)).orElseThrow(() -> new BadCredentialsException(INVALID_TOKEN_MESSAGE));
        Instant now = Instant.now();

        if (refreshToken.getRevokedAt() != null) {
            repository.revokeAllActiveByUserId(refreshToken.getUserId(), now);
            throw new BadCredentialsException(INVALID_TOKEN_MESSAGE);
        }

        if (refreshToken.getExpiresAt().isBefore(now))
            throw new BadCredentialsException(INVALID_TOKEN_MESSAGE);

        if (repository.revokeIfActive(refreshToken.getId(), now) == 0) {
            repository.revokeAllActiveByUserId(refreshToken.getUserId(), now);
            throw new BadCredentialsException(INVALID_TOKEN_MESSAGE);
        }

        return new RefreshResult(refreshToken.getUserId(), issue(refreshToken.getUserId()));
    }

    @Transactional
    public void revoke(String token) {
        if (token == null || token.isBlank())
            return;
        String hash = hashToken(token);
        Optional<RefreshToken> refreshToken = repository.findByTokenHash(hash);
        refreshToken.ifPresent(value -> repository.revokeIfActive(value.getId(), Instant.now()));
    }
}
