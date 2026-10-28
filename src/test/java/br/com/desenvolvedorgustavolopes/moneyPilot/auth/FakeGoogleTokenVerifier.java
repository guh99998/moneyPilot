package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.InvalidGoogleTokenException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dublê de teste: traduz credenciais fabricadas pelo teste em {@link GoogleIdentity}. Credencial que o teste
 * não fabricou é recusada, como o Google recusaria um token inválido.
 */
@Component
@Primary
public class FakeGoogleTokenVerifier implements GoogleTokenVerifier {

    private final Map<String, GoogleIdentity> identities = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();

    /** Devolve a credencial que o "Google" aceitaria para essa identidade. */
    public String credentialFor(GoogleIdentity identity) {
        String credential = "fake-credential-" + sequence.incrementAndGet();
        identities.put(credential, identity);
        return credential;
    }

    @Override
    public GoogleIdentity verify(String credential) {
        GoogleIdentity identity = identities.get(credential);
        if (identity == null) {
            throw new InvalidGoogleTokenException();
        }
        return identity;
    }
}
