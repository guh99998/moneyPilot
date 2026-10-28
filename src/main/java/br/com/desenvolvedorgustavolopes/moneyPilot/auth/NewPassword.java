package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Regras de uma senha nova (cadastro, troca e reset): de 8 a 64 caracteres. O bcrypt só aceita até 72 bytes,
 * então acentos e emoji, que ocupam mais de um byte, também contam.
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NewPasswordValidator.class)
public @interface NewPassword {

    String message() default "A senha precisa ter de 8 a 64 caracteres.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
