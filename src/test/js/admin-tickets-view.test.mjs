import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll, findByTag, findByText } from './fake-dom.mjs';

installFakeDom();

const { renderAdminTickets } = await import('../../main/resources/static/js/views/admin-tickets.js');
const { api, session } = await import('../../main/resources/static/js/api.js');

let calls;

/** Fake backend: every call answers with the next response of `queue` and is recorded in `calls`. */
function startServer(queue) {
    calls = [];
    globalThis.fetch = async (url, options) => {
        const parsed = new URL(url, 'http://localhost');
        calls.push({ path: parsed.pathname, search: parsed.search, options });
        const { status, body } = queue.shift();
        return body === undefined ? new Response(null, { status }) : Response.json(body, { status });
    };
}

beforeEach(() => {
    session.clear();
    session.start('token', 'admin@example.com');
});

const ticket = (overrides = {}) => ({
    id: 1, reference: 'K7P3XN', category: 'PROBLEMA', status: 'OPEN', replyTo: 'ana@example.com',
    emailSent: true, createdAt: '2026-10-28T15:00:00Z', ...overrides
});
const page = (content) => ({ content, page: { number: 0, size: 20, totalPages: 1, totalElements: content.length } });
const detail = (overrides = {}) => ticket({
    message: 'O saldo não bate.', statusChangedAt: null, accountName: 'Ana Souza', ...overrides
});
const settle = () => new Promise((resolve) => setTimeout(resolve, 0));
const buttonsNamed = (view, text) => findAll(view, (node) => node.tagName === 'BUTTON' && node.textContent === text);

test('opens on the open tickets and lists them with reference, subject, status and e-mail', async () => {
    startServer([{ status: 200, body: page([ticket()]) }]);

    const view = await renderAdminTickets();

    assert.equal(calls[0].path, '/api/v1/admin/support/tickets');
    assert.match(calls[0].search, /status=OPEN/);
    const text = view.textContent;
    for (const expected of ['K7P3XN', 'Problema', 'Aberto', 'ana@example.com']) {
        assert.ok(text.includes(expected), expected);
    }
    assert.ok(!text.includes('E-mail não enviado'));
});

test('marks the tickets whose e-mail to support was not sent', async () => {
    startServer([{ status: 200, body: page([ticket({ emailSent: false })]) }]);

    const view = await renderAdminTickets();

    assert.ok(view.textContent.includes('E-mail não enviado'));
});

test('changing the filter asks the server for that status; "Todos" sends none', async () => {
    startServer([
        { status: 200, body: page([ticket()]) },
        { status: 200, body: page([ticket({ status: 'RESOLVED' })]) },
        { status: 200, body: page([]) }
    ]);
    const view = await renderAdminTickets();

    const select = findByTag(view, 'select')[0];
    select.value = 'RESOLVED';
    await select.dispatch('change');
    await settle();
    assert.match(calls[1].search, /status=RESOLVED/);

    const again = findByTag(view, 'select')[0];
    again.value = '';
    await again.dispatch('change');
    await settle();
    assert.ok(!calls[2].search.includes('status='));
    assert.ok(view.textContent.includes('Nenhum chamado com esse status.'));
});

test('a message with HTML or script is shown as text, never as markup', async () => {
    const hostile = '<script>alert(1)</script><img src=x onerror=alert(2)>';
    startServer([
        { status: 200, body: page([ticket()]) },
        { status: 200, body: detail({ message: hostile }) }
    ]);
    const view = await renderAdminTickets();

    await buttonsNamed(view, 'Abrir')[0].dispatch('click');

    assert.equal(calls[1].path, '/api/v1/admin/support/tickets/1');
    const message = findAll(view, (node) => node.className.split(' ').includes('ticket-message'))[0];
    assert.equal(message.textContent, hostile);
    assert.equal(message.children.length, 1, 'only a text node, no elements');
    assert.equal(findByTag(view, 'script').length, 0);
    assert.equal(findByTag(view, 'img').length, 0);
});

test('an opened ticket offers a mailto link to reply and the account name', async () => {
    startServer([
        { status: 200, body: page([ticket()]) },
        { status: 200, body: detail() }
    ]);
    const view = await renderAdminTickets();

    await buttonsNamed(view, 'Abrir')[0].dispatch('click');

    const link = findByTag(view, 'a').find((a) => a.getAttribute('href').startsWith('mailto:'));
    assert.equal(link.getAttribute('href'), 'mailto:ana@example.com');
    assert.ok(view.textContent.includes('Ana Souza'));
});

test('saving the status sends the PATCH and shows the ticket with the new status', async () => {
    startServer([
        { status: 200, body: page([ticket()]) },
        { status: 200, body: detail() },
        { status: 200, body: detail({ status: 'RESOLVED', statusChangedAt: '2026-10-28T16:00:00Z' }) }
    ]);
    const view = await renderAdminTickets();
    await buttonsNamed(view, 'Abrir')[0].dispatch('click');

    const select = findByTag(view, 'select')[0];
    select.value = 'RESOLVED';
    await findByTag(view, 'form')[0].dispatch('submit');

    assert.equal(calls[2].options.method, 'PATCH');
    assert.deepEqual(JSON.parse(calls[2].options.body), { status: 'RESOLVED' });
    assert.ok(findAll(view, (node) => node.className.split(' ').includes('badge-settled')).length > 0);
    assert.ok(view.textContent.includes('Resolvido'));
});

test('a server refusal (404) is shown instead of the list', async () => {
    startServer([{ status: 404, body: { message: 'Resource not found' } }]);

    const view = await renderAdminTickets();

    assert.ok(view.textContent.includes('Resource not found'));
});

test('the profile tells the session whether to show the panel', async () => {
    startServer([{ status: 200, body: { name: 'Ana', admin: true } }]);
    assert.equal(session.admin, null);

    await api.profile.get();
    assert.equal(session.admin, true);

    session.clear();
    assert.equal(session.admin, null);

    session.start('token', 'ana@example.com');
    startServer([{ status: 200, body: { name: 'Ana', admin: false } }]);
    await api.profile.get();
    assert.equal(session.admin, false);
});
