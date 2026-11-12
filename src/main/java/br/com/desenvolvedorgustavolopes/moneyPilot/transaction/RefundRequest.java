package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RefundRequest(
        @Schema(description = "Data do estorno, no formato aaaa-mm-dd. Cada parcela estornada usa a maior entre esta data e a data da própria parcela.", example = "2026-11-12") @NotNull LocalDate date,
        @Schema(description = "Alcance em compra parcelada (padrão ONLY_THIS). Fora de parcela só vale ONLY_THIS.", enumAsRef = true, nullable = true) RefundScope scope
) {
}
