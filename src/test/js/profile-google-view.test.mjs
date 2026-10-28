import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll, findByTag, findByText } from './fake-dom.mjs';

installFakeDom();

const { renderProfile } = await import('../../main/resources/static/js/views/profile.js');
const { session } = await import('../../main/resources/static/js/api.js');

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

const BASE = {
    name: 'Ana Paula', email: 'ana@example.com', createdAt: '2026-01-10T12:00:00Z', pendingEmail: null,
    hasPassword: true, googleEmail: null, avatarUrl: null, avatarHidden: false
};
const PHOTO = 'https://lh3.example/ana.png';

/**
 * Fake backend with a mutable profile. `respond(path, body)` may answer a POST/DELETE; whatever it returns
 * as `profile` replaces the stored one, so the screen's reload sees the change.
 */
function startServer(initial, respond = () => ({ status: 204 })) {
    const state = { profile: { ...BASE, ...initial }, requests: [], googleClientId: '' };
    globalThis.fetch = async (url, options = {}) => {
        const path = new URL(url, 'http://localhost').pathname;
        const method = options.method || 'GET';
        if (path === '/data/common-passwords.txt') return new Response('senha12345\n');
        if (path === '/api/v1/auth/google/config') return Response.json({ clientId: state.googleClientId });
        if (path === '/api/v1/profile' && method === 'GET') return Response.json(state.profile);
        const body = options.body ? JSON.parse(options.body) : undefined;
        state.requests.push({ method, path, body });
        const answer = respond(path, body, state) || { status: 204 };
        if (answer.profile) state.profile = { ...state.profile, ...answer.profile };
        return answer.body === undefined ? new Response(null, { status: answer.status }) : Response.json(answer.body, { status: answer.status });
    };
    return state;
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
const byClass = (view, name) => findAll(view, (node) => node.className.split(' ').includes(name));
const heading = (view, text) => findByText(view, 'h2', text);
const cardOf = (view, title) => heading(view, title).parent.parent;
const textOf = (view, title) => cardOf(view, title).textContent;
const alerts = (root) => findAll(root, (node) => node.getAttribute('role') === 'alert' && !node.hidden);

const GOOGLE_ONLY = { hasPassword: false, googleEmail: 'ana@gmail.com', avatarUrl: PHOTO };
const LINKED = { googleEmail: 'ana@gmail.com', avatarUrl: PHOTO };

test('a Google-only account sees that it has no password, and the e-mail form gives way to a notice', async () => {
    startServer(GOOGLE_ONLY);
    const view = await renderProfile();

    assert.match(textOf(view, 'Senha'), /Sua conta ainda não tem senha/);
    assert.ok(findByText(cardOf(view, 'Senha'), 'button', 'Criar senha'));
    assert.equal(findByTag(cardOf(view, 'Senha'), 'form').length, 0);
    assert.match(textOf(view, 'E-mail'), /crie uma senha antes/);
    assert.equal(findByTag(cardOf(view, 'E-mail'), 'input').length, 0);
});

test('"Criar senha" sends the reset link to the account e-mail and moves focus to the answer', async () => {
    const state = startServer(GOOGLE_ONLY, (path) => (path === '/api/v1/auth/password/forgot' ? { status: 202 } : null));
    const view = await renderProfile();

    await findByText(view, 'button', 'Criar senha').dispatch('click');

    assert.deepEqual(state.requests, [{ method: 'POST', path: '/api/v1/auth/password/forgot', body: { email: 'ana@example.com' } }]);
    const status = findAll(view, (node) => node.getAttribute('role') === 'status')[0];
    assert.match(status.textContent, /Enviamos um link para ana@example.com/);
    assert.equal(document.activeElement, status);
});

test('a refused "Criar senha" is announced and the button works again', async () => {
    startServer(GOOGLE_ONLY, () => ({ status: 429, body: { message: 'Too many' } }));
    const view = await renderProfile();
    const button = findByText(view, 'button', 'Criar senha');

    await button.dispatch('click');

    assert.equal(alerts(cardOf(view, 'Senha')).length, 1);
    assert.equal(button.disabled, false);
    assert.equal(button.textContent, 'Criar senha');
});

test('a Google-only account cannot unlink: the button is off and says why', async () => {
    startServer(GOOGLE_ONLY);
    const view = await renderProfile();

    const button = findByText(cardOf(view, 'Google'), 'button', 'Desvincular');

    assert.equal(button.disabled, true);
    assert.equal(button.getAttribute('aria-describedby'), 'profile-google-unlink-note');
    assert.match(byId(view, 'profile-google-unlink-note').textContent, /crie uma senha antes/);
    assert.match(textOf(view, 'Google'), /Conectado como ana@gmail.com/);
});

test('an unlinked account asks for the current password before the Google button', async (t) => {
    const state = startServer({});
    turnGoogleOn(t, state);
    const view = await renderProfile();
    await settle();

    const card = cardOf(view, 'Google');
    assert.equal(card.hidden, false);
    assert.ok(byId(card, 'profile-google-password'));
    assert.equal(byClass(card, 'google-block')[0].hidden, false);
});

test('the whole Google card stays hidden while Google is off', async () => {
    startServer({});
    const view = await renderProfile();
    await settle();

    assert.equal(cardOf(view, 'Google').hidden, true);
});

test('the Google card of a linked account shows even while Google is off, so it can be unlinked', async () => {
    startServer(LINKED);
    const view = await renderProfile();
    await settle();

    assert.equal(cardOf(view, 'Google').hidden, false);
});

test('linking without the current password asks for it and sends nothing', async (t) => {
    const state = startServer({});
    const calls = turnGoogleOn(t, state);
    const view = await renderProfile();
    await settle();

    await calls.initialize.callback({ credential: 'id-token' });

    assert.equal(byId(view, 'profile-google-password-error').textContent, 'Informe a senha atual para vincular.');
    assert.equal(document.activeElement, byId(view, 'profile-google-password'));
    assert.deepEqual(state.requests, []);
});

test('linking sends the credential and the password, then shows the connected account', async (t) => {
    const state = startServer({}, (path) => (path === '/api/v1/profile/google' ? { status: 204, profile: LINKED } : null));
    const calls = turnGoogleOn(t, state);
    const view = await renderProfile();
    await settle();
    byId(view, 'profile-google-password').value = 'minha frase secreta';

    await calls.initialize.callback({ credential: 'id-token' });

    assert.deepEqual(state.requests, [{
        method: 'POST', path: '/api/v1/profile/google', body: { credential: 'id-token', currentPassword: 'minha frase secreta' }
    }]);
    assert.match(textOf(view, 'Google'), /Conectado como ana@gmail.com/);
    assert.equal(document.activeElement, heading(view, 'Google'));
    assert.equal(session.avatar, PHOTO);
});

test('linking with the wrong password shows the field error in Portuguese', async (t) => {
    const message = 'Current password is incorrect';
    const state = startServer({}, () => ({ status: 400, body: { message, fieldErrors: { currentPassword: message } } }));
    const calls = turnGoogleOn(t, state);
    const view = await renderProfile();
    await settle();
    byId(view, 'profile-google-password').value = 'errada';

    await calls.initialize.callback({ credential: 'id-token' });

    assert.equal(byId(view, 'profile-google-password-error').textContent, 'Senha atual incorreta.');
});

test('a Google account used elsewhere is announced as a conflict', async (t) => {
    const state = startServer({}, () => ({
        status: 409, body: { message: 'This Google account is already linked to another account' }
    }));
    const calls = turnGoogleOn(t, state);
    const view = await renderProfile();
    await settle();
    byId(view, 'profile-google-password').value = 'minha frase secreta';

    await calls.initialize.callback({ credential: 'id-token' });

    const [box] = alerts(cardOf(view, 'Google'));
    assert.match(box.textContent, /já está vinculada a outra conta/);
});

test('unlinking sends the password and goes back to the unlinked card', async () => {
    const state = startServer(LINKED, (path) => (path === '/api/v1/profile/google/unlink'
        ? { status: 204, profile: { googleEmail: null, avatarUrl: null } }
        : null));
    const view = await renderProfile();
    byId(view, 'profile-google-password').value = 'minha frase secreta';

    await findByTag(cardOf(view, 'Google'), 'form')[0].dispatch('submit');

    assert.deepEqual(state.requests, [{
        method: 'POST', path: '/api/v1/profile/google/unlink', body: { currentPassword: 'minha frase secreta' }
    }]);
    assert.doesNotMatch(textOf(view, 'Google'), /Conectado como/);
    assert.equal(document.activeElement, heading(view, 'Google'));
});

test('unlinking with the wrong password keeps the link and shows the field error', async () => {
    const message = 'Current password is incorrect';
    startServer(LINKED, () => ({ status: 400, body: { message, fieldErrors: { currentPassword: message } } }));
    const view = await renderProfile();
    byId(view, 'profile-google-password').value = 'errada';

    await findByTag(cardOf(view, 'Google'), 'form')[0].dispatch('submit');

    assert.equal(byId(view, 'profile-google-password-error').textContent, 'Senha atual incorreta.');
    assert.match(textOf(view, 'Google'), /Conectado como ana@gmail.com/);
});

test('the photo is shown as a decorative image that does not leak the referrer, and can be removed', async () => {
    const state = startServer(LINKED, (path) => (path === '/api/v1/profile/avatar'
        ? { status: 204, profile: { avatarUrl: null, avatarHidden: true } }
        : null));
    const view = await renderProfile();

    const [image] = findByTag(cardOf(view, 'Dados'), 'img');
    assert.equal(image.getAttribute('src'), PHOTO);
    assert.equal(image.getAttribute('alt'), '');
    assert.equal(image.getAttribute('referrerpolicy'), 'no-referrer');
    assert.equal(image.getAttribute('width'), '64');
    assert.equal(image.getAttribute('height'), '64');

    await findByText(view, 'button', 'Remover foto').dispatch('click');

    assert.deepEqual(state.requests, [{ method: 'DELETE', path: '/api/v1/profile/avatar', body: undefined }]);
    assert.equal(findByTag(cardOf(view, 'Dados'), 'img').length, 0);
    assert.equal(findByText(view, 'button', 'Remover foto'), undefined);
    assert.equal(session.avatar, '');
});

test('after removing the photo, "Usar foto do Google" brings it back', async () => {
    const state = startServer({ ...LINKED, avatarUrl: null, avatarHidden: true }, (path) => (path === '/api/v1/profile/avatar/google'
        ? { status: 204, profile: { avatarUrl: PHOTO, avatarHidden: false } }
        : null));
    const view = await renderProfile();

    await findByText(view, 'button', 'Usar foto do Google').dispatch('click');

    assert.deepEqual(state.requests, [{ method: 'POST', path: '/api/v1/profile/avatar/google', body: undefined }]);
    assert.equal(findByTag(cardOf(view, 'Dados'), 'img').length, 1);
    assert.ok(findByText(view, 'button', 'Remover foto'));
});

test('without a photo the avatar shows the initials, and "Usar foto do Google" is offered only with a link', async () => {
    startServer({});
    const view = await renderProfile();

    assert.equal(byClass(cardOf(view, 'Dados'), 'avatar')[0].textContent, 'AP');
    assert.equal(findByText(view, 'button', 'Usar foto do Google'), undefined);
    assert.equal(findByText(view, 'button', 'Remover foto'), undefined);
});

test('a photo that fails to load is replaced by the initials', async () => {
    startServer(LINKED);
    const view = await renderProfile();
    const [image] = findByTag(cardOf(view, 'Dados'), 'img');

    await image.dispatch('error');

    assert.equal(byClass(cardOf(view, 'Dados'), 'avatar')[0].textContent, 'AP');
});
