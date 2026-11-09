package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceResponse(
        @Schema(description = "Primeiro dia do ciclo (dia do fechamento anterior)", example = "2026-10-10") LocalDate periodStart,
        @Schema(description = "Último dia do ciclo (véspera do fechamento)", example = "2026-11-09") LocalDate periodEnd,
        @Schema(description = "Dia em que a fatura fecha. Compra neste dia cai na fatura seguinte.", example = "2026-11-10") LocalDate closingDate,
        @Schema(description = "Dia em que a fatura vence", example = "2026-11-17") LocalDate dueDate,
        @Schema(description = "Despesas do ciclo menos receitas (estornos). Transferências não entram. Valor em reais com 2 casas decimais.", example = "320.00") BigDecimal total,
        @Schema(description = "Quanto dos pagamentos (transferências para o cartão) foi abatido desta fatura, da mais antiga para a mais nova", example = "100.00") BigDecimal paid,
        @Schema(description = "Quanto falta pagar: total menos o pago, nunca negativo", example = "220.00") BigDecimal remaining,
        @Schema(description = "Situação da fatura", enumAsRef = true) InvoiceStatus status,
        @Schema(description = "Quanto do total é previsto: cobranças de compras recorrentes ainda não geradas, projetadas até o fim do ciclo seguinte ao atual. Já está somado ao total.", example = "39.90") BigDecimal projectedTotal
) {
}
