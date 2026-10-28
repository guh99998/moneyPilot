package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/** {@code clientId} vazio significa que o login com Google está desligado. */
public record GoogleConfigResponse(@Schema(description = "Client id do Google", example = "1234567890-exemplo.apps.googleusercontent.com") String clientId) {
}
