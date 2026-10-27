package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens opacos de uso único: o valor bruto vai para o usuário e só o hash SHA-256 é gravado.
 */
@Component
public class OpaqueTokens {

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        byte[] rawToken = new byte[32];

        secureRandom.nextBytes(rawToken);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(rawToken);
    }

    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytesToDigest = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytesToDigest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available in this JVM", e);
        }
    }
}
