package br.com.desenvolvedorgustavolopes.moneyPilot.config;

import br.com.desenvolvedorgustavolopes.moneyPilot.auth.EmailAddress;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * {@code emails}: e-mails das contas administradoras (ADMIN_EMAILS, separados por vírgula). Vem do ambiente, nunca
 * do código. Vazio ou ausente: ninguém é administrador.
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(List<String> emails) {

    public AdminProperties {
        emails = emails == null ? List.of() : emails.stream()
                .map(EmailAddress::normalize)
                .filter(email -> !email.isEmpty())
                .toList();
    }

    public boolean isAdminEmail(String email) {
        return email != null && emails.contains(EmailAddress.normalize(email));
    }
}
