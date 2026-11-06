import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll, findByTag } from './fake-dom.mjs';

installFakeDom();

const {
    renderInvoices, payableThrough, payInvoicePayload, installmentPayload, INVOICE_STATUS
} = await import('../../main/resources/static/js/views/invoices.js');

const page = (content) => ({ content, page: { number: 0, totalPages: 1 }, totalPages: 1, number: 0 });

const INVOICES = [
    { periodStart: '2026-01-10', periodEnd: '2026-02-09', closingDate: '2026-02-10', dueDate: '2026-02-17', total: '120.00', paid: '100.00', remaining: '20.00', status: 'OVERDUE' },
    { periodStart: '2026-02-10', periodEnd: '2026-03-09', closingDate: '2026-03-10', dueDate: '2026-03-17', total: '200.00', paid: '0.00', remaining: '200.00', status: 'CLOSED' },
    { periodStart: '2026-03-10', periodEnd: '2026-04-09', closingDate: '2026-04-10', dueDate: '2026-04-17', total: '50.50', paid: '0.00', remaining: '50.50', status: 'OPEN' }
];

function mockApi({ accounts, invoices = INVOICES }) {
    const calls = [];
    globalThis.fetch = async (url, options = {}) => {
        const path = new URL(url, 'http://localhost').pathname;
        calls.push(`${options.method || 'GET'} ${path}`);
        if (path === '/api/v1/accounts') return Response.json(page(accounts));
        if (path.endsWith('/invoices')) return Response.json(invoices);
        if (path === '/api/v1/transactions') return Response.json(page([]));
        if (path === '/api/v1/categories') return Response.json(page([]));
        return new Response(null, { status: 500 });
    };
    return calls;
}

const CARD = { id: 7, name: 'Nubank', type: 'CREDIT_CARD', closingDay: 10, dueDay: 17 };
const CHECKING = { id: 1, name: 'Corrente', type: 'CHECKING' };
const text = (root) => root.textContent.replace(/\s+/g, ' ');
const byClass = (root, name) => findAll(root, (node) => node.className.split(' ').includes(name));

test('payableThrough sums what is left on the invoice and on every older one, in whole cents', () => {
    assert.equal(payableThrough(INVOICES, 0), '20.00');
    assert.equal(payableThrough(INVOICES, 1), '220.00');
    assert.equal(payableThrough(INVOICES, 2), '270.50');
    assert.equal(payableThrough([{ remaining: '0.10' }, { remaining: '0.20' }], 1), '0.30');
});

test('payInvoicePayload makes a transfer from the chosen account to the card', () => {
    const payload = payInvoicePayload({ fromAccountId: '1', amount: '220.00', date: '2026-03-15', description: null }, 7);

    assert.deepEqual(payload, {
        fromAccountId: 1, toAccountId: 7, amount: '220.00', date: '2026-03-15', description: 'Pagamento da fatura'
    });
});

test('installmentPayload turns the form into the installment purchase request', () => {
    const payload = installmentPayload({
        categoryId: '3', description: 'Notebook', totalAmount: '1200.00', installments: '6', date: '2026-03-15'
    }, 7);

    assert.deepEqual(payload, {
        accountId: 7, categoryId: 3, description: 'Notebook', totalAmount: '1200.00', installments: 6, date: '2026-03-15'
    });
});

test('every invoice status has a label and a badge kind', () => {
    for (const status of ['OPEN', 'CLOSED', 'OVERDUE', 'PAID', 'FUTURE']) {
        assert.ok(INVOICE_STATUS[status].label, status);
        assert.ok(INVOICE_STATUS[status].kind, status);
    }
});

test('the open invoice is the highlight, with its total and due date', async () => {
    mockApi({ accounts: [CHECKING, CARD] });

    const view = await renderInvoices();

    const highlight = byClass(view, 'invoice-current')[0];
    assert.ok(highlight, 'highlight card');
    assert.match(text(highlight), /Fatura atual/);
    assert.match(text(highlight), /R\$\s50,50/);
    assert.match(text(highlight), /Vence em 17\/04\/2026/);
    assert.match(text(highlight), /Fecha em 10\/04\/2026/);
});

test('the other invoices are listed with their status', async () => {
    mockApi({ accounts: [CHECKING, CARD] });

    const view = await renderInvoices();

    const listed = text(byClass(view, 'invoice-list')[0]);
    assert.match(listed, /Vencida/);
    assert.match(listed, /Fechada/);
    assert.match(listed, /R\$\s200,00/);
});

test('each invoice with something left offers to pay it, and the card offers an installment purchase', async () => {
    mockApi({ accounts: [CHECKING, CARD] });

    const view = await renderInvoices();

    const labels = findByTag(view, 'button').map((button) => button.textContent);
    assert.ok(labels.filter((label) => label === 'Pagar fatura').length >= 2, 'pay buttons');
    assert.ok(labels.includes('Compra parcelada'));
});

test('without a card that has closing and due days, the screen explains how to set one up', async () => {
    mockApi({ accounts: [CHECKING, { id: 8, name: 'Cartão sem dias', type: 'CREDIT_CARD' }] });

    const view = await renderInvoices();

    assert.match(text(view), /fechamento e o do vencimento/);
    const link = findByTag(view, 'a').find((anchor) => anchor.getAttribute('href') === '#/accounts');
    assert.ok(link, 'link to Contas');
    assert.equal(byClass(view, 'invoice-current').length, 0);
});

test('a future invoice does not offer to be paid yet', async () => {
    const future = { periodStart: '2026-05-10', periodEnd: '2026-06-09', closingDate: '2026-06-10', dueDate: '2026-06-17', total: '90.00', paid: '0.00', remaining: '90.00', status: 'FUTURE' };
    mockApi({ accounts: [CHECKING, CARD], invoices: [INVOICES[2], future] });

    const view = await renderInvoices();

    const labels = findByTag(view, 'button').map((button) => button.textContent);
    assert.equal(labels.filter((label) => label === 'Pagar fatura').length, 1, 'only the open invoice');
});
