import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll } from './fake-dom.mjs';

const { toasts } = installFakeDom();

const { renderProfile } = await import('../../main/resources/static/js/views/profile.js');

const PROFILE = {
    name: 'Ana Paula', email: 'ana@example.com', createdAt: '2026-01-10T12:00:00Z', pendingEmail: null,
    hasPassword: true, googleEmail: null, avatarUrl: null, avatarHidden: false
};
const ACCOUNTS = [
    { id: 1, name: 'Corrente', type: 'CHECKING', initialBalance: '100.00', countsAsAvailable: true },
    { id: 2, name: 'Renda fixa', type: 'INVESTMENT', initialBalance: '50.00', countsAsAvailable: false }
];

let requests;
let putStatus;

function startServer() {
    requests = [];
    putStatus = 200;
    globalThis.fetch = async (url, options = {}) => {
        const path = new URL(url, 'http://localhost').pathname;
        const method = options.method || 'GET';
        requests.push({ method, path, body: options.body ? JSON.parse(options.body) : undefined });
        if (path === '/data/common-passwords.txt') return new Response('senha12345\n');
        if (path === '/api/v1/profile') return Response.json(PROFILE);
        if (path === '/api/v1/accounts' && method === 'GET') {
            return Response.json({ content: ACCOUNTS, totalPages: 1, number: 0, page: { number: 0, totalPages: 1 } });
        }
        if (path.startsWith('/api/v1/accounts/') && method === 'PUT') {
            if (putStatus !== 200) return Response.json({ message: 'Falhou no servidor' }, { status: putStatus });
            const sent = JSON.parse(options.body);
            return Response.json({ ...ACCOUNTS.find((a) => path.endsWith(`/${a.id}`)), countsAsAvailable: sent.countsAsAvailable });
        }
        return new Response(null, { status: 404 });
    };
}

const tabsOf = (view) => findAll(view, (node) => node.getAttribute('role') === 'tab');
const panelsOf = (view) => findAll(view, (node) => node.getAttribute('role') === 'tabpanel');
const selected = (view) => tabsOf(view).filter((tab) => tab.getAttribute('aria-selected') === 'true').map((tab) => tab.textContent);
const checkboxes = (view) => findAll(view, (node) => node.getAttribute('type') === 'checkbox');

test('the profile has three tabs wired to their panels, and only the first panel is visible', async () => {
    startServer();
    const view = await renderProfile();

    const tabs = tabsOf(view);
    assert.deepEqual(tabs.map((tab) => tab.textContent), ['Dados', 'Acesso e segurança', 'Saldo disponível']);
    assert.equal(findAll(view, (node) => node.getAttribute('role') === 'tablist')[0].getAttribute('aria-label'), 'Seções do perfil');
    panelsOf(view).forEach((panel, index) => {
        assert.equal(tabs[index].getAttribute('aria-controls'), panel.getAttribute('id'));
        assert.equal(panel.getAttribute('aria-labelledby'), tabs[index].getAttribute('id'));
    });
    assert.deepEqual(panelsOf(view).map((panel) => panel.hidden), [false, true, true]);
    assert.deepEqual(tabs.map((tab) => tab.getAttribute('tabindex')), ['0', '-1', '-1']);
    assert.deepEqual(selected(view), ['Dados']);
});

test('arrow keys, Home and End move the selection and the focus, wrapping around', async () => {
    startServer();
    const view = await renderProfile();
    const tabs = tabsOf(view);

    await tabs[0].dispatch('keydown', { key: 'ArrowRight' });
    assert.deepEqual(selected(view), ['Acesso e segurança']);
    assert.equal(globalThis.document.activeElement, tabs[1]);

    await tabs[1].dispatch('keydown', { key: 'End' });
    assert.deepEqual(selected(view), ['Saldo disponível']);

    await tabs[2].dispatch('keydown', { key: 'ArrowRight' });
    assert.deepEqual(selected(view), ['Dados']);

    await tabs[0].dispatch('keydown', { key: 'ArrowLeft' });
    assert.deepEqual(selected(view), ['Saldo disponível']);

    await tabs[2].dispatch('keydown', { key: 'Home' });
    assert.deepEqual(selected(view), ['Dados']);
    assert.deepEqual(panelsOf(view).map((panel) => panel.hidden), [false, true, true]);
});

test('the accounts are only fetched when the "Saldo disponível" tab opens', async () => {
    startServer();
    const view = await renderProfile();
    assert.equal(requests.some((request) => request.path === '/api/v1/accounts'), false);

    await tabsOf(view)[2].dispatch('click');
    await new Promise((resolve) => setTimeout(resolve, 0));

    assert.equal(requests.filter((request) => request.path === '/api/v1/accounts').length, 1);
    assert.deepEqual(checkboxes(view).map((box) => box.checked), [true, false]);
});

test('unchecking an account sends the whole account with the new choice and confirms it', async () => {
    startServer();
    const view = await renderProfile();
    await tabsOf(view)[2].dispatch('click');
    await new Promise((resolve) => setTimeout(resolve, 0));

    const [box] = checkboxes(view);
    box.checked = false;
    await box.dispatch('change');

    const put = requests.find((request) => request.method === 'PUT');
    assert.equal(put.path, '/api/v1/accounts/1');
    assert.deepEqual(put.body, { name: 'Corrente', accountType: 'CHECKING', initialBalance: '100.00', countsAsAvailable: false });
    assert.equal(box.checked, false);
    assert.equal(box.disabled, false);
});

test('when saving fails the checkbox goes back to the real state and the error is visible', async () => {
    startServer();
    const view = await renderProfile();
    await tabsOf(view)[2].dispatch('click');
    await new Promise((resolve) => setTimeout(resolve, 0));
    putStatus = 500;

    const box = checkboxes(view)[1];
    box.checked = true;
    await box.dispatch('change');

    assert.equal(box.checked, false);
    assert.equal(box.disabled, false);
    const alert = findAll(view, (node) => node.getAttribute('role') === 'alert' && !node.hidden);
    assert.equal(alert.length, 1);
    assert.match(alert[0].textContent, /Falhou no servidor/);
    assert.ok(toasts.children.length > 0);
});
