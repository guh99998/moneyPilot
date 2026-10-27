package br.com.desenvolvedorgustavolopes.moneyPilot.notification;

import br.com.desenvolvedorgustavolopes.moneyPilot.bill.BillType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Alerta de títulos preenche billType, count, total (e dueUntil nos a vencer); alerta de orçamento preenche
 * categoryId, categoryName, spent, budgeted, percentUsed, month e year.
 */
public record AlertResponse(
        String key,
        AlertKind kind,
        BillType billType,
        Long count,
        BigDecimal total,
        LocalDate dueUntil,
        Long categoryId,
        String categoryName,
        BigDecimal spent,
        BigDecimal budgeted,
        Integer percentUsed,
        Integer month,
        Integer year
) {
}
