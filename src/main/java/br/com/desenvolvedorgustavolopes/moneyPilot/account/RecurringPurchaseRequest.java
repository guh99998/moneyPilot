package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringPurchaseRequest(
        @Schema(description = "Identificador do cartão de crédito, que precisa ter fechamento e vencimento", example = "1") @NotNull Long accountId,
        @Schema(description = "Identificador da categoria, de despesa", example = "3") @NotNull Long categoryId,
        @Schema(description = "Descrição livre", example = "Streaming de vídeo") @Size(max = 255) String description,
        @Schema(description = "Valor de cada cobrança em reais, número com 2 casas decimais e ponto decimal.", example = "39.90") @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @Schema(description = "Dia do mês da cobrança, de 1 a 31 (em mês mais curto vale o último dia)", example = "20") @NotNull @Min(1) @Max(31) Integer dayOfMonth,
        @Schema(description = "Início, no formato aaaa-mm-dd; no passado gera as cobranças desde essa data", example = "2026-11-01") @NotNull LocalDate startDate,
        @Schema(description = "Fim, no formato aaaa-mm-dd; vazio se não termina", example = "2027-10-31", nullable = true) LocalDate endDate
) {
}
