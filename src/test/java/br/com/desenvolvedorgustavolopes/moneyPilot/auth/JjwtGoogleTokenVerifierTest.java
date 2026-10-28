package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.config.GoogleProperties;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.InvalidGoogleTokenException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JjwtGoogleTokenVerifierTest {

    private static final String CLIENT_ID = "meu-app.apps.googleusercontent.com";
    private static final String KEY_ID = "chave-1";
    private static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");

    private static KeyPair googleKeys;
    private static KeyPair strangerKeys;

    private final GoogleKeySource keySource = kid -> KEY_ID.equals(kid) ? googleKeys.getPublic() : null;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        googleKeys = generator.generateKeyPair();
        strangerKeys = generator.generateKeyPair();
    }

    @Test
    void verify_acceptsAValidToken_andReturnsTheIdentity() {
        GoogleIdentity identity = verifier(NOW).verify(token().compact());

        assertEquals("1234567890", identity.subject());
        assertEquals("ana@example.com", identity.email());
        assertEquals("Ana Souza", identity.name());
        assertEquals("https://lh3.example/ana.png", identity.picture());
    }

    @Test
    void verify_normalizesTheEmail() {
        GoogleIdentity identity = verifier(NOW).verify(token().claim("email", " Ana@Example.COM ").compact());

        assertEquals("ana@example.com", identity.email());
    }

    @Test
    void verify_acceptsTheIssuerWithScheme() {
        GoogleIdentity identity = verifier(NOW).verify(token().issuer("https://accounts.google.com").compact());

        assertEquals("1234567890", identity.subject());
    }

    @Test
    void verify_acceptsEmailVerifiedSentAsText() {
        GoogleIdentity identity = verifier(NOW).verify(token().claim("email_verified", "true").compact());

        assertEquals("ana@example.com", identity.email());
    }

    @Test
    void verify_rejectsATokenSignedWithAnotherKey() {
        String token = token().signWith(strangerKeys.getPrivate(), Jwts.SIG.RS256).compact();

        assertRejected(token);
    }

    @Test
    void verify_rejectsATokenIssuedToAnotherApp() {
        assertRejected(unsigned("outro-app.apps.googleusercontent.com")
                .claim("email_verified", true)
                .signWith(googleKeys.getPrivate(), Jwts.SIG.RS256)
                .compact());
    }

    @Test
    void verify_rejectsAnotherIssuer() {
        assertRejected(token().issuer("https://accounts.exemplo.com").compact());
    }

    @Test
    void verify_rejectsAnExpiredToken_byTheInjectedClock() {
        String token = token().compact();

        assertThrows(InvalidGoogleTokenException.class,
                () -> verifier(NOW.plus(Duration.ofHours(2))).verify(token));
    }

    @Test
    void verify_rejectsAnUnverifiedEmail() {
        assertRejected(token().claim("email_verified", false).compact());
    }

    @Test
    void verify_rejectsATokenWithoutEmailVerifiedClaim() {
        assertRejected(unsigned().signWith(googleKeys.getPrivate(), Jwts.SIG.RS256).compact());
    }

    @Test
    void verify_rejectsAnUnknownKeyId() {
        JwtBuilder builder = token();
        builder.header().keyId("chave-que-nao-existe");

        assertRejected(builder.compact());
    }

    @Test
    void verify_rejectsAnUnsignedToken() {
        String token = Jwts.builder()
                .subject("1234567890")
                .issuer("accounts.google.com")
                .audience().add(CLIENT_ID).and()
                .claim("email", "ana@example.com")
                .claim("email_verified", true)
                .expiration(Date.from(NOW.plus(Duration.ofHours(1))))
                .compact();

        assertRejected(token);
    }

    @Test
    void verify_rejectsGarbage() {
        assertRejected("isto-nao-e-um-jwt");
        assertRejected("");
        assertRejected(null);
    }

    @Test
    void verify_rejectsEverything_whenTheFeatureIsOff() {
        JjwtGoogleTokenVerifier off = new JjwtGoogleTokenVerifier(keySource, new GoogleProperties(""), clock(NOW));

        assertThrows(InvalidGoogleTokenException.class, () -> off.verify(token().compact()));
    }

    private void assertRejected(String token) {
        assertThrows(InvalidGoogleTokenException.class, () -> verifier(NOW).verify(token));
    }

    private JjwtGoogleTokenVerifier verifier(Instant now) {
        return new JjwtGoogleTokenVerifier(keySource, new GoogleProperties(CLIENT_ID), clock(now));
    }

    private static Clock clock(Instant now) {
        return Clock.fixed(now, ZoneOffset.UTC);
    }

    /** Um ID token válido; cada teste estraga uma coisa. */
    private JwtBuilder token() {
        return unsigned()
                .claim("email_verified", true)
                .signWith(googleKeys.getPrivate(), Jwts.SIG.RS256);
    }

    private JwtBuilder unsigned() {
        return unsigned(CLIENT_ID);
    }

    private JwtBuilder unsigned(String audience) {
        return Jwts.builder()
                .header().keyId(KEY_ID).and()
                .issuer("accounts.google.com")
                .subject("1234567890")
                .audience().add(audience).and()
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plus(Duration.ofHours(1))))
                .claims(Map.of(
                        "email", "ana@example.com",
                        "name", "Ana Souza",
                        "picture", "https://lh3.example/ana.png"));
    }
}
