import { test } from 'node:test';
import assert from 'node:assert/strict';

import { billFiltersFor, destinationFor, isBudgetAlert, isTicketAlert } from '../../main/resources/static/js/alert-targets.js';

test('overdue bills alert opens A pagar e receber filtered by overdue and by its type', () => {
    const alert = { kind: 'BILLS_OVERDUE', billType: 'PAYABLE', count: 2, total: '150.00' };

    assert.deepEqual(billFiltersFor(alert, '2026-10-28'), { type: 'PAYABLE', status: 'OVERDUE' });
    assert.equal(destinationFor(alert), '#/bills');
});

test('due soon alert filters open bills from today up to the alert limit date', () => {
    const alert = { kind: 'BILLS_DUE_SOON', billType: 'RECEIVABLE', count: 3, total: '450.00', dueUntil: '2026-11-04' };

    assert.deepEqual(billFiltersFor(alert, '2026-10-28'), {
        type: 'RECEIVABLE', status: 'OPEN', dueDateFrom: '2026-10-28', dueDateTo: '2026-11-04'
    });
    assert.equal(destinationFor(alert), '#/bills');
});

test('budget alerts go to Orçamentos', () => {
    for (const kind of ['BUDGET_WARNING', 'BUDGET_EXCEEDED']) {
        const alert = { kind, categoryId: 3, month: 10, year: 2026 };
        assert.equal(isBudgetAlert(alert), true);
        assert.equal(destinationFor(alert), '#/budgets');
    }
    assert.equal(isBudgetAlert({ kind: 'BILLS_DUE_SOON' }), false);
});

test('ticket alerts go to Suporte', () => {
    const alert = { kind: 'SUPPORT_TICKET_UPDATED', ticketReference: 'K7P3XN', ticketStatus: 'RESOLVED' };

    assert.equal(isTicketAlert(alert), true);
    assert.equal(isBudgetAlert(alert), false);
    assert.equal(destinationFor(alert), '#/suporte');
    assert.equal(isTicketAlert({ kind: 'BILLS_OVERDUE' }), false);
});
