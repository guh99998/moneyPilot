package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.InvalidGoogleTokenException;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.Jwks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.security.Key;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Busca o JWK Set do Google e guarda em cache pelo {@code max-age} da resposta. Um {@code kid} desconhecido
 * força uma busca nova (rotação de chaves), no máximo uma por minuto.
 */
@Slf4j
@Component
public class HttpGoogleKeySource implements GoogleKeySource {

    static final String CERTS_URL = "https://www.googleapis.com/oauth2/v3/certs";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_MAX_AGE = Duration.ofHours(1);
    private static final Duration MIN_REFETCH = Duration.ofMinutes(1);
    private static final Pattern MAX_AGE = Pattern.compile("max-age=(\\d+)");

    private final RestClient client;
    private final Clock clock;
    private final String url;

    private Map<String, Key> keys = Map.of();
    private Instant expiresAt = Instant.MIN;
    private Instant fetchedAt = Instant.MIN;

    @Autowired
    public HttpGoogleKeySource(Clock clock) {
        // RestClient.Builder não é um bean neste projeto: o cliente é montado aqui, com os timeouts.
        this(RestClient.builder().requestFactory(timeoutFactory()).build(), clock, CERTS_URL);
    }

    HttpGoogleKeySource(RestClient client, Clock clock, String url) {
        this.client = client;
        this.clock = clock;
        this.url = url;
    }

    @Override
    public synchronized Key keyFor(String keyId) {
        Instant now = clock.instant();

        if (!now.isBefore(expiresAt)) {
            fetch(now);
        } else if (!keys.containsKey(keyId) && !now.isBefore(fetchedAt.plus(MIN_REFETCH))) {
            fetch(now);
        }

        return keys.get(keyId);
    }

    private void fetch(Instant now) {
        try {
            ResponseEntity<String> response = client.get().uri(url).retrieve().toEntity(String.class);
            Map<String, Key> fresh = new HashMap<>();

            for (Jwk<?> jwk : Jwks.setParser().build().parse(response.getBody()).getKeys()) {
                if (jwk.getId() != null) {
                    fresh.put(jwk.getId(), jwk.toKey());
                }
            }

            keys = fresh;
            fetchedAt = now;
            expiresAt = now.plus(maxAge(response.getHeaders().getCacheControl()));
        } catch (RuntimeException e) {
            log.error("Could not load the Google signing keys: {}", e.getClass().getSimpleName());
            throw new InvalidGoogleTokenException();
        }
    }

    private static Duration maxAge(String cacheControl) {
        if (cacheControl == null) {
            return DEFAULT_MAX_AGE;
        }
        Matcher matcher = MAX_AGE.matcher(cacheControl);
        return matcher.find() ? Duration.ofSeconds(Long.parseLong(matcher.group(1))) : DEFAULT_MAX_AGE;
    }

    private static JdkClientHttpRequestFactory timeoutFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}
