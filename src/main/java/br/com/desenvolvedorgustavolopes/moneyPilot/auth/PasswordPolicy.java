package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.WeakPasswordException;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;

/**
 * Parte da regra de senha nova que depende de quem a escolhe: o e-mail e o nome da pessoa. O resto (tamanho, lista
 * de senhas vazadas, sequências) não depende de ninguém e fica em {@link NewPasswordValidator}.
 */
@Component
public class PasswordPolicy {

    static final String PERSONAL_MESSAGE = "A senha não pode conter seu e-mail nem seu nome.";

    // Partes menores que isso (Ana, Léo, "jo@") apareceriam por acaso em muitas senhas.
    static final int MIN_PART_LENGTH = 4;

    /** @throws WeakPasswordException se a senha contém o e-mail, a parte antes do {@code @} ou uma parte do nome. */
    public void checkPersonal(String field, String password, String email, String name) {
        String normalizedPassword = normalize(password);

        if (containsAny(normalizedPassword, emailParts(email)) || containsAny(normalizedPassword, nameParts(name))) {
            throw new WeakPasswordException(field, PERSONAL_MESSAGE);
        }
    }

    private static String[] emailParts(String email) {
        if (email == null || email.isBlank()) {
            return new String[0];
        }

        String normalized = normalize(email);
        int at = normalized.indexOf('@');
        String localPart = at < 0 ? normalized : normalized.substring(0, at);

        // O e-mail inteiro vale sempre; a parte local só quando é longa o bastante para não aparecer por acaso.
        return localPart.length() >= MIN_PART_LENGTH
                ? new String[]{normalized, localPart}
                : new String[]{normalized};
    }

    private static String[] nameParts(String name) {
        if (name == null) {
            return new String[0];
        }

        return Arrays.stream(normalize(name).split("[^\\p{L}]+"))
                .filter(part -> part.length() >= MIN_PART_LENGTH)
                .toArray(String[]::new);
    }

    private static boolean containsAny(String password, String[] parts) {
        return Arrays.stream(parts).anyMatch(password::contains);
    }

    // Sem acentos e sem diferença de maiúsculas: "JOÃO" e "joao" são a mesma coisa.
    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
