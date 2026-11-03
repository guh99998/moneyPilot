package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/** Conta com senha informa {@code currentPassword}; conta só com Google informa {@code credential}. */
public record DeleteAccountRequest(
        @Schema(description = "Senha atual. Obrigatória em conta com senha; não vale em conta só com Google", example = "minha frase secreta", nullable = true)
        String currentPassword,

        @Schema(description = "Credencial (ID token) nova do Google, da conta vinculada. Obrigatória em conta só com Google", example = "eyJhbGciOiJSUzI1NiIs...", nullable = true)
        String credential) {
}
