import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll, findByTag, findByText } from './fake-dom.mjs';

installFakeDom();

const { renderProfile } = await import('../../main/resources/static/js/views/profile.js');
const { renderAuth } = await import('../../main/resources/static/js/views/auth.js');
const { session } = await import('../../main/resources/static/js/api.js');

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

const BASE = {
    name: 'Ana Paula', email: 'ana@example.com', createdAt: '2026-01-10T12:00:00Z', pendingEmail: null,
    hasPassword: true, googleEmail: null, avatarUrl: null, avatarHidden: false
};
const GOOGLE_ONLY = { hasPassword: false, googleEmail: 'ana@gmail.com' };

/** Fake backend; `respond(path, body)` answers the deletion POST (204 by default). */
function startServer(profile, respond = () => ({ status: 204 })) {
    const state = { requests: [], googleClientId: '' };
    globalThis.fetch = async (url, options = {}) => {
        const path = new URL(url, 'http://localhost').pathname;
        const method = options.method || 'GET';
        if (path === '/data/common-passwords.txt') return new Response('senha12345\n');
        if (path === '/api/v1/auth/google/config') return Response.json({ clientId: state.googleClientId });
        if (path === '/api/v1/profile' && method === 'GET') return Response.json({ ...BASE, ...profile });
        const body = options.body ? JSON.parse(options.body) : undefined;
        state.requests.push({ method, path, body });
        const answer = respond(path, body);
        return answer.body === undefined ? new Response(null, { status: answer.status }) : Response.json(answer.body, { status: answer.status });
    };
    return state;
}

function listenForDeletion(t) {
    const events = [];
    const original = globalThis.window.dispatchEvent;
    globalThis.window.dispatchEvent = (event) => { events.push(event.type); };
    t.after(() => { globalThis.window.dispatchEvent = original; });
    return events;
}

function turnGoogleOn(t, state) {
    const calls = { initialize: null };
    state.googleClientId = 'meu-app.apps.googleusercontent.com';
    globalThis.google = {
        accounts: { id: { initialize: (options) => { calls.initialize = options; }, renderButton() {} } }
    };
    t.after(() => { delete globalThis.google; });
    return calls;
}

const byId = (view, id) => findAll(view, (node) => node.getAttribute('id') === id)[0];
const deleteSection = (view) => byId(view, 'profile-delete-title').parent.parent;
const type = async (input, text) => { input.value = text; await input.dispatch('input'); };
const deleteButton = (view) => findByText(deleteSection(view), 'button', 'Excluir minha conta');

// CA7

test('the deletion card says it is immediate and final, closes the tickets, says goodbye and keeps the door open', async () => {
    startServer({});
    const view = await renderProfile();
    const card = deleteSection(view);

    assert.equal(byId(view, 'profile-delete-title').tagName, 'H2');
    assert.match(card.textContent, /excluídos de imediato/);
    assert.match(card.textContent, /chamados de suporte são encerrados/);
    assert.match(card.textContent, /Não há como desfazer/);
    assert.match(card.textContent, /Sentimos muito por ver você ir/);
    assert.match(card.textContent, /felizes por você ter usado o moneyPilot/);
    assert.match(card.textContent, /portas continuam abertas/);
});

test('the sad face is decorative for screen readers', async () => {
    startServer({});
    const view = await renderProfile();

    const emoji = findAll(deleteSection(view), (node) => node.textContent === '😢')[0];

    assert.equal(emoji.getAttribute('aria-hidden'), 'true');
});

test('the confirmation word is labelled, and the button stays off until it is typed', async () => {
    startServer({});
    const view = await renderProfile();
    const input = byId(view, 'profile-delete-word');
    const label = findByTag(deleteSection(view), 'label').find((node) => node.getAttribute('for') === 'profile-delete-word');

    assert.match(label.textContent, /EXCLUIR/);
    assert.equal(input.getAttribute('autocomplete'), 'off');
    assert.equal(deleteButton(view).disabled, true);

    await type(input, 'EXCLUI');
    assert.equal(deleteButton(view).disabled, true);

    await type(input, 'excluir');
    assert.equal(deleteButton(view).disabled, false);

    await type(input, 'EXCLUIR!');
    assert.equal(deleteButton(view).disabled, true);
});

// CA1

test('with the password and the word, the account is deleted, the session is cleared and the app is told', async (t) => {
    const state = startServer({});
    const events = listenForDeletion(t);
    session.start('token-antigo', 'ana@example.com');
    const view = await renderProfile();

    byId(view, 'profile-delete-password').value = 'minha frase secreta';
    await type(byId(view, 'profile-delete-word'), 'EXCLUIR');
    await findByTag(deleteSection(view), 'form')[0].dispatch('submit');

    assert.deepEqual(state.requests, [{ method: 'POST', path: '/api/v1/profile/deletion', body: { currentPassword: 'minha frase secreta' } }]);
    assert.equal(session.active, false);
    assert.deepEqual(events.filter((name) => name === 'account:deleted'), ['account:deleted']);
});

test('the form does nothing while the word is missing', async (t) => {
    const state = startServer({});
    const events = listenForDeletion(t);
    const view = await renderProfile();

    byId(view, 'profile-delete-password').value = 'minha frase secreta';
    await findByTag(deleteSection(view), 'form')[0].dispatch('submit');

    assert.deepEqual(state.requests, []);
    assert.equal(events.includes('account:deleted'), false);
});

// CA2

test('a wrong password shows the error under the field, keeps the session and frees the button', async (t) => {
    startServer({}, () => ({ status: 400, body: { message: 'Current password is incorrect', fieldErrors: { currentPassword: 'Current password is incorrect' } } }));
    const events = listenForDeletion(t);
    session.start('token-vivo', 'ana@example.com');
    const view = await renderProfile();

    byId(view, 'profile-delete-password').value = 'errada';
    await type(byId(view, 'profile-delete-word'), 'EXCLUIR');
    await findByTag(deleteSection(view), 'form')[0].dispatch('submit');

    assert.equal(byId(view, 'profile-delete-password-error').textContent, 'Senha atual incorreta.');
    assert.equal(session.active, true);
    assert.equal(events.includes('account:deleted'), false);
    assert.equal(deleteButton(view).disabled, false);
    assert.equal(deleteButton(view).textContent, 'Excluir minha conta');
    assert.equal(document.activeElement, byId(view, 'profile-delete-password'));
});

test('too many attempts show the general alert', async () => {
    startServer({}, () => ({ status: 429, body: { message: 'Muitas tentativas' } }));
    const view = await renderProfile();

    byId(view, 'profile-delete-password').value = 'errada';
    await type(byId(view, 'profile-delete-word'), 'EXCLUIR');
    await findByTag(deleteSection(view), 'form')[0].dispatch('submit');

    const alerts = findAll(deleteSection(view), (node) => node.getAttribute('role') === 'alert' && !node.hidden);
    assert.equal(alerts.length, 1);
});

// CA3

test('a Google-only account has no password field: the Google button confirms, once the word is typed', async (t) => {
    const state = startServer(GOOGLE_ONLY);
    const google = turnGoogleOn(t, state);
    const events = listenForDeletion(t);
    const view = await renderProfile();
    await settle();

    const card = deleteSection(view);
    const block = findAll(card, (node) => node.className.split(' ').includes('google-block'))[0];
    assert.equal(byId(card, 'profile-delete-password'), undefined);
    assert.equal(block.hasAttribute('inert'), true);

    await type(byId(view, 'profile-delete-word'), 'EXCLUIR');
    assert.equal(block.hasAttribute('inert'), false);

    await google.initialize.callback({ credential: 'id-token' });

    assert.deepEqual(state.requests, [{ method: 'POST', path: '/api/v1/profile/deletion', body: { credential: 'id-token' } }]);
    assert.equal(events.includes('account:deleted'), true);
});

test('a Google credential without the word is not sent, and the word field says what is missing', async (t) => {
    const state = startServer(GOOGLE_ONLY);
    const google = turnGoogleOn(t, state);
    const view = await renderProfile();
    await settle();

    await google.initialize.callback({ credential: 'id-token' });

    assert.deepEqual(state.requests, []);
    assert.match(byId(view, 'profile-delete-word-error').textContent, /Digite EXCLUIR/);
});

test('another Google account is refused with a clear message under the Google button', async (t) => {
    const state = startServer(GOOGLE_ONLY, () => ({ status: 400, body: { message: 'x', fieldErrors: { credential: 'This is not the Google account linked to your account' } } }));
    const google = turnGoogleOn(t, state);
    const events = listenForDeletion(t);
    const view = await renderProfile();
    await settle();

    await type(byId(view, 'profile-delete-word'), 'EXCLUIR');
    await google.initialize.callback({ credential: 'id-token' });

    assert.match(byId(view, 'profile-delete-google-error').textContent, /não é a conta Google vinculada/);
    assert.equal(events.includes('account:deleted'), false);
});

// Login com o aviso

test('the login shows the deletion notice once, announced and focused', () => {
    const view = renderAuth(() => {}, { notice: 'Sua conta foi excluída. Obrigado por ter usado o moneyPilot.' });

    const notice = findAll(view, (node) => node.className === 'auth-notice')[0];
    assert.match(notice.textContent, /Sua conta foi excluída/);
    assert.equal(notice.getAttribute('role'), 'status');
    assert.equal(document.activeElement, notice);

    const again = renderAuth(() => {});
    assert.equal(findAll(again, (node) => node.className === 'auth-notice').length, 0);
});
