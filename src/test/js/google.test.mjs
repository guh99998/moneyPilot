import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll } from './fake-dom.mjs';

installFakeDom();

const { googleButton, googleErrorMessage } = await import('../../main/resources/static/js/google.js');
const { ApiError } = await import('../../main/resources/static/js/api.js');

const CLIENT_ID = 'meu-app.apps.googleusercontent.com';
const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

function serveConfig(clientId) {
    globalThis.fetch = async (url) => {
        assert.equal(new URL(url, 'http://localhost').pathname, '/api/v1/auth/google/config');
        return Response.json({ clientId });
    };
}

/** A stand-in for the Google script: remembers how it was initialized and where the button was drawn. */
function installGoogle() {
    const calls = { initialize: null, render: null };
    globalThis.google = {
        accounts: {
            id: {
                initialize: (options) => { calls.initialize = options; },
                renderButton: (slot, options) => { calls.render = { slot, options }; }
            }
        }
    };
    return calls;
}

beforeEach(() => {
    delete globalThis.google;
    document.body.children.length = 0;
});

test('the button stays hidden when the server has no client ID, and the script is never loaded', async () => {
    serveConfig('');

    const block = googleButton({ onCredential() {} });
    await settle();

    assert.equal(block.hidden, true);
    assert.equal(findAll(document.body, (node) => node.tagName === 'SCRIPT').length, 0);
});

test('the button stays hidden when the Google script is blocked', async () => {
    serveConfig(CLIENT_ID);

    const block = googleButton({ onCredential() {} });
    await settle();
    const [script] = findAll(document.body, (node) => node.tagName === 'SCRIPT');
    assert.equal(script.getAttribute('src'), 'https://accounts.google.com/gsi/client');
    assert.equal(block.hidden, true);

    await script.dispatch('error');
    await settle();

    assert.equal(block.hidden, true);
});

test('the button stays hidden when the config cannot be fetched', async () => {
    globalThis.fetch = async () => { throw new TypeError('offline'); };

    const block = googleButton({ onCredential() {} });
    await settle();

    assert.equal(block.hidden, true);
});

test('with a client ID and the script, the official button is drawn and the credential is handed over', async () => {
    serveConfig(CLIENT_ID);
    const calls = installGoogle();
    const received = [];

    const block = googleButton({ text: 'signup_with', separator: true, onCredential: (credential) => received.push(credential) });
    await settle();

    assert.equal(block.hidden, false);
    assert.equal(calls.initialize.client_id, CLIENT_ID);
    assert.equal(calls.render.options.text, 'signup_with');
    assert.equal(calls.render.options.locale, 'pt-BR');
    assert.equal(findAll(block, (node) => node.className === 'auth-or').length, 1);

    calls.initialize.callback({ credential: 'id-token' });
    assert.deepEqual(received, ['id-token']);
});

test('without the separator only the button shows', async () => {
    serveConfig(CLIENT_ID);
    installGoogle();

    const block = googleButton({ onCredential() {} });
    await settle();

    assert.equal(findAll(block, (node) => node.className === 'auth-or').length, 0);
});

test('errors are explained in Portuguese, by status', () => {
    const conflict = (message) => new ApiError(409, message);

    assert.match(googleErrorMessage(conflict('This e-mail belongs to an account linked to another Google account')),
        /outra conta Google/);
    assert.match(googleErrorMessage(conflict('This Google account is already linked to another account')),
        /já está vinculada a outra conta/);
    assert.match(googleErrorMessage(conflict('This account is already linked to Google. Unlink it first')),
        /Desvincule antes/);
    assert.match(googleErrorMessage(conflict('Create a password before unlinking Google')), /Crie uma senha/);
    assert.match(googleErrorMessage(new ApiError(401, 'Invalid Google credential')), /Não foi possível entrar com o Google/);
    assert.equal(googleErrorMessage(new ApiError(429, 'Muitas tentativas. Tente de novo em 5 minutos.')),
        'Muitas tentativas. Tente de novo em 5 minutos.');
    assert.match(googleErrorMessage(new Error('boom')), /Tente de novo/);
});
