package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonPasswordsTest {

    private final CommonPasswords passwords = new CommonPasswords();

    @ParameterizedTest
    @ValueSource(strings = {"aaaaaaaaaa", "AAAAAAAAAA", "1111111111", "          "})
    void repetitions(String password) {
        assertTrue(passwords.isSequenceOrRepetition(password));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890", "0123456789", "7890123456", "9876543210", "3210987654",
            "abcdefghij", "ABCDEFGHIJ", "jihgfedcba", "qrstuvwxyz"})
    void sequencesOfDigitsAndLetters(String password) {
        assertTrue(passwords.isSequenceOrRepetition(password));
    }

    @ParameterizedTest
    @ValueSource(strings = {"qwertyuiop", "QWERTYUIOP", "poiuytrewq", "asdfghjkl", "zxcvbnm"})
    void keyboardRows(String password) {
        assertTrue(passwords.isSequenceOrRepetition(password));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567891", "abcdefghik", "aaaaaaaaab", "qwertyuiopa", "12345abcde", "abcdcbaabc",
            "o céu está azul hoje", "13579acegi"})
    void everythingElseIsNotASequence(String password) {
        assertFalse(passwords.isSequenceOrRepetition(password));
    }

    @Test
    void lettersDoNotWrapAround() {
        assertFalse(passwords.isSequenceOrRepetition("xyzabcdefg"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"senha12345", "SENHA12345", "Senha12345", "1234567890", "qwertyuiop", "basketball"})
    void commonPasswordsIgnoreCase(String password) {
        assertTrue(passwords.isCommon(password));
    }

    @ParameterizedTest
    @ValueSource(strings = {"minha frase secreta", "minha senha12345 favorita", "senha123456789012"})
    void aCommonPasswordInsideALongerOneIsNotCommon(String password) {
        assertFalse(passwords.isCommon(password));
    }

    @Test
    void theListHeaderIsNotLoadedAsPasswords() {
        assertFalse(passwords.isCommon("# Senhas comuns recusadas pelo moneyPilot"));
        assertFalse(passwords.isCommon(""));
    }
}
