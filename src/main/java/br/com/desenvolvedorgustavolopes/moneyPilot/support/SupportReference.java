package br.com.desenvolvedorgustavolopes.moneyPilot.support;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Número curto do chamado: 6 caracteres sem os que se confundem ao ler (0/O, 1/I/L). */
@Component
public class SupportReference {

    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int LENGTH = 6;

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder reference = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            reference.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return reference.toString();
    }
}
