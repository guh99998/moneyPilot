import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll, findByTag } from './fake-dom.mjs';

installFakeDom();

const { invoiceNewsCard } = await import('../../main/resources/static/js/views/news-invoices.js');

test('people with a credit card see the notice with the steps and links to Contas and Faturas', () => {
    const card = invoiceNewsCard(1);

    assert.match(findByTag(card, 'h2')[0].textContent, /fatura do cartão/);
    assert.equal(findByTag(card, 'li').length, 3);
    assert.equal(findAll(card, (node) => node.getAttribute('href') === '#/accounts').length, 1);
    assert.equal(findAll(card, (node) => node.getAttribute('href') === '#/invoices').length, 1);
});

test('the notice says nothing changes until the days are informed', () => {
    const card = invoiceNewsCard(2);

    assert.match(card.textContent, /só muda quando você informar/);
});

test('a person without credit cards does not get the notice', () => {
    assert.equal(invoiceNewsCard(0), null);
});

test('"Entendi" dismisses it for good and tells the dashboard', async () => {
    let dismissed = 0;
    const card = invoiceNewsCard(1, () => { dismissed += 1; });

    await findByTag(card, 'button')[0].dispatch('click');

    assert.equal(dismissed, 1);
    assert.equal(invoiceNewsCard(1), null);
});
