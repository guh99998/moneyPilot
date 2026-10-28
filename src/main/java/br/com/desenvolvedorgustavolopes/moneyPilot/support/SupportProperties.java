package br.com.desenvolvedorgustavolopes.moneyPilot.support;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code email}: endereço que recebe os chamados. Vem do ambiente (SUPPORT_EMAIL), nunca do código.
 */
@Validated
@ConfigurationProperties(prefix = "app.support")
public record SupportProperties(@NotBlank String email) {
}
