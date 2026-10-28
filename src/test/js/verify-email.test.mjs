import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll } from './fake-dom.mjs';

installFakeDom();

const { renderVerifyEmail } = await import('../../main/resources/static/js/views/verify-email.js');

const INVALID_LINK_RESPONSE = { message: 'Link inválido ou expirado.' };

let confirmed;

/** Fake backend: `validTokens` activate the account, any other token gets the 400 the API sends. */
function startServer(validTokens) {
    confirmed = [];
    globalThis.fetch = async (url, options = {}) => {
        const path = new URL(url).pathname;
        if (path !== '/api/v1/auth/verification/confirm') throw new Error(`unexpected request to ${path}`);

        const { token } = JSON.parse(options.body);
        confirmed.push(token);
        return validTokens.includes(token)
            ? new Response(null, { status: 204 })
            : Response.json(INVALID_LINK_RESPONSE, { status: 400 });
    };
}

/** Opens the screen like the router does and waits for the confirmation call to settle. */
async function openLink(hash) {
    window.location.hash = hash;
    const view = renderVerifyEmail();
    await new Promise((resolve) => setTimeout(resolve, 0));
    return view;
}

function statusOf(view) {
    return findAll(view, (node) => node.className.split(' ').includes('auth-tagline'))[0];
}

test('a second link opened in the same tab is confirmed with its own token', async () => {
    startServer(['token-a']);

    const first = await openLink('#/verificar-email?token=token-a');
    assert.match(statusOf(first).textContent, /Conta verificada/);

    const second = await openLink('#/verificar-email?token=token-b');

    assert.deepEqual(confirmed, ['token-a', 'token-b']);
    assert.match(statusOf(second).textContent, /Link inválido ou expirado/);
});

test('rendering again without a token reuses the result and does not spend the link twice', async () => {
    startServer(['token-c']);

    await openLink('#/verificar-email?token=token-c');
    const again = await openLink('#/verificar-email');

    assert.deepEqual(confirmed, ['token-c']);
    assert.match(statusOf(again).textContent, /Conta verificada/);
});
