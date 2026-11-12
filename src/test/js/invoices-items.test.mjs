import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll, findByTag } from './fake-dom.mjs';

installFakeDom();

const {
    itemActions, itemBadges, editPayload, editScope, refundPayload, refundPreview, deleteMessage
} = await import('../../main/resources/static/js/views/invoices-items.js');
const { renderInvoices, selectInvoiceCard } = await import('../../main/resources/static/js/views/invoices.js');

const purchase = (extra = {}) => ({
    id: 10, accountId: 7, categoryId: 3, categoryName: 'Lazer', type: 'EXPENSE', description: 'Notebook',
    amount: '83.33', date: '2026-04-12', ...extra
});
const installment = (extra = {}) => purchase({ installmentGroupId: 'g-1', installmentNumber: 4, installmentTotal: 10, ...extra });

test('an expense on the card can be edited, refunded and deleted', () => {
    assert.deepEqual(itemActions(purchase()), ['edit', 'refund', 'delete']);
    assert.deepEqual(itemActions(installment()), ['edit', 'refund', 'delete']);
});

test('an income on the card can be edited and deleted, but not refunded', () => {
    assert.deepEqual(itemActions(purchase({ type: 'INCOME' })), ['edit', 'delete']);
});

test('a refunded purchase can only have the refund undone', () => {
    assert.deepEqual(itemActions(purchase({ refundTransactionId: 15 })), ['undoRefund']);
});

test('payments, invoice settlements and refunds have no actions', () => {
    assert.deepEqual(itemActions(purchase({ type: 'INCOME', transferGroupId: 't-1' })), []);
    assert.deepEqual(itemActions(purchase({ billId: 5 })), []);
    assert.deepEqual(itemActions(purchase({ type: 'INCOME', refundOfTransactionId: 10 })), []);
});

test('the badges tell the refund from the refunded purchase', () => {
    assert.equal(itemBadges(purchase()).length, 0);
    assert.match(itemBadges(purchase({ refundTransactionId: 15 }))[0].textContent, /Estornada/);
    assert.match(itemBadges(purchase({ type: 'INCOME', refundOfTransactionId: 10 }))[0].textContent, /Estorno/);
});

test('editPayload keeps the account and the type of the purchase', () => {
    const payload = editPayload(purchase(), { categoryId: '4', amount: '90.00', description: 'Notebook novo', date: '2026-04-13' });

    assert.deepEqual(payload, {
        accountId: 7, type: 'EXPENSE', categoryId: 4, amount: '90.00', description: 'Notebook novo', date: '2026-04-13'
    });
});

test('the edit scope only goes with an installment', () => {
    assert.equal(editScope(purchase(), { scope: 'THIS_AND_FOLLOWING' }), undefined);
    assert.equal(editScope(installment(), { scope: 'THIS_AND_FOLLOWING' }), 'THIS_AND_FOLLOWING');
    assert.equal(editScope(installment(), {}), 'ONLY_THIS');
});

test('refundPayload sends the date, and the scope only for an installment', () => {
    assert.deepEqual(refundPayload(purchase(), { date: '2026-11-12', scope: 'WHOLE_PURCHASE' }), { date: '2026-11-12' });
    assert.deepEqual(refundPayload(installment(), { date: '2026-11-12', scope: 'WHOLE_PURCHASE' }), { date: '2026-11-12', scope: 'WHOLE_PURCHASE' });
    assert.deepEqual(refundPayload(installment(), { date: '2026-11-12' }), { date: '2026-11-12', scope: 'ONLY_THIS' });
});

test('refundPreview says how many installments are refunded and the date rule', () => {
    assert.match(refundPreview(purchase(), { date: '2026-11-12' }), /receita de R\$\s83,33 no cartão em 12\/11\/2026/);
    assert.match(refundPreview(installment(), { date: '2026-11-12', scope: 'ONLY_THIS' }), /a parcela 4\/10/);
    assert.match(refundPreview(installment(), { date: '2026-11-12', scope: 'THIS_AND_FOLLOWING' }), /7 parcelas, de 4\/10 a 10\/10/);
    assert.match(refundPreview(installment(), { date: '2026-11-12', scope: 'WHOLE_PURCHASE' }), /10 parcelas, de 1\/10 a 10\/10/);
    assert.match(refundPreview(installment(), { date: '2026-11-12', scope: 'WHOLE_PURCHASE' }), /na da parcela, a que for mais tarde/);
});

test('refundPreview waits for the date', () => {
    assert.match(refundPreview(purchase(), { date: '' }), /Informe a data/);
});

test('the delete message warns that an installment removes the whole group and suggests the refund', () => {
    assert.match(deleteMessage(installment()), /parcela 4 de 10\. Todas as 10 parcelas serão apagadas juntas/);
    assert.match(deleteMessage(installment()), /prefira estornar/);
    assert.match(deleteMessage(purchase()), /prefira estornar/);
});

// ---- na tela Faturas ----

const page = (content) => ({ content, page: { number: 0, totalPages: 1 }, totalPages: 1, number: 0 });
const NUBANK = { id: 7, name: 'Nubank', type: 'CREDIT_CARD', closingDay: 10, dueDay: 17, invoicesFrom: '2026-01-10' };
const INVOICE = {
    periodStart: '2026-03-10', periodEnd: '2026-04-09', closingDate: '2026-04-10', dueDate: '2026-04-17', status: 'OPEN',
    total: '83.33', paid: '0.00', remaining: '83.33'
};

function mockApi(transactions) {
    const calls = [];
    globalThis.fetch = async (url, options = {}) => {
        const parsed = new URL(url, 'http://localhost');
        const path = parsed.pathname;
        calls.push({ method: options.method || 'GET', path, search: parsed.search, body: options.body ? JSON.parse(options.body) : null });
        if (path === '/api/v1/accounts') return Response.json(page([NUBANK]));
        if (path === '/api/v1/accounts/7/invoices') return Response.json([INVOICE]);
        if (path === '/api/v1/transactions' && (options.method || 'GET') === 'GET') return Response.json(page(transactions));
        if (path === '/api/v1/recurring-purchases') return Response.json(page([]));
        if (path === '/api/v1/categories') return Response.json(page([{ id: 3, name: 'Lazer', type: 'EXPENSE' }, { id: 4, name: 'Casa', type: 'EXPENSE' }]));
        if (path.startsWith('/api/v1/transactions')) return Response.json([], { status: options.method === 'DELETE' ? 204 : 201 });
        return new Response(null, { status: 500 });
    };
    return calls;
}

const text = (root) => root.textContent.replace(/\s+/g, ' ');
const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

async function openPurchases(transactions) {
    const calls = mockApi(transactions);
    selectInvoiceCard('');
    const view = await renderInvoices();
    const details = findAll(view, (node) => node.className === 'invoice-purchases')[0];
    details.open = true;
    await details.dispatch('toggle');
    await flush();
    return { view, calls };
}

const lines = (view) => findAll(view, (node) => node.className === 'invoice-purchase');
const buttonLabels = (line) => findByTag(line, 'button').map((button) => button.textContent);

test('each purchase line offers its actions, and payments and refunds offer none', async () => {
    const { view } = await openPurchases([
        purchase(),
        purchase({ id: 11, description: 'Pagamento', type: 'INCOME', transferGroupId: 't-1' }),
        purchase({ id: 12, description: 'Estorno: TV', type: 'INCOME', refundOfTransactionId: 9 }),
        purchase({ id: 9, description: 'TV', refundTransactionId: 12 })
    ]);

    const [normal, payment, refund, refunded] = lines(view);
    assert.deepEqual(buttonLabels(normal), ['Editar', 'Estornar', 'Apagar']);
    assert.deepEqual(buttonLabels(payment), []);
    assert.deepEqual(buttonLabels(refund), []);
    assert.match(text(refund), /Estorno/);
    assert.deepEqual(buttonLabels(refunded), ['Desfazer estorno']);
    assert.match(text(refunded), /Estornada/);
});

test('every action names the purchase it acts on, for screen readers', async () => {
    const { view } = await openPurchases([purchase()]);

    const labels = findByTag(lines(view)[0], 'button').map((button) => button.getAttribute('aria-label'));

    assert.deepEqual(labels, ['Editar: Notebook', 'Estornar: Notebook', 'Apagar: Notebook']);
});

test('refunding an installment posts the date and the scope to the refund route and reloads the invoices', async () => {
    const { view, calls } = await openPurchases([installment()]);

    await findByTag(lines(view)[0], 'button').find((button) => button.textContent === 'Estornar').dispatch('click');
    const dialog = findAll(globalThis.document.body, (node) => node.tagName === 'DIALOG').at(-1);
    assert.match(text(dialog), /Estornar compra/);
    const select = findByTag(dialog, 'select')[0];
    select.value = 'THIS_AND_FOLLOWING';
    await select.dispatch('change');
    await findByTag(dialog, 'form')[0].dispatch('submit');
    await flush();

    const refund = calls.find((call) => call.path === '/api/v1/transactions/10/refund');
    assert.equal(refund.method, 'POST');
    assert.equal(refund.body.scope, 'THIS_AND_FOLLOWING');
    assert.match(refund.body.date, /^\d{4}-\d{2}-\d{2}$/);
    assert.equal(calls.filter((call) => call.path === '/api/v1/accounts/7/invoices').length, 2, 'invoices reloaded');
});

test('undoing a refund deletes the refund transaction, not the purchase', async () => {
    const { view, calls } = await openPurchases([purchase({ id: 9, description: 'TV', refundTransactionId: 12 })]);

    await findByTag(lines(view)[0], 'button')[0].dispatch('click');
    const dialog = findAll(globalThis.document.body, (node) => node.tagName === 'DIALOG').at(-1);
    await findByTag(dialog, 'button').find((button) => button.textContent === 'Desfazer estorno').dispatch('click');
    await flush();

    assert.ok(calls.some((call) => call.method === 'DELETE' && call.path === '/api/v1/transactions/12'));
    assert.ok(!calls.some((call) => call.method === 'DELETE' && call.path === '/api/v1/transactions/9'));
});

test('deleting an installment removes the whole group', async () => {
    const { view, calls } = await openPurchases([installment()]);

    await findByTag(lines(view)[0], 'button').find((button) => button.textContent === 'Apagar').dispatch('click');
    const dialog = findAll(globalThis.document.body, (node) => node.tagName === 'DIALOG').at(-1);
    assert.match(text(dialog), /Todas as 10 parcelas serão apagadas juntas/);
    await findByTag(dialog, 'button').find((button) => button.textContent === 'Apagar').dispatch('click');
    await flush();

    assert.ok(calls.some((call) => call.method === 'DELETE' && call.path === '/api/v1/transactions/installments/g-1'));
});

test('editing an installment sends the scope as a query parameter and keeps the account and type', async () => {
    const { view, calls } = await openPurchases([installment()]);

    await findByTag(lines(view)[0], 'button').find((button) => button.textContent === 'Editar').dispatch('click');
    await flush();
    const dialog = findAll(globalThis.document.body, (node) => node.tagName === 'DIALOG').at(-1);
    assert.match(text(dialog), /Editar parcela/);
    const scope = findByTag(dialog, 'select').find((select) => select.parent.textContent.includes('Aplicar a'));
    scope.value = 'THIS_AND_FOLLOWING';
    await scope.dispatch('change');
    await findByTag(dialog, 'form')[0].dispatch('submit');
    await flush();

    const update = calls.find((call) => call.method === 'PUT');
    assert.equal(update.path, '/api/v1/transactions/10');
    assert.equal(update.search, '?scope=THIS_AND_FOLLOWING');
    assert.equal(update.body.accountId, 7);
    assert.equal(update.body.type, 'EXPENSE');
});

test('editing a plain purchase sends no scope', async () => {
    const { view, calls } = await openPurchases([purchase()]);

    await findByTag(lines(view)[0], 'button').find((button) => button.textContent === 'Editar').dispatch('click');
    await flush();
    const dialog = findAll(globalThis.document.body, (node) => node.tagName === 'DIALOG').at(-1);
    await findByTag(dialog, 'form')[0].dispatch('submit');
    await flush();

    const update = calls.find((call) => call.method === 'PUT');
    assert.equal(update.search, '');
});
