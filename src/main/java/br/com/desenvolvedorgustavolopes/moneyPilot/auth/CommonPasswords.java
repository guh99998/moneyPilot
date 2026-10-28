package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Senhas de listas públicas de senhas vazadas e padrões óbvios (repetição, sequência, linha de teclado). A lista é o
 * mesmo arquivo que o cliente web baixa para o medidor de força, então servidor e tela usam uma fonte só.
 */
@Component
public class CommonPasswords {

    static final String RESOURCE = "static/data/common-passwords.txt";

    private static final List<String> KEYBOARD_ROWS = List.of("qwertyuiop", "asdfghjkl", "zxcvbnm");

    private final Set<String> common;

    public CommonPasswords() {
        this.common = load();
    }

    /** Compara a senha inteira, sem diferença de maiúsculas. */
    public boolean isCommon(String password) {
        return common.contains(password.toLowerCase(Locale.ROOT));
    }

    /**
     * Um caractere repetido ({@code aaaaaaaaaa}), dígitos ou letras em sequência crescente ou decrescente
     * ({@code 1234567890}, {@code abcdefghij}) ou um trecho de linha de teclado ({@code qwertyuiop}).
     */
    public boolean isSequenceOrRepetition(String password) {
        String lower = password.toLowerCase(Locale.ROOT);

        return isRepetition(lower) || isRun(lower) || isKeyboardRow(lower);
    }

    private static boolean isRepetition(String password) {
        return password.codePoints().distinct().count() == 1;
    }

    private static boolean isRun(String password) {
        if (!password.chars().allMatch(c -> isAsciiDigit(c) || isAsciiLetter(c))) {
            return false;
        }

        boolean digits = password.chars().allMatch(CommonPasswords::isAsciiDigit);
        boolean letters = password.chars().allMatch(CommonPasswords::isAsciiLetter);
        if (!digits && !letters) {
            return false;
        }

        return steps(password, 1, digits) || steps(password, -1, digits);
    }

    // Os dígitos dão a volta (…890123…): 1234567890 e 7890123456 são sequências; as letras não.
    private static boolean steps(String password, int step, boolean wrapsAround) {
        for (int i = 1; i < password.length(); i++) {
            int delta = password.charAt(i) - password.charAt(i - 1);
            boolean expected = delta == step || (wrapsAround && Math.floorMod(delta, 10) == Math.floorMod(step, 10));
            if (!expected) {
                return false;
            }
        }
        return true;
    }

    private static boolean isKeyboardRow(String password) {
        return KEYBOARD_ROWS.stream().anyMatch(row ->
                row.contains(password) || new StringBuilder(row).reverse().toString().contains(password));
    }

    private static boolean isAsciiDigit(int c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isAsciiLetter(int c) {
        return c >= 'a' && c <= 'z';
    }

    private static Set<String> load() {
        Set<String> passwords = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty() && !line.startsWith("#")) {
                    passwords.add(line.toLowerCase(Locale.ROOT));
                }
            }
        } catch (IOException e) {
            // Sem a lista a regra de senha comum ficaria desligada em silêncio: melhor não subir.
            throw new UncheckedIOException("Could not read the common passwords list " + RESOURCE, e);
        }

        return Set.copyOf(passwords);
    }
}
