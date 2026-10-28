package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import java.util.Locale;

/**
 * E-mails are stored and looked up without surrounding spaces and in lower case,
 * so {@code Fulano@x.com} and {@code fulano@x.com} are the same account.
 */
final class EmailAddress {

    private EmailAddress() {
    }

    static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
