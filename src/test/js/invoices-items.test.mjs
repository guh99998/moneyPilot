import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll, findByTag } from './fake-dom.mjs';

installFakeDom();

const { itemActions, editPayload, editScope, deleteMessage } = await import('../../main/resources/static/js/views/invoices-items.js');
const { renderInvoices, selectInvoiceCard } = await import('../../main/resources/static/js/views/invoices.js');

const purchase = (extra = {}) => ({
    id: 10, accountId: 7, categoryId: 3, categoryName: 'Lazer', type: 'EXPENSE', description: 'Notebook',
    amount: '83.33', date: '2026-04-12', ...extra
});
const installment = (extra = {}) => purchase({ installmentGroupId: 'g-1', installmentNumber: 4, installmentTotal: 10, ...extra });

test('an expense or an income on the card can be edited and deleted', () => {
    assert.deepEqual(itemActions(purchase()), ['edit', 'delete']);
    assert.deepEqual(itemActions(installment()), ['edit', 'delete']);
    assert.deepEqual(itemActions(purchase({ type: 'INCOME' })), ['edit', 'delete']);
});

test('payments and invoice settlements have no actions', () => {
    assert.deepEqual(itemActions(purchase({ type: 'INCOME', transferGroupId: 't-1' })), []);
    assert.deepEqual(itemActions(purchase({ billId: 5 })), []);
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

test('the delete message warns that an installment removes the whole group', () => {
    assert.match(deleteMessage(installment()), /parcela 4 de 10\. Todas as 10 parcelas serão apagadas juntas/);
    assert.match(deleteMessage(purchase()), /será apagada e o histórico some/);
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
        if (path.startsWith('/api/v1/transactions')) return options.method === 'DELETE' ? new Response(null, { status: 204 }) : Response.json([]);
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
const lastDialog = () => findAll(globalThis.document.body, (node) => node.tagName === 'DIALOG').at(-1);

test('each purchase line offers its actions, and payments offer none', async () => {
    const { view } = await openPurchases([
        purchase(),
        purchase({ id: 11, description: 'Pagamento', type: 'INCOME', transferGroupId: 't-1' })
    ]);

    const [normal, payment] = lines(view);
    assert.deepEqual(buttonLabels(normal), ['Editar', 'Apagar']);
    assert.deepEqual(buttonLabels(payment), []);
});

test('every action names the purchase it acts on, for screen readers', async () => {
    const { view } = await openPurchases([purchase()]);

    const labels = findByTag(lines(view)[0], 'button').map((button) => button.getAttribute('aria-label'));

    assert.deepEqual(labels, ['Editar: Notebook', 'Apagar: Notebook']);
});

test('deleting an installment removes the whole group and reloads the invoices', async () => {
    const { view, calls } = await openPurchases([installment()]);

    await findByTag(lines(view)[0], 'button').find((button) => button.textContent === 'Apagar').dispatch('click');
    const dialog = lastDialog();
    assert.match(text(dialog), /Todas as 10 parcelas serão apagadas juntas/);
    await findByTag(dialog, 'button').find((button) => button.textContent === 'Apagar').dispatch('click');
    await flush();

    assert.ok(calls.some((call) => call.method === 'DELETE' && call.path === '/api/v1/transactions/installments/g-1'));
    assert.equal(calls.filter((call) => call.path === '/api/v1/accounts/7/invoices').length, 2, 'invoices reloaded');
});

test('deleting a plain purchase removes only it', async () => {
    const { view, calls } = await openPurchases([purchase()]);

    await findByTag(lines(view)[0], 'button').find((button) => button.textContent === 'Apagar').dispatch('click');
    await findByTag(lastDialog(), 'button').find((button) => button.textContent === 'Apagar').dispatch('click');
    await flush();

    assert.ok(calls.some((call) => call.method === 'DELETE' && call.path === '/api/v1/transactions/10'));
});

test('editing an installment sends the scope as a query parameter and keeps the account and type', async () => {
    const { view, calls } = await openPurchases([installment()]);

    await findByTag(lines(view)[0], 'button').find((button) => button.textContent === 'Editar').dispatch('click');
    await flush();
    const dialog = lastDialog();
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
    await findByTag(lastDialog(), 'form')[0].dispatch('submit');
    await flush();

    const update = calls.find((call) => call.method === 'PUT');
    assert.equal(update.search, '');
});
