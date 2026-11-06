import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll } from './fake-dom.mjs';

installFakeDom();

const { mergeUpcoming, upcomingCard } = await import('../../main/resources/static/js/views/dashboard.js');

const bill = (id, dueDate, extra = {}) => ({ id, dueDate, type: 'PAYABLE', amount: '10.00', description: `Título ${id}`, categoryName: 'Casa', ...extra });
const invoice = (accountId, dueDate, status, extra = {}) => ({
    accountId, accountName: 'Nubank', dueDate, status, closingDate: '2026-03-10', total: '200.00', paid: '0.00', remaining: '200.00', ...extra
});

const text = (root) => root.textContent.replace(/\s+/g, ' ');

test('mergeUpcoming mixes bills and invoices by due date, closest first', () => {
    const merged = mergeUpcoming(
        [bill(1, '2026-03-20'), bill(2, '2026-03-25')],
        [invoice(7, '2026-03-17', 'CLOSED'), invoice(7, '2026-04-17', 'OPEN')]
    );

    assert.deepEqual(merged.map((entry) => [entry.dueDate, entry.bill ? 'bill' : 'invoice']), [
        ['2026-03-17', 'invoice'],
        ['2026-03-20', 'bill'],
        ['2026-03-25', 'bill'],
        ['2026-04-17', 'invoice']
    ]);
});

test('mergeUpcoming keeps only the first six', () => {
    const bills = [1, 2, 3, 4, 5, 6].map((n) => bill(n, `2026-03-0${n}`));

    const merged = mergeUpcoming(bills, [invoice(7, '2026-03-02', 'CLOSED')]);

    assert.equal(merged.length, 6);
    assert.deepEqual(merged.map((entry) => entry.dueDate), ['2026-03-01', '2026-03-02', '2026-03-02', '2026-03-03', '2026-03-04', '2026-03-05']);
});

test('mergeUpcoming works with no invoices', () => {
    assert.equal(mergeUpcoming([bill(1, '2026-03-20')], []).length, 1);
    assert.deepEqual(mergeUpcoming([], []), []);
});

test('the card shows an invoice with the card name, the total and a link to Faturas', () => {
    const card = upcomingCard(mergeUpcoming([], [invoice(7, '2099-03-17', 'CLOSED')]));

    assert.match(text(card), /Fatura do Nubank/);
    assert.match(text(card), /A vencer/);
    assert.match(text(card), /R\$\s200,00/);
    const link = findAll(card, (node) => node.tagName === 'A' && node.textContent === 'Fatura do Nubank')[0];
    assert.equal(link.getAttribute('href'), '#/invoices');
});

test('an open invoice says it is open and when it closes; an overdue one is marked as late', () => {
    const open = upcomingCard(mergeUpcoming([], [invoice(7, '2099-04-17', 'OPEN')]));
    const late = upcomingCard(mergeUpcoming([], [invoice(7, '2000-02-17', 'OVERDUE')]));

    assert.match(text(open), /Aberta · fecha em 10\/03\/2026/);
    assert.match(text(late), /Vencido · a pagar/);
});

test('bills still render as before', () => {
    const card = upcomingCard(mergeUpcoming([bill(1, '2099-03-20', { installmentNumber: 2, installmentTotal: 6 })], []));

    assert.match(text(card), /Título 1/);
    assert.match(text(card), /Casa · 2\/6/);
});
