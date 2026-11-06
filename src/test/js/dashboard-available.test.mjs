import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll } from './fake-dom.mjs';

installFakeDom();

const { hero, savedCents, accountBalances, loadBalances } = await import('../../main/resources/static/js/views/dashboard.js');

const SUMMARY = { balance: '100.00' };
const FORECAST = { buckets: [] };
const byClass = (root, name) => findAll(root, (node) => node.className === name);

test('the hero shows the available balance and the saved amount when there is one', () => {
    const view = hero('4000.00', SUMMARY, FORECAST, 100000);

    assert.equal(byClass(view, 'label')[0].textContent, 'Saldo disponível');
    assert.match(byClass(view, 'saved')[0].textContent, /^Guardado: R\$\s1\.000,00$/);
});

test('the hero hides "Guardado" when no account is left out', () => {
    const view = hero('4000.00', SUMMARY, FORECAST, 0);

    assert.equal(byClass(view, 'saved').length, 0);
});

test('savedCents sums only the accounts that do not count, in whole cents', () => {
    const rows = [
        { name: 'Corrente', balance: '4000.10', countsAsAvailable: true },
        { name: 'Renda fixa', balance: '0.10', countsAsAvailable: false },
        { name: 'Cripto', balance: '0.20', countsAsAvailable: false }
    ];

    assert.equal(savedCents(rows), 30);
    assert.equal(savedCents([]), 0);
});

test('the table keeps every account and marks the ones outside the available balance', () => {
    const table = accountBalances([
        { name: 'Corrente', balance: '10.00', countsAsAvailable: true },
        { name: 'Renda fixa', balance: '20.00', countsAsAvailable: false }
    ]);

    const text = findAll(table, (node) => node.tagName === 'TD').map((cell) => cell.textContent);
    assert.ok(text.some((cell) => cell === 'Corrente'));
    assert.ok(text.some((cell) => cell === 'Renda fixa (fora do disponível)'));
});

test('loadBalances carries countsAsAvailable from the account, also when the balance request fails', async () => {
    globalThis.fetch = async (url) => {
        const path = new URL(url, 'http://localhost').pathname;
        if (path === '/api/v1/accounts') {
            return Response.json({
                content: [
                    { id: 1, name: 'Corrente', initialBalance: '5.00', countsAsAvailable: true },
                    { id: 2, name: 'Renda fixa', initialBalance: '7.00', countsAsAvailable: false }
                ],
                page: { number: 0, totalPages: 1 }, totalPages: 1, number: 0
            });
        }
        if (path === '/api/v1/accounts/1/balance') return Response.json({ currentBalance: '9.00' });
        return new Response(null, { status: 500 });
    };

    const rows = await loadBalances();

    assert.deepEqual(rows.map((row) => [row.name, row.balance, row.countsAsAvailable]),
        [['Corrente', '9.00', true], ['Renda fixa', '7.00', false]]);
});

test('savedCents leaves credit cards out: a debt is not money put aside', () => {
    const rows = [
        { name: 'Renda fixa', balance: '300.00', countsAsAvailable: false, type: 'INVESTMENT' },
        { name: 'Nubank', balance: '-200.00', countsAsAvailable: false, type: 'CREDIT_CARD' }
    ];

    assert.equal(savedCents(rows), 30000);
});

test('the table says a card outside the available balance enters the forecast through its invoice', () => {
    const table = accountBalances([
        { name: 'Nubank', balance: '-200.00', countsAsAvailable: false, type: 'CREDIT_CARD', hasInvoices: true },
        { name: 'Inter', balance: '-50.00', countsAsAvailable: false, type: 'CREDIT_CARD', hasInvoices: false }
    ]);

    const cells = findAll(table, (node) => node.tagName === 'TD').map((cell) => cell.textContent);
    assert.ok(cells.some((cell) => cell === 'Nubank (a fatura entra na previsão)'));
    assert.ok(cells.some((cell) => cell === 'Inter (fora do disponível)'));
});

test('loadBalances carries the account type and whether the card has invoices', async () => {
    globalThis.fetch = async (url) => {
        const path = new URL(url, 'http://localhost').pathname;
        if (path === '/api/v1/accounts') {
            return Response.json({
                content: [{ id: 3, name: 'Nubank', type: 'CREDIT_CARD', initialBalance: '0.00', countsAsAvailable: false, closingDay: 10, dueDay: 17 }],
                page: { number: 0, totalPages: 1 }, totalPages: 1, number: 0
            });
        }
        if (path === '/api/v1/accounts/3/balance') return Response.json({ currentBalance: '-80.00' });
        return new Response(null, { status: 500 });
    };

    const [row] = await loadBalances();

    assert.equal(row.type, 'CREDIT_CARD');
    assert.equal(row.hasInvoices, true);
});
