import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll, findByTag, findByText } from './fake-dom.mjs';

installFakeDom();

const { renderAuth } = await import('../../main/resources/static/js/views/auth.js');
const { session } = await import('../../main/resources/static/js/api.js');

// The login and register screens ask whether Google is on. It is off unless a test turns it on.
let googleClientId = '';
const googleOff = (url) => new URL(url, 'http://localhost').pathname === '/api/v1/auth/google/config'
    ? Response.json({ clientId: googleClientId })
    : null;

/** Fake backend: every call (but the Google config) answers with the next response of `queue`. */
function startServer(queue) {
    globalThis.fetch = async (url) => {
        const off = googleOff(url);
        if (off) return off;
        const { status, body } = queue.shift();
        return body === undefined ? new Response(null, { status }) : Response.json(body, { status });
    };
}

function open() {
    return renderAuth(() => {});
}

function inputOfType(view, type) {
    return findByTag(view, 'input').find((input) => input.getAttribute('type') === type);
}

function errorBoxOf(view) {
    return findAll(view, (node) => node.className.split(' ').includes('dialog-error'))[0];
}

/** Text of the elements an input points to through aria-describedby. */
function describedBy(view, input) {
    const ids = (input.getAttribute('aria-describedby') || '').split(' ').filter(Boolean);
    return ids.map((id) => findAll(view, (node) => node.getAttribute('id') === id)[0]?.textContent);
}

async function switchToRegister(view) {
    await findByText(view, 'button', 'Criar agora').dispatch('click');
}

async function submit(view) {
    await findByTag(view, 'form')[0].dispatch('submit');
}

function fillLogin(view) {
    inputOfType(view, 'email').value = 'ana@example.com';
    inputOfType(view, 'password').value = 'segredo-123';
}

test('logging in to an unverified account shows an announced error and offers a new link', async () => {
    startServer([{ status: 403, body: { message: 'Conta não ativada.' } }]);
    const view = open();
    fillLogin(view);

    await submit(view);

    const errorBox = errorBoxOf(view);
    assert.equal(errorBox.hidden, false);
    assert.equal(errorBox.getAttribute('role'), 'alert');
    assert.match(errorBox.textContent, /ainda não foi ativada/);
    assert.ok(findByText(view, 'button', 'Reenviar link de ativação'));
});

test('field errors are linked to their inputs and flagged as invalid', async () => {
    startServer([{
        status: 400,
        body: { message: 'Dados inválidos.', fieldErrors: { email: 'E-mail inválido.', password: 'Senha fraca.' } }
    }]);
    const view = open();
    await switchToRegister(view);
    findByTag(view, 'input').find((input) => input.getAttribute('type') === 'text').value = 'Ana';
    fillLogin(view);

    await submit(view);

    const email = inputOfType(view, 'email');
    const password = inputOfType(view, 'password');
    assert.equal(email.getAttribute('aria-invalid'), 'true');
    assert.ok(describedBy(view, email).includes('E-mail inválido.'));
    assert.equal(password.getAttribute('aria-invalid'), 'true');
    assert.ok(describedBy(view, password).includes('Senha fraca.'));
    assert.ok(describedBy(view, password).includes('De 10 a 64 caracteres.'));
    assert.equal(findByTag(view, 'input')[0].getAttribute('aria-invalid'), null);
});

test('the invalid flag is cleared when the next attempt fails for another reason', async () => {
    startServer([
        { status: 400, body: { message: 'Dados inválidos.', fieldErrors: { email: 'E-mail inválido.' } } },
        { status: 500, body: { message: 'Tente de novo.' } }
    ]);
    const view = open();
    await switchToRegister(view);
    fillLogin(view);

    await submit(view);
    await submit(view);

    assert.equal(inputOfType(view, 'email').getAttribute('aria-invalid'), null);
    assert.equal(errorBoxOf(view).textContent, 'Tente de novo.');
});

/** [label text, type of the input the label points to] for every label on the screen. */
function labelTargets(view) {
    return findByTag(view, 'label').map((label) => {
        const target = label.getAttribute('for');
        const input = target && findAll(view, (node) => node.tagName === 'INPUT' && node.getAttribute('id') === target)[0];
        return [label.textContent, input?.getAttribute('type')];
    });
}

test('every label points to the input it names, in login and register', async () => {
    const view = open();

    assert.deepEqual(labelTargets(view), [['E-mail', 'email'], ['Senha', 'password']]);

    await switchToRegister(view);

    assert.deepEqual(labelTargets(view), [['Nome', 'text'], ['E-mail', 'email'], ['Senha', 'password']]);
});

test('forgot password sends the typed email and shows the generic answer', async () => {
    let sent;
    globalThis.fetch = async (url, options = {}) => {
        const off = googleOff(url);
        if (off) return off;
        sent = { path: new URL(url).pathname, body: JSON.parse(options.body) };
        return new Response(null, { status: 202 });
    };
    const view = open();
    inputOfType(view, 'email').value = 'ana@example.com';

    await findByText(view, 'button', 'Redefinir').dispatch('click');
    assert.equal(inputOfType(view, 'email').value, 'ana@example.com');
    await submit(view);

    assert.deepEqual(sent, { path: '/api/v1/auth/password/forgot', body: { email: 'ana@example.com' } });
    const status = findAll(view, (node) => node.getAttribute('role') === 'status')[0];
    assert.match(status.textContent, /Se houver uma conta com ana@example.com/);
});

test('a refused forgot request is announced and the form stays', async () => {
    startServer([{ status: 429, body: { message: 'Muitas tentativas.' } }]);
    const view = open();
    await findByText(view, 'button', 'Redefinir').dispatch('click');
    inputOfType(view, 'email').value = 'ana@example.com';

    await submit(view);

    const errorBox = errorBoxOf(view);
    assert.equal(errorBox.hidden, false);
    assert.equal(errorBox.getAttribute('role'), 'alert');
    assert.ok(findByText(view, 'button', 'Enviar link'));
});

test('after asking for a reset, focus moves to the generic answer so it is read out', async () => {
    startServer([{ status: 202 }]);
    const view = open();
    await findByText(view, 'button', 'Redefinir').dispatch('click');
    inputOfType(view, 'email').value = 'ana@example.com';

    await submit(view);

    const status = findAll(view, (node) => node.getAttribute('role') === 'status')[0];
    assert.equal(document.activeElement, status);
});

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));
const googleBlock = (view) => findAll(view, (node) => node.className === 'google-block')[0];

/** Turns Google on with a stand-in script; returns what the screen handed to it. */
function turnGoogleOn(t) {
    const calls = { initialize: null, render: null };
    googleClientId = 'meu-app.apps.googleusercontent.com';
    globalThis.google = {
        accounts: {
            id: {
                initialize: (options) => { calls.initialize = options; },
                renderButton: (slot, options) => { calls.render = options; }
            }
        }
    };
    t.after(() => {
        googleClientId = '';
        delete globalThis.google;
    });
    return calls;
}

test('the login always tells who signed up with Google where to go, and hides the button while Google is off', async () => {
    const view = open();
    await settle();

    assert.ok(findAll(view, (node) => node.className === 'auth-note'
        && node.textContent === 'Criou a conta com o Google? Use Entrar com Google.')[0]);
    assert.equal(googleBlock(view).hidden, true);
});

test('with Google on, the login shows the "ou" separator and the sign-in button', async (t) => {
    const calls = turnGoogleOn(t);
    const view = open();
    await settle();

    assert.equal(googleBlock(view).hidden, false);
    assert.equal(calls.render.text, 'signin_with');
    assert.equal(findAll(view, (node) => node.className === 'auth-or')[0].getAttribute('aria-hidden'), 'true');
});

test('the register screen offers the sign-up button, without the login hint', async (t) => {
    const calls = turnGoogleOn(t);
    const view = open();
    await switchToRegister(view);
    await settle();

    assert.equal(googleBlock(view).hidden, false);
    assert.equal(calls.render.text, 'signup_with');
    assert.equal(findAll(view, (node) => node.className === 'auth-note').length, 0);
});

test('a Google credential logs in the same way as a password does', async (t) => {
    const calls = turnGoogleOn(t);
    let authenticated = 0;
    let sent;
    globalThis.fetch = async (url, options = {}) => {
        const off = googleOff(url);
        if (off) return off;
        sent = { path: new URL(url).pathname, body: JSON.parse(options.body) };
        return Response.json({ token: 'access-token', email: 'ana@example.com' });
    };
    const view = renderAuth(() => { authenticated += 1; });
    await settle();

    await calls.initialize.callback({ credential: 'id-token' });

    assert.deepEqual(sent, { path: '/api/v1/auth/google', body: { credential: 'id-token' } });
    assert.equal(session.token, 'access-token');
    assert.equal(session.email, 'ana@example.com');
    assert.equal(authenticated, 1);
    assert.equal(errorBoxOf(view).hidden, true);
    session.clear();
});

test('an e-mail linked to another Google account is announced in the alert box', async (t) => {
    const calls = turnGoogleOn(t);
    globalThis.fetch = async (url) => googleOff(url)
        || Response.json({ message: 'This e-mail belongs to an account linked to another Google account' }, { status: 409 });
    let authenticated = 0;
    const view = renderAuth(() => { authenticated += 1; });
    await settle();

    await calls.initialize.callback({ credential: 'id-token' });

    const errorBox = errorBoxOf(view);
    assert.equal(errorBox.hidden, false);
    assert.equal(errorBox.getAttribute('role'), 'alert');
    assert.match(errorBox.textContent, /vinculada a outra conta Google/);
    assert.equal(authenticated, 0);
});

test('the login offers a way to talk to support, and the register screen does not', async () => {
    const view = open();

    await findByText(view, 'button', 'Falar com o suporte').dispatch('click');

    assert.equal(window.location.hash, '#/suporte');

    const register = open();
    await switchToRegister(register);
    assert.equal(findByText(register, 'button', 'Falar com o suporte'), undefined);
});

test('the login links to the "Usando com IA" guide, and the register screen does not', async () => {
    const view = open();

    await findByText(view, 'button', 'Usar com IA').dispatch('click');

    assert.equal(window.location.hash, '#/usando-com-ia');

    const register = open();
    await switchToRegister(register);
    assert.equal(findByText(register, 'button', 'Usar com IA'), undefined);
});
