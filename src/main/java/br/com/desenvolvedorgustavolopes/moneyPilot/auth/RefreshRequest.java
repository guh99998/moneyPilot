package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/** Refresh token no corpo, para clientes que não são navegador. */
public record RefreshRequest(@Schema(description = "Refresh token: vale 30 dias, guarde como uma senha. Cada uso gera um novo", example = "q3F9x2Zk1mT7vB8nR4sW6yH0aD5cE2gJ") String refreshToken) {
}
