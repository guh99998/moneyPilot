package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.config.GoogleProperties;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.InvalidGoogleTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.Clock;
import java.util.Date;
import java.util.Set;

/**
 * Valida o ID token do Google: assinatura (chaves públicas do Google), audience (nosso client ID), emissor,
 * expiração (pelo {@link Clock} injetado) e e-mail verificado pelo próprio Google.
 */
@Component
@RequiredArgsConstructor
public class JjwtGoogleTokenVerifier implements GoogleTokenVerifier {

    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    private static final long CLOCK_SKEW_SECONDS = 30;

    private final GoogleKeySource keySource;
    private final GoogleProperties properties;
    private final Clock clock;

    @Override
    public GoogleIdentity verify(String credential) {
        if (!properties.enabled() || credential == null || credential.isBlank()) {
            throw new InvalidGoogleTokenException();
        }

        try {
            Claims claims = Jwts.parser()
                    .keyLocator(new LocatorAdapter<Key>() {
                        @Override
                        protected Key locate(ProtectedHeader header) {
                            Key key = keySource.keyFor(header.getKeyId());
                            if (key == null) {
                                throw new InvalidGoogleTokenException();
                            }
                            return key;
                        }
                    })
                    .clock(() -> Date.from(clock.instant()))
                    .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                    .requireAudience(properties.clientId())
                    .build()
                    .parseSignedClaims(credential)
                    .getPayload();

            if (!ISSUERS.contains(claims.getIssuer()) || !emailVerified(claims)) {
                throw new InvalidGoogleTokenException();
            }

            String subject = claims.getSubject();
            String email = claims.get("email", String.class);

            if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
                throw new InvalidGoogleTokenException();
            }

            return new GoogleIdentity(
                    subject,
                    EmailAddress.normalize(email),
                    claims.get("name", String.class),
                    claims.get("picture", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidGoogleTokenException();
        }
    }

    // O Google manda o booleano; algumas versões antigas mandam a string "true".
    private static boolean emailVerified(Claims claims) {
        Object value = claims.get("email_verified");
        return Boolean.TRUE.equals(value) || "true".equals(value);
    }
}
