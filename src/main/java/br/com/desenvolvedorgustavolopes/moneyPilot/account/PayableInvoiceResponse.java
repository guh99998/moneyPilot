package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PayableInvoiceResponse(
        @Schema(description = "Identificador do cartão de crédito", example = "2") Long accountId,
        @Schema(description = "Nome do cartão", example = "Nubank") String accountName,
        @Schema(description = "Primeiro dia do ciclo (dia do fechamento anterior)", example = "2026-10-10") LocalDate periodStart,
        @Schema(description = "Último dia do ciclo (véspera do fechamento)", example = "2026-11-09") LocalDate periodEnd,
        @Schema(description = "Dia em que a fatura fecha", example = "2026-11-10") LocalDate closingDate,
        @Schema(description = "Dia em que a fatura vence", example = "2026-11-17") LocalDate dueDate,
        @Schema(description = "Total da fatura: despesas menos receitas do ciclo. Valor em reais com 2 casas decimais.", example = "320.00") BigDecimal total,
        @Schema(description = "Quanto dos pagamentos já foi abatido desta fatura", example = "100.00") BigDecimal paid,
        @Schema(description = "Quanto falta pagar (sempre maior que zero nesta lista)", example = "220.00") BigDecimal remaining,
        @Schema(description = "Situação: aberta (ainda recebe compras), fechada ou vencida", enumAsRef = true) InvoiceStatus status
) {
}
