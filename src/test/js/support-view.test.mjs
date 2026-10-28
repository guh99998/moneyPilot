import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll, findByTag, findByText } from './fake-dom.mjs';

installFakeDom();

const { renderSupport } = await import('../../main/resources/static/js/views/support.js');
const { session } = await import('../../main/resources/static/js/api.js');

let calls;

/** Fake backend: every call answers with the next response of `queue` and is recorded in `calls`. */
function startServer(queue) {
    calls = [];
    globalThis.fetch = async (url, options) => {
        calls.push({ path: new URL(url, 'http://localhost').pathname, options });
        const { status, body, headers } = queue.shift();
        return body === undefined
            ? new Response(null, { status, headers })
            : Response.json(body, { status, headers });
    };
}

beforeEach(() => {
    session.clear();
    window.location.hash = '';
});

function open() {
    return renderSupport();
}

const textarea = (view) => findByTag(view, 'textarea')[0];
const select = (view) => findByTag(view, 'select')[0];
const emailInput = (view) => findByTag(view, 'input').find((input) => input.getAttribute('type') === 'email');
const errorBoxOf = (view) => findAll(view, (node) => node.className.split(' ').includes('dialog-error'))[0];
const statusOf = (view) => findAll(view, (node) => node.getAttribute('role') === 'status')[0];

async function submit(view) {
    await findByTag(view, 'form')[0].dispatch('submit');
}

/** Text of the elements an input points to through aria-describedby. */
function describedBy(view, input) {
    const ids = (input.getAttribute('aria-describedby') || '').split(' ').filter(Boolean);
    return ids.map((id) => findAll(view, (node) => node.getAttribute('id') === id)[0]?.textContent);
}

const MESSAGE = 'O saldo da minha conta não bate com o extrato.';
const sentBody = (call = calls[0]) => JSON.parse(call.options.body);

test('without a session the form asks for the e-mail and offers the way back to the login', () => {
    const view = open();

    assert.ok(emailInput(view));
    assert.equal(emailInput(view).hasAttribute('required'), true);
    assert.ok(findByText(view, 'button', 'Voltar para o login'));
});

test('with a session the form has no e-mail field', () => {
    session.start('token-123', 'ana@example.com');
    const view = open();

    assert.equal(emailInput(view), undefined);
    assert.ok(select(view));
    assert.ok(textarea(view));
    assert.equal(findByText(view, 'button', 'Voltar para o login'), undefined);
});

test('the categories are Dúvida, Problema, Sugestão and Outro', () => {
    const labels = findByTag(open(), 'option').map((option) => option.textContent);

    assert.deepEqual(labels, ['Dúvida', 'Problema', 'Sugestão', 'Outro']);
});

test('the message limits the length and shows a live counter', async () => {
    const view = open();
    assert.equal(textarea(view).getAttribute('maxlength'), '2000');
    assert.deepEqual(describedBy(view, textarea(view)).slice(0, 1), ['0/2000']);

    textarea(view).value = 'olá';
    await textarea(view).dispatch('input');

    assert.deepEqual(describedBy(view, textarea(view)).slice(0, 1), ['3/2000']);
});

test('a logged-in ticket goes with the session token and without e-mail, and the number is shown', async () => {
    session.start('token-123', 'ana@example.com');
    startServer([{ status: 200, body: { reference: 'K7Q2M9', replyTo: 'ana@example.com' } }]);
    const view = open();
    select(view).value = 'PROBLEMA';
    textarea(view).value = MESSAGE;

    await submit(view);

    assert.equal(calls.length, 1);
    assert.equal(calls[0].path, '/api/v1/support/tickets');
    assert.equal(calls[0].options.headers.Authorization, 'Bearer token-123');
    assert.deepEqual(sentBody(), { category: 'PROBLEMA', message: MESSAGE });
    const status = statusOf(view);
    assert.match(status.textContent, /chamado K7Q2M9/);
    assert.match(status.textContent, /ana@example\.com/);
    assert.equal(globalThis.document.activeElement, status);
    assert.equal(findByTag(view, 'form').length, 0);
});

test('an anonymous ticket goes without a token, with the typed e-mail', async () => {
    startServer([{ status: 200, body: { reference: 'ABCD23', replyTo: 'joao@example.com' } }]);
    const view = open();
    select(view).value = 'SUGESTAO';
    textarea(view).value = MESSAGE;
    emailInput(view).value = ' joao@example.com ';

    await submit(view);

    assert.equal(calls[0].options.headers.Authorization, undefined);
    assert.deepEqual(sentBody(), { category: 'SUGESTAO', message: MESSAGE, email: 'joao@example.com' });
    assert.match(statusOf(view).textContent, /chamado ABCD23/);
});

test('field errors are linked to their fields, flagged as invalid and keep what was typed', async () => {
    startServer([{
        status: 400,
        body: {
            message: 'Validation failed',
            fieldErrors: { message: 'A mensagem deve ter de 10 a 2.000 caracteres.', email: 'Informe um e-mail válido.' }
        }
    }]);
    const view = open();
    textarea(view).value = 'curta';
    emailInput(view).value = 'ana@example';

    await submit(view);

    assert.equal(textarea(view).getAttribute('aria-invalid'), 'true');
    assert.equal(emailInput(view).getAttribute('aria-invalid'), 'true');
    assert.ok(describedBy(view, textarea(view)).includes('A mensagem deve ter de 10 a 2.000 caracteres.'));
    assert.deepEqual(describedBy(view, emailInput(view)), ['Informe um e-mail válido.']);
    assert.equal(errorBoxOf(view).hidden, true);
    assert.equal(textarea(view).value, 'curta');
    assert.equal(emailInput(view).value, 'ana@example');
});

test('a send failure (502) shows an announced error and keeps the text for another try', async () => {
    startServer([
        { status: 502, body: { message: 'Não foi possível enviar o e-mail. Tente novamente.' } },
        { status: 200, body: { reference: 'K7Q2M9', replyTo: 'joao@example.com' } }
    ]);
    const view = open();
    textarea(view).value = MESSAGE;
    emailInput(view).value = 'joao@example.com';

    await submit(view);

    const errorBox = errorBoxOf(view);
    assert.equal(errorBox.hidden, false);
    assert.equal(errorBox.getAttribute('role'), 'alert');
    assert.match(errorBox.textContent, /Não foi possível enviar/);
    assert.equal(textarea(view).value, MESSAGE);
    assert.equal(findByTag(view, 'button').find((button) => button.getAttribute('type') === 'submit').disabled, false);

    await submit(view);

    assert.equal(calls.length, 2);
    assert.match(statusOf(view).textContent, /chamado K7Q2M9/);
});

test('too many tickets (429) show the waiting time and keep the text', async () => {
    startServer([{ status: 429, body: { message: 'Too many' }, headers: { 'Retry-After': '1800' } }]);
    const view = open();
    textarea(view).value = MESSAGE;
    emailInput(view).value = 'joao@example.com';

    await submit(view);

    assert.match(errorBoxOf(view).textContent, /30 minutos/);
    assert.equal(textarea(view).value, MESSAGE);
});

test('a double submit sends a single ticket', async () => {
    startServer([{ status: 200, body: { reference: 'K7Q2M9', replyTo: 'joao@example.com' } }]);
    const view = open();
    textarea(view).value = MESSAGE;
    emailInput(view).value = 'joao@example.com';

    await Promise.all([submit(view), submit(view)]);

    assert.equal(calls.length, 1);
});

test('while sending, the button is disabled and says so', async () => {
    let release;
    globalThis.fetch = () => new Promise((resolve) => {
        release = () => resolve(Response.json({ reference: 'K7Q2M9', replyTo: 'joao@example.com' }));
    });
    const view = open();
    textarea(view).value = MESSAGE;
    emailInput(view).value = 'joao@example.com';
    const button = findByTag(view, 'button').find((node) => node.getAttribute('type') === 'submit');

    const pending = submit(view);

    assert.equal(button.disabled, true);
    assert.equal(button.textContent, 'Enviando…');
    release();
    await pending;
});

test('the way back to the login clears the hash', async () => {
    window.location.hash = '#/suporte';
    const view = open();

    await findByText(view, 'button', 'Voltar para o login').dispatch('click');

    assert.equal(window.location.hash, '');
});
