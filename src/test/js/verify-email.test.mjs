import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

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

test('the activation result is announced to screen readers', async () => {
    startServer(['token-d']);

    const view = await openLink('#/verificar-email?token=token-d');

    assert.equal(statusOf(view).getAttribute('role'), 'status');
    assert.equal(statusOf(view).getAttribute('aria-live'), 'polite');
    assert.match(statusOf(view).textContent, /Conta verificada/);
});

test('a failed activation is marked as an error and the stylesheet gives it a look', async () => {
    startServer([]);

    const view = await openLink('#/verificar-email?token=token-e');

    assert.equal(statusOf(view).classList.contains('error'), true);
    const css = readFileSync(new URL('../../main/resources/static/css/app.css', import.meta.url), 'utf8');
    assert.match(css, /\.auth-tagline\.error\s*\{/);
});

test('the resend form label points to its e-mail input', async () => {
    startServer([]);

    const view = await openLink('#/verificar-email?token=token-f');

    const [label] = findAll(view, (node) => node.tagName === 'LABEL');
    const target = label.getAttribute('for');
    const input = findAll(view, (node) => node.tagName === 'INPUT' && node.getAttribute('id') === target)[0];
    assert.ok(target);
    assert.equal(input.getAttribute('type'), 'email');
});
