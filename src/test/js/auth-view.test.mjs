import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll, findByTag, findByText } from './fake-dom.mjs';

installFakeDom();

const { renderAuth } = await import('../../main/resources/static/js/views/auth.js');

/** Fake backend: every call to `path` answers with the next response of `queue`. */
function startServer(queue) {
    globalThis.fetch = async () => {
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
    assert.ok(describedBy(view, password).includes('Mínimo de 8 caracteres.'));
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
