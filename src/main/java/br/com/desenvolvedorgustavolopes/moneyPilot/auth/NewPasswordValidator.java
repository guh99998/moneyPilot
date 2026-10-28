package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class NewPasswordValidator implements ConstraintValidator<NewPassword, String> {

    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 64;
    static final int MAX_BYTES = 72;

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
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("A senha é longa demais. Use menos acentos ou emoji.")
                    .addConstraintViolation();
            return false;
        }

        return true;
    }
}
