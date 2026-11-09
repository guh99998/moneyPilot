import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll, findByTag } from './fake-dom.mjs';

installFakeDom();

const {
    renderInvoices, payableThrough, payInvoicePayload, installmentPayload, purchasePayload,
    selectInvoiceCard, openPayInvoiceDialog, INVOICE_STATUS, installmentPreview
} = await import('../../main/resources/static/js/views/invoices.js');

const page = (content) => ({ content, page: { number: 0, totalPages: 1 }, totalPages: 1, number: 0 });

const inv = (dueDate, status, total, paid = '0.00', extra = {}) => ({
    periodStart: '2026-01-10', periodEnd: '2026-02-09', closingDate: '2026-02-10', dueDate, status,
    total, paid, remaining: (Number(total) - Number(paid)).toFixed(2), ...extra
});

const NUBANK_INVOICES = [
    inv('2026-02-17', 'OVERDUE', '120.00', '100.00'),
    inv('2026-03-17', 'CLOSED', '200.00'),
    inv('2026-04-17', 'OPEN', '50.50', '0.00', { closingDate: '2026-04-10' }),
    inv('2026-05-17', 'FUTURE', '90.00')
];
const INTER_INVOICES = [inv('2026-03-12', 'PAID', '80.00', '80.00'), inv('2026-04-12', 'OPEN', '300.00', '0.00', { closingDate: '2026-04-05' })];

const CHECKING = { id: 1, name: 'Corrente', type: 'CHECKING' };
const NUBANK = { id: 7, name: 'Nubank', type: 'CREDIT_CARD', closingDay: 10, dueDay: 17, invoicesFrom: '2026-01-10' };
const INTER = { id: 9, name: 'Inter', type: 'CREDIT_CARD', closingDay: 5, dueDay: 12, invoicesFrom: '2026-02-05' };
const NO_DAYS = { id: 8, name: 'Cartão sem dias', type: 'CREDIT_CARD' };

function mockApi({ accounts, invoices = { 7: NUBANK_INVOICES, 9: INTER_INVOICES }, recurring = [] }) {
    const calls = [];
    globalThis.fetch = async (url, options = {}) => {
        const path = new URL(url, 'http://localhost').pathname;
        calls.push(`${options.method || 'GET'} ${path}`);
        if (path === '/api/v1/accounts') return Response.json(page(accounts));
        const match = path.match(/^\/api\/v1\/accounts\/(\d+)\/invoices$/);
        if (match) return Response.json(invoices[match[1]] || []);
        if (path === '/api/v1/transactions') return Response.json(page([]));
        if (path === '/api/v1/recurring-purchases') return Response.json(page(recurring));
        if (path === '/api/v1/categories') return Response.json(page([]));
        return new Response(null, { status: 500 });
    };
    return calls;
}

const text = (root) => root.textContent.replace(/\s+/g, ' ');
const byClass = (root, name) => findAll(root, (node) => node.className.split(' ').includes(name));
const bodyRows = (view) => findAll(view, (node) => node.tagName === 'TR' && node.parent?.tagName === 'TBODY');
const dueDates = (view) => bodyRows(view).map((row) => findByTag(row, 'td')[0].textContent);
const buttonsOf = (root) => findByTag(root, 'button').map((button) => button.textContent);

test.beforeEach(() => selectInvoiceCard(''));

test('payableThrough sums what is left on the invoice and on every older one, in whole cents', () => {
    assert.equal(payableThrough(NUBANK_INVOICES, 0), '20.00');
    assert.equal(payableThrough(NUBANK_INVOICES, 1), '220.00');
    assert.equal(payableThrough(NUBANK_INVOICES, 2), '270.50');
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

test('purchasePayload makes an expense on the card', () => {
    const payload = purchasePayload({ categoryId: '3', description: 'Mercado', amount: '49.90', date: '2026-03-15' }, 7);

    assert.deepEqual(payload, {
        accountId: 7, categoryId: 3, amount: '49.90', type: 'EXPENSE', description: 'Mercado', date: '2026-03-15'
    });
});

test('every invoice status has a label and a badge kind', () => {
    for (const status of ['OPEN', 'CLOSED', 'OVERDUE', 'PAID', 'FUTURE']) {
        assert.ok(INVOICE_STATUS[status].label, status);
        assert.ok(INVOICE_STATUS[status].kind, status);
    }
});

test('openPayInvoiceDialog is exported for the payable list to reuse', () => {
    assert.equal(typeof openPayInvoiceDialog, 'function');
});

test('one list shows the invoices of every card, newest due date first', async () => {
    mockApi({ accounts: [CHECKING, NUBANK, INTER] });

    const view = await renderInvoices();

    assert.deepEqual(dueDates(view), [
        '17/05/2026', '17/04/2026', '12/04/2026', '17/03/2026', '12/03/2026', '17/02/2026'
    ]);
    const cards = bodyRows(view).map((row) => findByTag(row, 'td')[1].textContent);
    assert.ok(cards.some((cell) => cell.includes('Nubank')));
    assert.ok(cards.some((cell) => cell.includes('Inter')));
});

test('each row shows the situation, the total and what is left', async () => {
    mockApi({ accounts: [CHECKING, NUBANK] });

    const view = await renderInvoices();

    const overdue = bodyRows(view).find((row) => text(row).includes('17/02/2026'));
    assert.match(text(overdue), /Vencida/);
    assert.match(text(overdue), /R\$\s120,00/);
    assert.match(text(overdue), /R\$\s20,00/);
});

test('the summary counts what is due, what is overdue and the open invoices', async () => {
    mockApi({ accounts: [CHECKING, NUBANK, INTER] });

    const view = await renderInvoices();

    const summary = text(byClass(view, 'invoice-summary')[0]);
    assert.match(summary, /A pagar R\$\s570,50/);
    assert.match(summary, /Vencido R\$\s20,00/);
    assert.match(summary, /2 abertas/);
});

test('each card has its current invoice highlighted, with the date the invoices count from', async () => {
    mockApi({ accounts: [CHECKING, NUBANK, INTER] });

    const view = await renderInvoices();

    const highlights = byClass(view, 'invoice-current');
    assert.equal(highlights.length, 2);
    assert.match(text(highlights[0]), /Fatura atual · Nubank/);
    assert.match(text(highlights[0]), /R\$\s50,50/);
    assert.match(text(highlights[0]), /Vence em 17\/04\/2026/);
    assert.match(text(highlights[0]), /Faturas contam desde 10\/01\/2026/);
    assert.match(text(highlights[1]), /Fatura atual · Inter/);
    assert.match(text(highlights[1]), /Faturas contam desde 05\/02\/2026/);
});

test('each highlight offers a new purchase, an installment purchase and to pay', async () => {
    mockApi({ accounts: [CHECKING, NUBANK] });

    const view = await renderInvoices();

    const labels = buttonsOf(byClass(view, 'invoice-current')[0]);
    assert.ok(labels.includes('Nova compra'));
    assert.ok(labels.includes('Compra parcelada'));
    assert.ok(labels.includes('Pagar fatura'));
});

test('only invoices with something left to pay, and not future, offer to be paid', async () => {
    mockApi({ accounts: [CHECKING, NUBANK] });

    const view = await renderInvoices();

    const rows = bodyRows(view);
    const pay = (row) => buttonsOf(row).includes('Pagar fatura');
    assert.equal(pay(rows.find((row) => text(row).includes('17/05/2026'))), false, 'future');
    assert.equal(pay(rows.find((row) => text(row).includes('17/04/2026'))), true, 'open');
    assert.equal(pay(rows.find((row) => text(row).includes('17/03/2026'))), true, 'closed');
    assert.equal(pay(rows.find((row) => text(row).includes('17/02/2026'))), true, 'overdue');
});

test('every row can open the purchases of its cycle', async () => {
    mockApi({ accounts: [CHECKING, NUBANK] });

    const view = await renderInvoices();

    for (const row of bodyRows(view)) {
        assert.ok(findByTag(row, 'summary').some((node) => /Ver compras/.test(node.textContent)), text(row));
    }
});

test('the card filter narrows the list to that card', async () => {
    mockApi({ accounts: [CHECKING, NUBANK, INTER] });
    const view = await renderInvoices();

    const select = findByTag(view, 'select').find((node) => node.parent.textContent.includes('Cartão'));
    select.value = '9';
    await select.dispatch('change');

    assert.deepEqual(dueDates(view), ['12/04/2026', '12/03/2026']);
});

test('the status filter keeps only that situation', async () => {
    mockApi({ accounts: [CHECKING, NUBANK, INTER] });
    const view = await renderInvoices();

    const select = findByTag(view, 'select').find((node) => node.parent.textContent.includes('Situação'));
    select.value = 'OPEN';
    await select.dispatch('change');

    assert.deepEqual(dueDates(view), ['17/04/2026', '12/04/2026']);
});

test('clicking a column header sorts by it, and again reverses', async () => {
    mockApi({ accounts: [CHECKING, NUBANK, INTER] });
    const view = await renderInvoices();

    const header = () => findByTag(view, 'button').find((node) => node.className === 'sort-btn' && node.textContent.startsWith('Total'));
    await header().dispatch('click');
    const ascending = bodyRows(view).map((row) => findByTag(row, 'td')[3].textContent);
    await header().dispatch('click');
    const descending = bodyRows(view).map((row) => findByTag(row, 'td')[3].textContent);

    assert.match(ascending[0], /R\$\s50,50/);
    assert.match(descending[0], /R\$\s300,00/);
});

test('selectInvoiceCard opens the screen filtered on that card', async () => {
    mockApi({ accounts: [CHECKING, NUBANK, INTER] });
    selectInvoiceCard(9);

    const view = await renderInvoices();

    assert.deepEqual(dueDates(view), ['12/04/2026', '12/03/2026']);
    const select = findByTag(view, 'select').find((node) => node.parent.textContent.includes('Cartão'));
    assert.equal(select.value, '9');
});

test('invoices of every card are fetched, one request per card with days', async () => {
    const calls = mockApi({ accounts: [CHECKING, NUBANK, INTER, NO_DAYS] });

    await renderInvoices();

    assert.ok(calls.includes('GET /api/v1/accounts/7/invoices'));
    assert.ok(calls.includes('GET /api/v1/accounts/9/invoices'));
    assert.ok(!calls.some((call) => call.includes('/accounts/8/')), 'card without days');
});

test('without a card that has closing and due days, the screen explains how to set one up', async () => {
    mockApi({ accounts: [CHECKING, NO_DAYS] });

    const view = await renderInvoices();

    assert.match(text(view), /fechamento e o do vencimento/);
    const link = findByTag(view, 'a').find((anchor) => anchor.getAttribute('href') === '#/accounts');
    assert.ok(link, 'link to Contas');
    assert.equal(byClass(view, 'invoice-current').length, 0);
});

test('no invoice matching the filters shows an empty message, not a broken table', async () => {
    mockApi({ accounts: [CHECKING, NUBANK], invoices: { 7: NUBANK_INVOICES } });
    selectInvoiceCard(7);
    const view = await renderInvoices();
    const select = findByTag(view, 'select').find((node) => node.parent.textContent.includes('Situação'));

    select.value = 'PAID';
    await select.dispatch('change');

    assert.equal(bodyRows(view).length, 0);
    assert.match(text(view), /Nenhuma fatura com esses filtros/);
});

const STREAMING = {
    id: 3, accountId: 7, accountName: 'Nubank', categoryId: 3, categoryName: 'Lazer', description: 'Streaming', amount: '39.90',
    dayOfMonth: 20, startDate: '2026-01-20', endDate: null, active: true, generatedUntil: '2026-02-20'
};

test('each card highlight offers a recurring purchase next to the installment one', async () => {
    mockApi({ accounts: [CHECKING, NUBANK, INTER] });

    const view = await renderInvoices();

    const labels = buttonsOf(view);
    assert.equal(labels.filter((label) => label === 'Assinatura mensal').length, 2);
    assert.equal(labels.filter((label) => label === 'Compra parcelada').length, 2);
});

test('the recurring purchases of the cards are listed with edit, deactivate and delete', async () => {
    mockApi({ accounts: [CHECKING, NUBANK], recurring: [STREAMING] });

    const view = await renderInvoices();

    const section = byClass(view, 'recurring-purchases')[0];
    assert.match(text(section), /Streaming/);
    assert.match(text(section), /Nubank · todo dia 20/);
    assert.match(text(section), /R\$\s39,90/);
    const labels = buttonsOf(section);
    assert.ok(labels.includes('Editar') && labels.includes('Desativar') && labels.includes('Apagar'));
});

test('a deactivated recurring purchase has no deactivate button', async () => {
    mockApi({ accounts: [CHECKING, NUBANK], recurring: [{ ...STREAMING, active: false }] });

    const view = await renderInvoices();

    const section = byClass(view, 'recurring-purchases')[0];
    assert.match(text(section), /Desativada/);
    assert.ok(!buttonsOf(section).includes('Desativar'));
});

test('the invoice shows how much of the total is projected', async () => {
    const withProjection = [inv('2026-04-17', 'OPEN', '90.00', '0.00', { closingDate: '2026-04-10', projectedTotal: '39.90' })];
    mockApi({ accounts: [CHECKING, NUBANK], invoices: { 7: withProjection }, recurring: [STREAMING] });

    const view = await renderInvoices();

    assert.match(text(view), /Previsto: R\$\s39,90 em assinaturas mensais/);
    assert.match(text(bodyRows(view)[0]), /Inclui R\$\s39,90 previsto/);
});

test('installmentPayload sends only the total when the person typed the total', () => {
    const payload = installmentPayload({
        amountMode: 'total', categoryId: '3', description: 'Notebook', totalAmount: '1200.00', installmentAmount: null,
        installments: '6', currentInstallment: null, date: '2026-03-15'
    }, 7);

    assert.deepEqual(payload, {
        accountId: 7, categoryId: 3, description: 'Notebook', totalAmount: '1200.00', installments: 6, date: '2026-03-15'
    });
});

test('installmentPayload sends the installment amount and the current installment', () => {
    const payload = installmentPayload({
        amountMode: 'installment', categoryId: '3', description: 'TV', totalAmount: null, installmentAmount: '83.33',
        installments: '10', currentInstallment: '4', date: '2026-03-17'
    }, 7);

    assert.deepEqual(payload, {
        accountId: 7, categoryId: 3, description: 'TV', installmentAmount: '83.33', installments: 10, currentInstallment: 4, date: '2026-03-17'
    });
});

test('installmentPayload leaves the current installment out when it is empty', () => {
    const payload = installmentPayload({
        amountMode: 'installment', categoryId: '3', description: 'TV', installmentAmount: '83.33',
        installments: '10', currentInstallment: null, date: '2026-03-17'
    }, 7);

    assert.equal('currentInstallment' in payload, false);
});

test('installmentPreview says how many installments are launched, with the numbers and the start date', () => {
    const text = installmentPreview({ amountMode: 'installment', installmentAmount: '83.33', installments: '10', currentInstallment: '4', date: '2026-03-17' });

    assert.match(text, /7 parcelas de R\$\s83,33/);
    assert.match(text, /4\/10 a 10\/10/);
    assert.match(text, /17\/03\/2026/);
});

test('installmentPreview for the total shows the first installment carrying the leftover cent', () => {
    const text = installmentPreview({ amountMode: 'total', totalAmount: '100.00', installments: '3', date: '2026-03-17' });

    assert.match(text, /3 parcelas de R\$\s33,33/);
    assert.match(text, /primeira de R\$\s33,34/);
});

test('installmentPreview waits for the values', () => {
    assert.match(installmentPreview({ amountMode: 'installment', installmentAmount: '', installments: '10', date: '2026-03-17' }), /valor/i);
});
