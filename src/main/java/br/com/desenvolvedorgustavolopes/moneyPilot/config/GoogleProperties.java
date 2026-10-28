package br.com.desenvolvedorgustavolopes.moneyPilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code clientId}: client ID OAuth (Web) do Google. Não é segredo. Vazio desliga o login com Google.
 */
@ConfigurationProperties(prefix = "app.google")
public record GoogleProperties(String clientId) {

    public GoogleProperties {
        clientId = clientId == null ? "" : clientId.strip();
    }

    public boolean enabled() {
        return !clientId.isEmpty();
    }
}
