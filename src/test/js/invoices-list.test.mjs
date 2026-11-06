import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom } from './fake-dom.mjs';

installFakeDom();

const { buildRows, filterInvoices, sortInvoices, summarize, EMPTY_FILTERS } = await import('../../main/resources/static/js/views/invoices-list.js');

const NUBANK = { id: 7, name: 'Nubank', type: 'CREDIT_CARD', closingDay: 10, dueDay: 17, invoicesFrom: '2026-01-10' };
const INTER = { id: 9, name: 'Inter', type: 'CREDIT_CARD', closingDay: 5, dueDay: 12, invoicesFrom: '2026-02-05' };

const inv = (dueDate, status, total, paid = '0.00', extra = {}) => ({
    periodStart: '2026-01-10', periodEnd: '2026-02-09', closingDate: '2026-02-10', dueDate, status,
    total, paid, remaining: (Number(total) - Number(paid)).toFixed(2), ...extra
});

const BY_CARD = new Map([
    [7, [inv('2026-02-17', 'OVERDUE', '120.00', '100.00'), inv('2026-03-17', 'CLOSED', '200.00'), inv('2026-04-17', 'OPEN', '50.50'), inv('2026-05-17', 'FUTURE', '90.00')]],
    [9, [inv('2026-03-12', 'PAID', '80.00', '80.00'), inv('2026-04-12', 'OPEN', '300.00')]]
]);
const ROWS = buildRows([NUBANK, INTER], BY_CARD);

const ids = (rows) => rows.map((row) => `${row.card.name}:${row.invoice.dueDate}`);

test('buildRows makes one row per invoice, with the card and what it takes to pay through it', () => {
    assert.equal(ROWS.length, 6);
    const nubank = ROWS.filter((row) => row.card.id === 7);
    assert.deepEqual(nubank.map((row) => row.payAmount), ['20.00', '220.00', '270.50', '270.50']);
    assert.deepEqual(ROWS.filter((row) => row.card.id === 9).map((row) => row.payAmount), ['0.00', '300.00']);
});

test('the default filters keep every invoice', () => {
    assert.equal(filterInvoices(ROWS, EMPTY_FILTERS).length, 6);
});

test('the card filter keeps only that card', () => {
    assert.deepEqual(ids(filterInvoices(ROWS, { ...EMPTY_FILTERS, cardId: '9' })), ['Inter:2026-03-12', 'Inter:2026-04-12']);
});

test('the status filter keeps only that situation', () => {
    assert.deepEqual(ids(filterInvoices(ROWS, { ...EMPTY_FILTERS, status: 'OPEN' })), ['Nubank:2026-04-17', 'Inter:2026-04-12']);
    assert.deepEqual(ids(filterInvoices(ROWS, { ...EMPTY_FILTERS, status: 'PAID' })), ['Inter:2026-03-12']);
});

test('the due date range keeps both ends', () => {
    const march = { ...EMPTY_FILTERS, dueFrom: '2026-03-12', dueTo: '2026-03-17' };

    assert.deepEqual(ids(filterInvoices(ROWS, march)), ['Nubank:2026-03-17', 'Inter:2026-03-12']);
});

test('filters combine', () => {
    const rows = filterInvoices(ROWS, { cardId: '7', status: 'OPEN', dueFrom: '', dueTo: '' });

    assert.deepEqual(ids(rows), ['Nubank:2026-04-17']);
});

test('the default order is the due date, newest first', () => {
    const sorted = sortInvoices(ROWS, { key: 'dueDate', dir: 'desc' });

    assert.deepEqual(ids(sorted).slice(0, 3), ['Nubank:2026-05-17', 'Nubank:2026-04-17', 'Inter:2026-04-12']);
});

test('it sorts by total, by what is left, by card and by situation, both ways', () => {
    assert.deepEqual(sortInvoices(ROWS, { key: 'total', dir: 'desc' }).map((row) => row.invoice.total).slice(0, 2), ['300.00', '200.00']);
    assert.deepEqual(sortInvoices(ROWS, { key: 'total', dir: 'asc' }).map((row) => row.invoice.total).slice(0, 2), ['50.50', '80.00']);
    assert.deepEqual(sortInvoices(ROWS, { key: 'remaining', dir: 'desc' }).map((row) => row.invoice.remaining).slice(0, 2), ['300.00', '200.00']);
    assert.equal(sortInvoices(ROWS, { key: 'card', dir: 'asc' })[0].card.name, 'Inter');
    assert.equal(sortInvoices(ROWS, { key: 'status', dir: 'asc' })[0].invoice.status, 'OVERDUE');
});

test('sorting does not change the original list', () => {
    const before = ids(ROWS);

    sortInvoices(ROWS, { key: 'total', dir: 'asc' });

    assert.deepEqual(ids(ROWS), before);
});

test('summarize counts what is due, what is overdue and how many invoices are open, in whole cents', () => {
    const summary = summarize(ROWS);

    // a pagar: 20 + 200 + 50,50 (Nubank) + 300 (Inter); a futura e a paga ficam de fora
    assert.equal(summary.payableCents, 57050);
    assert.equal(summary.overdueCents, 2000);
    assert.equal(summary.overdueCount, 1);
    assert.equal(summary.openCount, 2);
});

test('summarize of nothing is all zeros', () => {
    assert.deepEqual(summarize([]), { payableCents: 0, overdueCents: 0, overdueCount: 0, openCount: 0 });
});
