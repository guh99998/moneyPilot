package br.com.desenvolvedorgustavolopes.moneyPilot.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.BillType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Alerta de títulos preenche billType, count, total (e dueUntil nos a vencer); alerta de orçamento preenche
 * categoryId, categoryName, spent, budgeted, percentUsed, month e year.
 */
public record AlertResponse(
        @Schema(description = "Chave do alerta", example = "PAYABLE_OVERDUE") String key,
        @Schema(description = "Tipo do alerta", enumAsRef = true) AlertKind kind,
        @Schema(description = "Tipo das contas, se o alerta for de contas", enumAsRef = true) BillType billType,
        @Schema(description = "Quantidade de itens do alerta", example = "3") Long count,
        @Schema(description = "Total das contas do alerta. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "320.00") BigDecimal total,
        @Schema(description = "Vencimento mais distante entre as contas do alerta, aaaa-mm-dd", example = "2026-11-05") LocalDate dueUntil,
        @Schema(description = "Categoria do orçamento, se o alerta for de orçamento", example = "3") Long categoryId,
        @Schema(description = "Nome da categoria, se o alerta for de orçamento", example = "Mercado") String categoryName,
        @Schema(description = "Gasto no mês, se o alerta for de orçamento. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "640.00") BigDecimal spent,
        @Schema(description = "Limite, se o alerta for de orçamento. Valor em reais, número com 2 casas decimais e ponto decimal.", example = "800.00") BigDecimal budgeted,
        @Schema(description = "Percentual do limite gasto, se o alerta for de orçamento", example = "80") Integer percentUsed,
        @Schema(description = "Mês do orçamento, se o alerta for de orçamento", example = "10") Integer month,
        @Schema(description = "Ano do orçamento, se o alerta for de orçamento", example = "2026") Integer year
) {
}
