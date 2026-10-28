package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Regras de uma senha nova (cadastro, troca e reset): de 10 a 64 caracteres, fora das listas públicas de senhas
 * vazadas e sem ser só uma sequência ou repetição. O bcrypt só aceita até 72 bytes, então acentos e emoji, que
 * ocupam mais de um byte, também contam. A parte que depende do e-mail e do nome está em {@link PasswordPolicy}.
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NewPasswordValidator.class)
public @interface NewPassword {

    String message() default "A senha precisa ter de 10 a 64 caracteres.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
