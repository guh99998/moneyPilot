import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll, findByTag, findByText } from './fake-dom.mjs';

installFakeDom();

const { onboardingCard, loadOnboarding } = await import('../../main/resources/static/js/views/onboarding.js');
const { session } = await import('../../main/resources/static/js/api.js');

const textOf = (view) => findAll(view, () => true).map((node) => node.textContent).join(' ');

const STEPS = (a, t, b) => [
    { key: 'ACCOUNT', done: a },
    { key: 'TRANSACTION', done: t },
    { key: 'BUDGET', done: b }
];

let requests;

function startServer(handler) {
    requests = [];
    globalThis.fetch = async (url, options = {}) => {
        const path = new URL(url).pathname;
        requests.push({ path, method: options.method || 'GET' });
        return handler(path, options);
    };
}

beforeEach(() => {
    session.clear();
    session.start('token-123', 'ana@example.com');
});

test('the card lists the three steps in order, each with its state written out and a link to its screen', () => {
    const card = onboardingCard({ visible: true, steps: STEPS(true, false, false) }, () => {});

    const items = findByTag(card, 'li');
    assert.equal(items.length, 3);
    assert.match(items[0].textContent, /Feito/);
    assert.match(items[0].textContent, /conta bancária/);
    assert.match(items[1].textContent, /A fazer/);
    assert.match(items[1].textContent, /despesa ou receita/);
    assert.match(items[2].textContent, /A fazer/);
    assert.match(items[2].textContent, /orçamento/);

    const links = findByTag(card, 'a').map((link) => link.getAttribute('href'));
    assert.deepEqual(links, ['#/accounts', '#/transactions', '#/budgets']);
});

test('the steps are an ordered list and the card has a heading', () => {
    const card = onboardingCard({ visible: true, steps: STEPS(false, false, false) }, () => {});

    assert.equal(findByTag(card, 'ol').length, 1);
    assert.equal(findByTag(card, 'h2')[0].textContent, 'Primeiros passos');
});

test('dismissing calls the API once and tells the dashboard to remove the card', async () => {
    startServer(() => new Response(null, { status: 204 }));
    let removed = 0;
    const card = onboardingCard({ visible: true, steps: STEPS(false, false, false) }, () => { removed++; });

    await findByText(card, 'button', 'Dispensar').dispatch('click');

    assert.deepEqual(requests, [{ path: '/api/v1/onboarding/dismiss', method: 'POST' }]);
    assert.equal(removed, 1);
});

test('when dismissing fails the card stays', async () => {
    startServer(() => Response.json({ message: 'Erro' }, { status: 500 }));
    let removed = 0;
    const card = onboardingCard({ visible: true, steps: STEPS(false, false, false) }, () => { removed++; });

    await findByText(card, 'button', 'Dispensar').dispatch('click');

    assert.equal(removed, 0);
});

test('loadOnboarding returns the payload when the card is visible', async () => {
    startServer(() => Response.json({ visible: true, steps: STEPS(true, false, false) }));

    const onboarding = await loadOnboarding();

    assert.equal(onboarding.visible, true);
    assert.equal(textOf(onboardingCard(onboarding, () => {})).includes('Feito'), true);
});

test('loadOnboarding returns null when the card is hidden, so the dashboard shows nothing', async () => {
    startServer(() => Response.json({ visible: false, steps: [] }));

    assert.equal(await loadOnboarding(), null);
});

test('loadOnboarding never breaks the dashboard: a failure just means no card', async () => {
    startServer(() => Response.json({ message: 'Erro' }, { status: 500 }));

    assert.equal(await loadOnboarding(), null);
});
