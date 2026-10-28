import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll, findByTag } from './fake-dom.mjs';

installFakeDom();

const { renderProfile } = await import('../../main/resources/static/js/views/profile.js');

const PROFILE = {
    name: 'Ana Paula', email: 'ana@example.com', createdAt: '2026-01-10T12:00:00Z', pendingEmail: null,
    hasPassword: true, googleEmail: null, avatarUrl: null, avatarHidden: false
};
const LEAKED = 'Essa senha aparece em listas públicas de senhas vazadas na internet. Escolha outra, de preferência uma frase.';

/** Fake backend: the password list, the profile on GET /profile, and `onPost(path)` for everything else. */
function startServer(onPost = () => ({ status: 204 })) {
    globalThis.fetch = async (url, options = {}) => {
        const path = new URL(url, 'http://localhost').pathname;
        if (path === '/data/common-passwords.txt') return new Response('senha12345\n');
        if (path === '/api/v1/profile' && (options.method || 'GET') === 'GET') return Response.json(PROFILE);
        const { status, body } = onPost(path);
        return body === undefined ? new Response(null, { status }) : Response.json(body, { status });
    };
}

const byId = (view, id) => findAll(view, (node) => node.getAttribute('id') === id)[0];
const byClass = (view, name) => findAll(view, (node) => node.className === name);
const passwordForm = (view) => findByTag(view, 'form')[2];

test('the four password fields of the profile have the eye and their labels point to them', async () => {
    startServer();
    const view = await renderProfile();

    assert.equal(byClass(view, 'password-toggle').length, 4);

    const labelled = findByTag(view, 'label')
        .filter((label) => label.getAttribute('for')?.startsWith('profile-'))
        .map((label) => [label.textContent, label.getAttribute('for')]);
    assert.deepEqual(labelled.sort(), [
        ['Nova senha', 'profile-new-password'],
        ['Senha atual', 'profile-current-password'],
        ['Senha atual', 'profile-email-password'],
        ['Senha atual', 'profile-google-password']
    ].sort());
    labelled.forEach(([, id]) => assert.ok(byId(view, id), id));
});

test('only the new password has the meter, linked by aria-describedby with the hint and the error', async () => {
    startServer();
    const view = await renderProfile();

    const meters = byClass(view, 'strength');
    assert.equal(meters.length, 1);
    assert.deepEqual(byId(view, 'profile-new-password').getAttribute('aria-describedby').split(' '),
        ['profile-new-password-hint', 'profile-new-password-error', meters[0].getAttribute('id')]);
    assert.equal(byId(view, 'profile-new-password').getAttribute('minlength'), '10');
    assert.match(byId(view, 'profile-new-password-hint').textContent, /De 10 a 64 caracteres/);
});

test('the meter knows the account name and e-mail', async () => {
    startServer();
    const view = await renderProfile();
    const input = byId(view, 'profile-new-password');
    const [meter] = byClass(view, 'strength');

    input.value = 'tudo sobre a paula';
    await input.dispatch('input');
    assert.match(meter.textContent, /não pode conter seu e-mail nem seu nome/);

    input.value = 'o céu está azul hoje';
    await input.dispatch('input');
    assert.match(meter.textContent, /forte/);
});

test('the eye of the new password shows it and announces the state', async () => {
    startServer();
    const view = await renderProfile();
    const input = byId(view, 'profile-new-password');
    const eye = findByTag(input.parent, 'button')[0];

    await eye.dispatch('click');

    assert.equal(input.getAttribute('type'), 'text');
    assert.equal(eye.getAttribute('aria-pressed'), 'true');
    assert.equal(eye.getAttribute('aria-label'), 'Esconder senha');
});

test('a refusal from the server is shown as it came, under the new password field', async () => {
    startServer(() => ({ status: 400, body: { message: LEAKED, fieldErrors: { newPassword: LEAKED } } }));
    const view = await renderProfile();
    byId(view, 'profile-current-password').value = 'minha frase secreta';
    byId(view, 'profile-new-password').value = 'senha12345';

    await passwordForm(view).dispatch('submit');

    assert.equal(byId(view, 'profile-new-password-error').textContent, LEAKED);
});

test('the "different from the current one" answer keeps its own message', async () => {
    const message = 'New password must be different from the current one';
    startServer(() => ({ status: 400, body: { message, fieldErrors: { newPassword: message } } }));
    const view = await renderProfile();
    byId(view, 'profile-current-password').value = 'minha frase secreta';
    byId(view, 'profile-new-password').value = 'minha frase secreta';

    await passwordForm(view).dispatch('submit');

    assert.equal(byId(view, 'profile-new-password-error').textContent, 'A nova senha deve ser diferente da atual.');
});

test('after changing the password the fields and the meter are cleared', async () => {
    startServer();
    const view = await renderProfile();
    const input = byId(view, 'profile-new-password');
    const [meter] = byClass(view, 'strength');
    input.value = 'o céu está azul hoje';
    await input.dispatch('input');
    byId(view, 'profile-current-password').value = 'minha frase secreta';
    assert.equal(meter.hidden, false);

    await passwordForm(view).dispatch('submit');

    assert.equal(input.value, '');
    assert.equal(meter.hidden, true);
});
