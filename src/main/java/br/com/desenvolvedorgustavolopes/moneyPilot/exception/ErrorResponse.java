package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

public record ErrorResponse(@Schema(description = "Momento do erro (UTC)", example = "2026-10-28T12:00:00Z") Instant timestamp,
                            @Schema(description = "Código HTTP", example = "400") int status,
                            @Schema(description = "Texto do código HTTP", example = "Bad Request") String error,
                            @Schema(description = "Mensagem", example = "Não consigo ver meu saldo.") String message,
                            @Schema(description = "Mensagem de erro por campo, quando o erro é de validação") Map<String, String> fieldErrors) {
}
