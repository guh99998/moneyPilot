package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.InvalidGoogleTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpGoogleKeySourceTest {

    private static final String URL = "https://google.test/certs";

    private MockRestServiceServer server;
    private HttpGoogleKeySource source;
    private Instant now;
    private String jwks;

    @BeforeEach
    void setUp() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        now = Instant.parse("2026-10-28T15:00:00Z");
        Clock clock = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now;
            }
        };
        source = new HttpGoogleKeySource(builder.build(), clock, URL);

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        RSAPublicKey key = (RSAPublicKey) generator.generateKeyPair().getPublic();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        jwks = """
                {"keys": [{"kty": "RSA", "alg": "RS256", "use": "sig", "kid": "chave-1", "n": "%s", "e": "%s"}]}
                """.formatted(encoder.encodeToString(unsigned(key.getModulus().toByteArray())),
                encoder.encodeToString(unsigned(key.getPublicExponent().toByteArray())));
    }

    @Test
    void keyFor_loadsTheKeysFromGoogle() {
        expectFetch(3600);

        assertNotNull(source.keyFor("chave-1"));
        server.verify();
    }

    @Test
    void keyFor_cachesThemForTheMaxAge() {
        expectFetch(3600);

        source.keyFor("chave-1");
        now = now.plus(Duration.ofMinutes(59));
        assertNotNull(source.keyFor("chave-1"));

        server.verify();
    }

    @Test
    void keyFor_loadsThemAgainAfterTheMaxAge() {
        expectFetch(600);
        expectFetch(600);

        source.keyFor("chave-1");
        now = now.plus(Duration.ofMinutes(11));
        source.keyFor("chave-1");

        server.verify();
    }

    @Test
    void keyFor_anUnknownKeyId_triggersOneRefetchAtMostPerMinute() {
        expectFetch(3600);
        expectFetch(3600);

        assertNull(source.keyFor("rotacionada"));
        assertNull(source.keyFor("rotacionada"));
        now = now.plus(Duration.ofSeconds(61));
        assertNull(source.keyFor("rotacionada"));

        server.verify();
    }

    @Test
    void keyFor_whenGoogleIsUnreachable_failsAsAnInvalidToken() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(InvalidGoogleTokenException.class, () -> source.keyFor("chave-1"));
        server.verify();
    }

    private void expectFetch(int maxAgeSeconds) {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jwks, MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=" + maxAgeSeconds + ", must-revalidate"));
    }

    // BigInteger.toByteArray() devolve um byte de sinal à esquerda que o JWK não quer.
    private static byte[] unsigned(byte[] bytes) {
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }
}
