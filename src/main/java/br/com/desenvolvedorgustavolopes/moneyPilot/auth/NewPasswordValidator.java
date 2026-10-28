package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
public class NewPasswordValidator implements ConstraintValidator<NewPassword, String> {

    static final int MIN_LENGTH = 10;
    static final int MAX_LENGTH = 64;
    static final int MAX_BYTES = 72;

    static final String LEAKED_MESSAGE =
            "Essa senha aparece em listas públicas de senhas vazadas na internet. Escolha outra, de preferência uma frase.";
    static final String SEQUENCE_MESSAGE =
            "Sequências e repetições, como 1234567890 ou aaaaaaaaaa, são fáceis de adivinhar. Escolha outra.";

    private final CommonPasswords commonPasswords;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        // Ausente ou em branco é com o @NotBlank.
        if (password == null || password.isBlank()) {
            return true;
        }

        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            return false;
        }

        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return refuse(context, "A senha é longa demais. Use menos acentos ou emoji.");
        }

        if (commonPasswords.isCommon(password)) {
            return refuse(context, LEAKED_MESSAGE);
        }

        if (commonPasswords.isSequenceOrRepetition(password)) {
            return refuse(context, SEQUENCE_MESSAGE);
        }

        return true;
    }

    private static boolean refuse(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
