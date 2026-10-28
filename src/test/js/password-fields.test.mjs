import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll, findByTag, findByText } from './fake-dom.mjs';

installFakeDom();

const { renderAuth } = await import('../../main/resources/static/js/views/auth.js');
const { renderResetPassword } = await import('../../main/resources/static/js/views/reset-password.js');

const LEAKED = 'Essa senha aparece em listas públicas de senhas vazadas na internet. Escolha outra, de preferência uma frase.';

/** Fake backend: serves the password list, says Google is off, and answers the other calls with `answer()`. */
function startServer(answer = () => ({ status: 204 })) {
    let sent = 0;
    globalThis.fetch = async (url) => {
        if (new URL(url, 'http://localhost').pathname === '/data/common-passwords.txt') {
            return new Response('senha12345\n');
        }
        if (new URL(url, 'http://localhost').pathname === '/api/v1/auth/google/config') {
            return Response.json({ clientId: '' });
        }
        sent += 1;
        const { status, body } = answer();
        return body === undefined ? new Response(null, { status }) : Response.json(body, { status });
    };
    return () => sent;
}

const byClass = (view, name) => findAll(view, (node) => node.className === name);
const passwordIn = (view, autocomplete) => findByTag(view, 'input')
    .find((input) => input.getAttribute('autocomplete') === autocomplete);
const describedBy = (view, input) => (input.getAttribute('aria-describedby') || '').split(' ').filter(Boolean)
    .map((id) => findAll(view, (node) => node.getAttribute('id') === id)[0]?.textContent);

async function switchToRegister(view) {
    await findByText(view, 'button', 'Criar agora').dispatch('click');
}

// ---------- login e cadastro ----------

test('login has the eye on the password field and no strength meter', () => {
    startServer();
    const view = renderAuth(() => {});

    assert.equal(byClass(view, 'password-toggle').length, 1);
    assert.equal(byClass(view, 'strength').length, 0);
    assert.equal(passwordIn(view, 'current-password').getAttribute('minlength'), null);
});

test('the eye shows and hides what was typed in the login password', async () => {
    startServer();
    const view = renderAuth(() => {});
    const [eye] = byClass(view, 'password-toggle');
    const password = passwordIn(view, 'current-password');

    await eye.dispatch('click');

    assert.equal(password.getAttribute('type'), 'text');
    assert.equal(eye.getAttribute('aria-pressed'), 'true');
    assert.equal(eye.getAttribute('aria-label'), 'Esconder senha');
});

test('register asks for 10 to 64 characters, with the eye and a meter linked to the field', async () => {
    startServer();
    const view = renderAuth(() => {});
    await switchToRegister(view);

    const password = passwordIn(view, 'new-password');
    const [meter] = byClass(view, 'strength');

    assert.equal(password.getAttribute('minlength'), '10');
    assert.equal(password.getAttribute('maxlength'), '64');
    assert.equal(byClass(view, 'password-toggle').length, 1);
    assert.equal(meter.hidden, true);
    assert.ok(password.getAttribute('aria-describedby').split(' ').includes(meter.getAttribute('id')));
    assert.ok(describedBy(view, password).includes('De 10 a 64 caracteres.'));
});

test('the register meter changes while typing and uses the name and e-mail already in the form', async () => {
    startServer();
    const view = renderAuth(() => {});
    await switchToRegister(view);
    findByTag(view, 'input')[0].value = 'Ana Paula';
    findByTag(view, 'input').find((input) => input.getAttribute('type') === 'email').value = 'ana@example.com';
    const password = passwordIn(view, 'new-password');
    const [meter] = byClass(view, 'strength');

    password.value = 'curta';
    await password.dispatch('input');
    assert.equal(meter.hidden, false);
    assert.match(meter.textContent, /fraca/);

    password.value = 'tudo sobre a paula';
    await password.dispatch('input');
    assert.match(meter.textContent, /não pode conter seu e-mail nem seu nome/);

    password.value = 'o céu está azul hoje';
    await password.dispatch('input');
    assert.match(meter.textContent, /forte/);
});

test('the meter only informs: a weak password is still sent and the server message is shown on the field', async () => {
    const sent = startServer(() => ({
        status: 400,
        body: { message: 'Validation failed', fieldErrors: { password: LEAKED } }
    }));
    const view = renderAuth(() => {});
    await switchToRegister(view);
    findByTag(view, 'input')[0].value = 'Ana';
    findByTag(view, 'input').find((input) => input.getAttribute('type') === 'email').value = 'ana@example.com';
    const password = passwordIn(view, 'new-password');
    password.value = 'curta';

    await findByTag(view, 'form')[0].dispatch('submit');

    assert.equal(sent(), 1);
    assert.ok(describedBy(view, password).includes(LEAKED));
});

test('the register labels point to the password input that now sits inside the field', async () => {
    startServer();
    const view = renderAuth(() => {});
    await switchToRegister(view);

    const label = findByTag(view, 'label').find((node) => node.textContent === 'Senha');
    const target = findAll(view, (node) => node.tagName === 'INPUT' && node.getAttribute('id') === label.getAttribute('for'))[0];

    assert.equal(target.getAttribute('autocomplete'), 'new-password');
});

// ---------- reset ----------

function openReset(token) {
    window.location.hash = `#/redefinir-senha?token=${token}`;
    return renderResetPassword();
}

test('reset: the new password has the eye, asks for 10 to 64 characters and has a meter linked to it', () => {
    startServer();
    const view = openReset('token-meter');
    const input = passwordIn(view, 'new-password');
    const [meter] = byClass(view, 'strength');

    assert.equal(input.getAttribute('minlength'), '10');
    assert.equal(byClass(view, 'password-toggle').length, 1);
    assert.ok(input.getAttribute('aria-describedby').split(' ').includes(meter.getAttribute('id')));
    assert.ok(describedBy(view, input).includes('De 10 a 64 caracteres.'));
});

test('reset: the meter reacts to what is typed and names public lists for a leaked password', async () => {
    startServer();
    const view = openReset('token-meter-2');
    const input = passwordIn(view, 'new-password');
    const [meter] = byClass(view, 'strength');

    input.value = 'senha12345';
    await input.dispatch('input');
    assert.match(meter.textContent, /fraca/);
    assert.match(meter.textContent, /listas públicas de senhas vazadas na internet/);

    input.value = 'uma frase bem comprida';
    await input.dispatch('input');
    assert.match(meter.textContent, /forte/);
});
