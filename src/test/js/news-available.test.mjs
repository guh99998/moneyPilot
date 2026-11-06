import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll, findByTag } from './fake-dom.mjs';

installFakeDom();

const { availableNewsCard } = await import('../../main/resources/static/js/views/news-available.js');

test('people with accounts see the notice with the three steps and a link to the profile', () => {
    const card = availableNewsCard(2);

    assert.match(findByTag(card, 'h2')[0].textContent, /saldo disponível/);
    assert.equal(findByTag(card, 'li').length, 3);
    assert.equal(findAll(card, (node) => node.getAttribute('href') === '#/profile').length, 1);
});

test('a person with no accounts yet does not get the notice', () => {
    assert.equal(availableNewsCard(0), null);
});

test('"Entendi" dismisses it for good and tells the dashboard', async () => {
    let dismissed = 0;
    const card = availableNewsCard(1, () => { dismissed += 1; });

    await findByTag(card, 'button')[0].dispatch('click');

    assert.equal(dismissed, 1);
    assert.equal(availableNewsCard(1), null);
});
