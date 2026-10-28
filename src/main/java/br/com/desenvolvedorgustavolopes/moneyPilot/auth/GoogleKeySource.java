package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import java.security.Key;

/** De onde vêm as chaves públicas com que o Google assina os ID tokens. */
public interface GoogleKeySource {

    /** A chave pública do {@code kid}, ou {@code null} se o Google não a publica. */
    Key keyFor(String keyId);
}
