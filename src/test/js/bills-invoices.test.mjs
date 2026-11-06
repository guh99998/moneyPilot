import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll, findByTag } from './fake-dom.mjs';

installFakeDom();

const { invoiceMatchesFilters, loadInvoiceRows, invoiceRow } = await import('../../main/resources/static/js/views/bills-invoices.js');
const { renderInvoices } = await import('../../main/resources/static/js/views/invoices.js');

const OVERDUE = { accountId: 7, accountName: 'Nubank', periodStart: '2026-01-10', periodEnd: '2026-02-09', closingDate: '2026-02-10', dueDate: '2026-02-17', total: '120.00', paid: '100.00', remaining: '20.00', status: 'OVERDUE' };
const CLOSED = { accountId: 7, accountName: 'Nubank', periodStart: '2026-02-10', periodEnd: '2026-03-09', closingDate: '2026-03-10', dueDate: '2026-03-17', total: '200.00', paid: '0.00', remaining: '200.00', status: 'CLOSED' };
const OPEN = { accountId: 7, accountName: 'Nubank', periodStart: '2026-03-10', periodEnd: '2026-04-09', closingDate: '2026-04-10', dueDate: '2026-04-17', total: '50.50', paid: '0.00', remaining: '50.50', status: 'OPEN' };
const OTHER = { accountId: 9, accountName: 'Inter', periodStart: '2026-02-05', periodEnd: '2026-03-04', closingDate: '2026-03-05', dueDate: '2026-03-20', total: '80.00', paid: '0.00', remaining: '80.00', status: 'CLOSED' };
const ALL = [OVERDUE, CLOSED, OPEN, OTHER];

const DEFAULTS = { type: '', status: 'OPEN', categoryId: '', accountId: '', dueDateFrom: '', dueDateTo: '' };
const withFilters = (changes) => ({ ...DEFAULTS, ...changes });
const text = (root) => root.textContent.replace(/\s+/g, ' ');

function mockPayable(invoices = ALL) {
    const calls = [];
    globalThis.fetch = async (url) => {
        const path = new URL(url, 'http://localhost').pathname;
        calls.push(path);
        if (path === '/api/v1/invoices/payable') return Response.json(invoices);
        if (path === '/api/v1/accounts') return Response.json({ content: [{ id: 9, name: 'Inter', type: 'CREDIT_CARD', closingDay: 5, dueDay: 12 }], page: { number: 0, totalPages: 1 }, totalPages: 1, number: 0 });
        if (path.endsWith('/invoices')) return Response.json([]);
        return new Response(null, { status: 500 });
    };
    return calls;
}

test('with the default filters every invoice shows', () => {
    assert.deepEqual(ALL.filter((invoice) => invoiceMatchesFilters(invoice, DEFAULTS)), ALL);
});

test('only the "a pagar" type shows invoices', () => {
    assert.equal(invoiceMatchesFilters(OPEN, withFilters({ type: 'PAYABLE' })), true);
    assert.equal(invoiceMatchesFilters(OPEN, withFilters({ type: 'RECEIVABLE' })), false);
});

test('the overdue status shows only overdue invoices, and settled or canceled show none', () => {
    assert.deepEqual(ALL.filter((invoice) => invoiceMatchesFilters(invoice, withFilters({ status: 'OVERDUE' }))), [OVERDUE]);
    assert.equal(invoiceMatchesFilters(OPEN, withFilters({ status: '' })), true);
    assert.equal(invoiceMatchesFilters(OPEN, withFilters({ status: 'SETTLED' })), false);
    assert.equal(invoiceMatchesFilters(OPEN, withFilters({ status: 'CANCELED' })), false);
});

test('a category filter hides invoices, which have no category', () => {
    assert.equal(invoiceMatchesFilters(OPEN, withFilters({ categoryId: '3' })), false);
});

test('the account filter keeps only that card', () => {
    assert.deepEqual(ALL.filter((invoice) => invoiceMatchesFilters(invoice, withFilters({ accountId: '9' }))), [OTHER]);
    assert.equal(invoiceMatchesFilters(OPEN, withFilters({ accountId: '1' })), false);
});

test('the due date range keeps invoices that fall inside it, both ends included', () => {
    const march = withFilters({ dueDateFrom: '2026-03-17', dueDateTo: '2026-03-20' });

    assert.deepEqual(ALL.filter((invoice) => invoiceMatchesFilters(invoice, march)), [CLOSED, OTHER]);
});

test('loadInvoiceRows fetches on the first page and sums what is due through each invoice of a card', async () => {
    mockPayable();

    const { rows, failed } = await loadInvoiceRows(DEFAULTS, 0);

    assert.equal(failed, false);
    assert.deepEqual(rows.map((row) => [row.invoice.accountName, row.invoice.status, row.payAmount]), [
        ['Nubank', 'OVERDUE', '20.00'],
        ['Nubank', 'CLOSED', '220.00'],
        ['Nubank', 'OPEN', '270.50'],
        ['Inter', 'CLOSED', '80.00']
    ]);
});

test('loadInvoiceRows does not fetch past the first page or when the filters rule invoices out', async () => {
    const calls = mockPayable();

    assert.deepEqual((await loadInvoiceRows(DEFAULTS, 1)).rows, []);
    assert.deepEqual((await loadInvoiceRows(withFilters({ type: 'RECEIVABLE' }), 0)).rows, []);
    assert.equal(calls.length, 0);
});

test('a failure while loading invoices does not throw, it is reported', async () => {
    globalThis.fetch = async () => new Response(null, { status: 500 });

    const result = await loadInvoiceRows(DEFAULTS, 0);

    assert.deepEqual(result, { rows: [], failed: true });
});

test('the invoice row shows the card, the situation and the total, with no checkbox', () => {
    const tr = invoiceRow({ invoice: CLOSED, payAmount: '220.00' }, { onPay: () => {} });

    assert.equal(tr.tagName, 'TR');
    assert.match(text(tr), /Fatura do Nubank/);
    assert.match(text(tr), /A vencer/);
    assert.match(text(tr), /17\/03\/2026/);
    assert.match(text(tr), /R\$\s200,00/);
    assert.equal(findByTag(tr, 'input').length, 0);
});

test('an overdue invoice is marked overdue and shows what is left after a partial payment', () => {
    const tr = invoiceRow({ invoice: OVERDUE, payAmount: '20.00' }, { onPay: () => {} });

    assert.match(text(tr), /Vencida/);
    assert.match(text(tr), /R\$\s120,00/);
    assert.match(text(tr), /falta R\$\s20,00/);
    assert.ok(tr.className.split(' ').includes('is-overdue'));
});

test('the open invoice says it is in progress and when it closes', () => {
    const tr = invoiceRow({ invoice: OPEN, payAmount: '270.50' }, { onPay: () => {} });

    assert.match(text(tr), /Aberta/);
    assert.match(text(tr), /fecha em 10\/04\/2026, ainda recebe compras/);
});

test('"Pagar fatura" hands the row to the pay handler', async () => {
    const paid = [];
    const row = { invoice: CLOSED, payAmount: '220.00' };
    const tr = invoiceRow(row, { onPay: (picked) => paid.push(picked) });

    const button = findByTag(tr, 'button').find((node) => node.textContent === 'Pagar fatura');
    await button.dispatch('click');

    assert.deepEqual(paid, [row]);
});

test('"Ver itens da fatura" links to Faturas and opens it on that card', async () => {
    const calls = mockPayable();
    const tr = invoiceRow({ invoice: OTHER, payAmount: '80.00' }, { onPay: () => {} });

    const link = findAll(tr, (node) => node.tagName === 'A' && node.textContent === 'Ver itens da fatura')[0];
    assert.equal(link.getAttribute('href'), '#/invoices');
    await link.dispatch('click');
    await renderInvoices();

    assert.ok(calls.includes('/api/v1/accounts/9/invoices'), calls.join(', '));
});
