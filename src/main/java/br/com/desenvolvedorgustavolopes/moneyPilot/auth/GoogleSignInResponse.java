package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/** O e-mail é o da conta no app, que pode ser diferente do e-mail do Google. */
public record GoogleSignInResponse(@Schema(description = "Access token (JWT) para o cabeçalho Authorization: Bearer", example = "eyJhbGciOiJIUzI1NiJ9.exemplo.assinatura") String token, @Schema(description = "E-mail da conta", example = "ana@gmail.com") String email) {
}
