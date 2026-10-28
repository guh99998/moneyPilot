import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll, findByTag, findByText } from './fake-dom.mjs';

installFakeDom();

const { renderResetPassword } = await import('../../main/resources/static/js/views/reset-password.js');

let requests;

/** Fake backend: answers each call with the next response of `queue` and records what was sent. */
function startServer(queue) {
    requests = [];
    globalThis.fetch = async (url, options = {}) => {
        requests.push({ path: new URL(url).pathname, body: options.body && JSON.parse(options.body) });
        const { status, body } = queue.shift();
        return body === undefined ? new Response(null, { status }) : Response.json(body, { status });
    };
}

function openLink(hash) {
    window.location.hash = hash;
    return renderResetPassword();
}

function statusOf(view) {
    return findAll(view, (node) => node.getAttribute('role') === 'status')[0];
}

async function submitPassword(view, password) {
    findByTag(view, 'input').find((input) => input.getAttribute('type') === 'password').value = password;
    await findByTag(view, 'form')[0].dispatch('submit');
}

test('the token leaves the address bar and is the one sent with the new password', async () => {
    startServer([{ status: 204 }]);
    const view = openLink('#/redefinir-senha?token=token-a');

    assert.equal(window.location.hash, '#/redefinir-senha');

    await submitPassword(view, 'nova-senha-678');

    assert.deepEqual(requests, [{
        path: '/api/v1/auth/password/reset',
        body: { token: 'token-a', newPassword: 'nova-senha-678' }
    }]);
    assert.match(statusOf(view).textContent, /Senha alterada/);
    assert.ok(findByText(view, 'button', 'Entrar'));
});

test('rendering again without the token in the URL keeps the link usable', async () => {
    startServer([{ status: 204 }]);
    openLink('#/redefinir-senha?token=token-b');

    const again = openLink('#/redefinir-senha');
    await submitPassword(again, 'nova-senha-678');

    assert.equal(requests[0].body.token, 'token-b');
});

test('an expired or used link shows the error and a form to ask for another', async () => {
    startServer([
        { status: 400, body: { message: 'Invalid or expired password reset link' } },
        { status: 202 }
    ]);
    const view = openLink('#/redefinir-senha?token=token-c');

    await submitPassword(view, 'nova-senha-678');

    assert.match(statusOf(view).textContent, /Link inválido ou expirado/);
    assert.equal(statusOf(view).classList.contains('error'), true);
    const email = findByTag(view, 'input').find((input) => input.getAttribute('type') === 'email');
    email.value = 'ana@example.com';
    await findByTag(view, 'form')[0].dispatch('submit');
    assert.deepEqual(requests[1], { path: '/api/v1/auth/password/forgot', body: { email: 'ana@example.com' } });
});

test('a password the server refuses stays on the form, explained in Portuguese and focused', async () => {
    startServer([{ status: 400, body: { message: 'Validation failed', fieldErrors: { newPassword: 'size must be between 8 and 2147483647' } } }]);
    const view = openLink('#/redefinir-senha?token=token-d');

    await submitPassword(view, 'curta');

    const input = findByTag(view, 'input').find((node) => node.getAttribute('type') === 'password');
    assert.equal(input.getAttribute('aria-invalid'), 'true');
    const described = input.getAttribute('aria-describedby').split(' ')
        .map((id) => findAll(view, (node) => node.getAttribute('id') === id)[0]?.textContent);
    assert.ok(described.includes('Mínimo de 8 caracteres.'));
    assert.ok(described.includes('A senha precisa ter pelo menos 8 caracteres.'));
    assert.equal(document.activeElement, input);
});

test('after a successful reset, opening the screen without a token asks for a new link', async () => {
    startServer([{ status: 204 }]);
    await submitPassword(openLink('#/redefinir-senha?token=token-used'), 'nova-senha-678');

    const view = openLink('#/redefinir-senha');

    assert.match(statusOf(view).textContent, /Link inválido ou expirado/);
    assert.ok(findByTag(view, 'input').some((input) => input.getAttribute('type') === 'email'));
    assert.equal(requests.length, 1);
});

test('the new password label points to its input', () => {
    const view = openLink('#/redefinir-senha?token=token-e');

    const [label] = findByTag(view, 'label');
    const input = findAll(view, (node) => node.tagName === 'INPUT' && node.getAttribute('id') === label.getAttribute('for'))[0];
    assert.equal(input.getAttribute('type'), 'password');
});

test('after saving, focus moves to the result so it is read out', async () => {
    startServer([{ status: 204 }]);
    const view = openLink('#/redefinir-senha?token=token-focus');

    await submitPassword(view, 'nova-senha-678');

    assert.equal(document.activeElement, statusOf(view));
});
