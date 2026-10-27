package br.com.desenvolvedorgustavolopes.moneyPilot.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code publicUrl}: endereço público do app, base dos links enviados por e-mail. Sem barra final.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@NotBlank String publicUrl) {

    public AppProperties {
        if (publicUrl != null) {
            publicUrl = publicUrl.strip().replaceAll("/+$", "");
        }
    }
}
