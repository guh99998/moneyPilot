// Para onde um alerta leva. Sem DOM nem imports: o mapeamento é testado direto no Node.

/** Filtros da tela A pagar e receber que reproduzem exatamente o que o alerta de títulos descreve. */
export function billFiltersFor(alert, today) {
    if (alert.kind === 'BILLS_OVERDUE') {
        return { type: alert.billType, status: 'OVERDUE' };
    }
    // A data limite do alerta é o maior vencimento entre os títulos, então a janela não traz nenhum a mais.
    return { type: alert.billType, status: 'OPEN', dueDateFrom: today, dueDateTo: alert.dueUntil };
}

export function isBudgetAlert(alert) {
    return alert.kind === 'BUDGET_WARNING' || alert.kind === 'BUDGET_EXCEEDED';
}

export function isTicketAlert(alert) {
    return alert.kind === 'SUPPORT_TICKET_UPDATED';
}

export function destinationFor(alert) {
    if (isTicketAlert(alert)) return '#/suporte';
    return isBudgetAlert(alert) ? '#/budgets' : '#/bills';
}
