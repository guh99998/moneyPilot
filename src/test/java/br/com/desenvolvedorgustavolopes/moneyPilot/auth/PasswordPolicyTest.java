package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.WeakPasswordException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    private void check(String password, String email, String name) {
        policy.checkPersonal("password", password, email, name);
    }

    @Test
    void refusesThePartOfTheNameWithoutAccentsOrCase() {
        WeakPasswordException e = assertThrows(WeakPasswordException.class,
                () -> check("meu amigo JOAO", "x@example.com", "João Ferreira"));

        assertEquals("password", e.getField());
        assertEquals(PasswordPolicy.PERSONAL_MESSAGE, e.getMessage());
    }

    @Test
    void refusesTheAccentedPasswordForAnUnaccentedName() {
        assertThrows(WeakPasswordException.class,
                () -> check("tudo sobre a LÚCIA", "x@example.com", "Lucia Prado"));
    }

    @Test
    void refusesTheWholeEmailAndTheLocalPart() {
        assertThrows(WeakPasswordException.class,
                () -> check("ola gustavo@example.com ola", "Gustavo@Example.com", "G"));
        assertThrows(WeakPasswordException.class,
                () -> check("xxGUSTAVOxx", "gustavo@example.com", "G"));
    }

    @Test
    void namePartsWithFewerThanFourLettersDoNotCount() {
        assertDoesNotThrow(() -> check("banana na janela", "x@example.com", "Ana Luz"));
    }

    @Test
    void aShortEmailLocalPartDoesNotCount_butTheWholeEmailStillDoes() {
        assertDoesNotThrow(() -> check("uma frase com jo no meio", "jo@example.com", "X"));
        assertThrows(WeakPasswordException.class,
                () -> check("uma frase jo@example.com", "jo@example.com", "X"));
    }

    @Test
    void splitsTheNameOnAnythingThatIsNotALetter() {
        assertThrows(WeakPasswordException.class,
                () -> check("meu-amigo-pedro", "x@example.com", "Ana-Pedro  da Silva"));
    }

    @Test
    void acceptsAnUnrelatedPhrase() {
        assertDoesNotThrow(() -> check("o céu está azul hoje", "maria.silva@example.com", "Maria Silva"));
    }

    @Test
    void toleratesMissingEmailOrName() {
        assertDoesNotThrow(() -> policy.checkPersonal("newPassword", "o céu está azul hoje", null, null));
        assertDoesNotThrow(() -> policy.checkPersonal("newPassword", "o céu está azul hoje", " ", ""));
    }
}
