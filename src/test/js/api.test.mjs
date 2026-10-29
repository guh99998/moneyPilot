import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';

// api.js runs in the browser: give it the few globals it touches.
const storage = new Map();
globalThis.localStorage = {
    getItem: (key) => (storage.has(key) ? storage.get(key) : null),
    setItem: (key, value) => storage.set(key, String(value)),
    removeItem: (key) => storage.delete(key)
};
const events = [];
globalThis.window = {
    location: { origin: 'http://localhost' },
    dispatchEvent: (event) => events.push(event.type)
};

const { api, session, ApiError } = await import('../../main/resources/static/js/api.js');

let calls;
let server;

/** Fake backend. `validToken` is the only access token /accounts accepts; refresh hands out `nextToken`. */
function startServer({ validToken, nextToken = 'fresh', refreshStatus = 200, refreshDelay = 0, refreshThrows = false, refreshedTokenIsAccepted = true }) {
    calls = { refresh: 0, accounts: [] };
    server = { validToken };

    globalThis.fetch = async (url, options = {}) => {
        const path = new URL(url).pathname;

        if (path === '/api/v1/auth/refresh') {
            calls.refresh++;
            if (refreshThrows) throw new TypeError('network down');
            await new Promise((resolve) => setTimeout(resolve, refreshDelay));
            if (refreshStatus !== 200) return new Response(null, { status: refreshStatus });
            if (refreshedTokenIsAccepted) server.validToken = nextToken;
            return Response.json({ token: nextToken });
        }

        if (path === '/api/v1/accounts') {
            const sent = options.headers?.Authorization?.replace('Bearer ', '') ?? null;
            calls.accounts.push(sent);
            if (sent !== server.validToken) return new Response(null, { status: 401 });
            return Response.json({ content: [] });
        }

        if (path === '/api/v1/auth/login') {
            return new Response(JSON.stringify({ message: 'Invalid credentials' }), {
                status: 401,
                headers: { 'Content-Type': 'application/json' }
            });
        }

        throw new Error(`unexpected request ${path}`);
    };
}

beforeEach(() => {
    storage.clear();
    events.length = 0;
});

test('four parallel calls with an expired token trigger exactly one refresh', async () => {
    session.start('expired', 'user@example.com');
    startServer({ validToken: 'valid-now', nextToken: 'valid-now', refreshDelay: 20 });

    const results = await Promise.all([
        api.accounts.list(), api.accounts.list(), api.accounts.list(), api.accounts.list()
    ]);

    assert.equal(results.length, 4);
    assert.equal(calls.refresh, 1, 'each extra refresh rotates the token and would look like reuse to the server');
    assert.equal(session.token, 'valid-now');
    assert.deepEqual(events, [], 'the session must not be reported as expired');
});

test('a call whose 401 arrives after the refresh finished reuses the new token instead of refreshing again', async () => {
    session.start('expired', 'user@example.com');
    startServer({ validToken: 'fresh' });

    // slow call: sent with the expired token, answered (401) only after the fast one already renewed the session
    const realFetch = globalThis.fetch;
    let releaseSlow;
    const slowGate = new Promise((resolve) => { releaseSlow = resolve; });
    let slowIntercepted = false;
    globalThis.fetch = async (url, options) => {
        const isFirstAccounts = new URL(url).pathname === '/api/v1/accounts' && !slowIntercepted;
        if (isFirstAccounts) {
            slowIntercepted = true;
            const response = await realFetch(url, options);
            await slowGate;
            return response;
        }
        return realFetch(url, options);
    };

    const slow = api.accounts.list();
    await api.accounts.list();
    assert.equal(calls.refresh, 1);

    releaseSlow();
    await slow;

    assert.equal(calls.refresh, 1, 'the late 401 must not start a second rotation');
});

test('the failed call is retried once with the new token and succeeds', async () => {
    session.start('expired', 'user@example.com');
    startServer({ validToken: 'fresh' });

    await api.accounts.list();

    assert.deepEqual(calls.accounts, ['expired', 'fresh']);
});

test('when the refresh is refused the session is cleared, reported as expired, and nothing loops', async () => {
    session.start('expired', 'user@example.com');
    startServer({ validToken: 'never', refreshStatus: 401 });

    await assert.rejects(api.accounts.list(), (error) => error instanceof ApiError && error.status === 401);

    assert.equal(calls.refresh, 1);
    assert.equal(session.active, false);
    assert.deepEqual(events, ['session:expired']);
});

test('when the retry itself is refused the session ends without a second refresh', async () => {
    session.start('expired', 'user@example.com');
    // refresh "succeeds" but hands out a token the API still rejects
    startServer({ validToken: 'something-else', nextToken: 'still-bad', refreshedTokenIsAccepted: false });

    await assert.rejects(api.accounts.list(), (error) => error.status === 401);

    assert.equal(calls.refresh, 1);
    assert.deepEqual(events, ['session:expired']);
});

test('losing the network during the refresh keeps the session', async () => {
    session.start('expired', 'user@example.com');
    startServer({ validToken: 'never', refreshThrows: true });

    await assert.rejects(api.accounts.list(), (error) => error.status === 0);

    assert.equal(session.token, 'expired');
    assert.deepEqual(events, []);
});

test('an anonymous 401 (wrong password) never triggers a refresh', async () => {
    startServer({ validToken: 'x' });

    await assert.rejects(api.login({ email: 'a@b.c', password: 'wrong' }), (error) => error.status === 401);

    assert.equal(calls.refresh, 0);
    assert.deepEqual(events, []);
});

test('two waves of expiry each get their own single refresh', async () => {
    session.start('expired', 'user@example.com');
    startServer({ validToken: 'wave-1', nextToken: 'wave-1' });
    await Promise.all([api.accounts.list(), api.accounts.list()]);
    assert.equal(calls.refresh, 1);

    // the token expires again later
    server.validToken = 'wave-2';
    globalThis.fetch = ((original) => async (url, options) => {
        if (new URL(url).pathname === '/api/v1/auth/refresh') server.validToken = 'wave-2';
        return original(url, options);
    })(globalThis.fetch);

    const before = calls.refresh;
    await Promise.all([api.accounts.list(), api.accounts.list(), api.accounts.list()]);
    assert.equal(calls.refresh - before, 1);
});

function tooManyAttempts(retryAfter) {
    globalThis.fetch = async () => new Response(JSON.stringify({ message: 'Too many attempts. Try again in 15 minutes.' }), {
        status: 429,
        headers: { 'Content-Type': 'application/json', ...(retryAfter === undefined ? {} : { 'Retry-After': String(retryAfter) }) }
    });
}

test('a 429 explains how long to wait, rounding the Retry-After up to whole minutes', async () => {
    tooManyAttempts(900);
    await assert.rejects(api.login({ email: 'a@b.com', password: 'x' }), (error) =>
        error.status === 429 && error.message === 'Muitas tentativas. Tente de novo em 15 minutos.');

    tooManyAttempts(61);
    await assert.rejects(api.login({ email: 'a@b.com', password: 'x' }), (error) =>
        error.message === 'Muitas tentativas. Tente de novo em 2 minutos.');
});

test('a 429 uses the singular for one minute and never reports zero', async () => {
    tooManyAttempts(30);
    await assert.rejects(api.register({}), (error) =>
        error.message === 'Muitas tentativas. Tente de novo em 1 minuto.');
});

test('a 429 without Retry-After still gives a readable message', async () => {
    tooManyAttempts();
    await assert.rejects(api.login({}), (error) =>
        error.status === 429 && error.message === 'Muitas tentativas. Tente de novo em instantes.');
});

test('session keeps the user name and clears it on logout', () => {
    session.start('token', 'user@example.com');
    assert.equal(session.name, null);

    session.setName('Maria Silva');
    assert.equal(session.name, 'Maria Silva');

    session.clear();
    assert.equal(session.name, null);
});

test('a wrong current password is a 400 with the field error, not an expired session', async () => {
    session.start('valid', 'user@example.com');
    const requests = [];
    globalThis.fetch = async (url, options = {}) => {
        requests.push({ path: new URL(url).pathname, method: options.method, body: options.body });
        return new Response(JSON.stringify({
            message: 'Current password is incorrect',
            fieldErrors: { currentPassword: 'Current password is incorrect' }
        }), { status: 400, headers: { 'Content-Type': 'application/json' } });
    };

    await assert.rejects(api.changePassword('errada', 'nova-senha-123'), (error) =>
        error instanceof ApiError
        && error.status === 400
        && error.fieldErrors.currentPassword === 'Current password is incorrect');

    assert.deepEqual(requests, [{
        path: '/api/v1/auth/password',
        method: 'POST',
        body: JSON.stringify({ currentPassword: 'errada', newPassword: 'nova-senha-123' })
    }]);
    assert.deepEqual(events, []);
    assert.equal(session.token, 'valid');
});

test('updating the profile sends only the name', async () => {
    session.start('valid', 'user@example.com');
    let sent;
    globalThis.fetch = async (url, options = {}) => {
        sent = { path: new URL(url).pathname, method: options.method, body: options.body };
        return Response.json({ name: 'Novo Nome', email: 'user@example.com', createdAt: '2026-10-27T15:00:00Z' });
    };

    const profile = await api.profile.update('Novo Nome');

    assert.deepEqual(sent, { path: '/api/v1/profile', method: 'PUT', body: JSON.stringify({ name: 'Novo Nome' }) });
    assert.equal(profile.name, 'Novo Nome');
});

test('alerts use their own routes', async () => {
    session.start('valid', 'user@example.com');
    const requests = [];
    globalThis.fetch = async (url, options = {}) => {
        requests.push({ path: new URL(url).pathname, method: options.method ?? 'GET', body: options.body });
        return options.method === 'POST' ? new Response(null, { status: 204 }) : Response.json({ count: 0, alerts: [] });
    };

    await api.alerts.list();
    await api.alerts.dismiss('PAYABLE_DUE_SOON');

    assert.deepEqual(requests, [
        { path: '/api/v1/alerts', method: 'GET', body: undefined },
        { path: '/api/v1/alerts/dismiss', method: 'POST', body: JSON.stringify({ key: 'PAYABLE_DUE_SOON' }) }
    ]);
});

test('a successful change announces api:changed; reads, failures and anonymous calls do not', async () => {
    session.start('valid', 'user@example.com');

    globalThis.fetch = async () => Response.json({ count: 0, alerts: [] });
    await api.alerts.list();
    assert.deepEqual(events, []);

    globalThis.fetch = async () => new Response(null, { status: 204 });
    await api.alerts.dismiss('PAYABLE_OVERDUE');
    assert.deepEqual(events, ['api:changed']);

    globalThis.fetch = async () => Response.json({ id: 1 }, { status: 201 });
    await api.accounts.create({ name: 'Conta' });
    assert.deepEqual(events, ['api:changed', 'api:changed']);

    events.length = 0;
    globalThis.fetch = async () => new Response(JSON.stringify({ message: 'nope' }), { status: 400, headers: { 'Content-Type': 'application/json' } });
    await assert.rejects(api.accounts.create({ name: '' }));
    assert.deepEqual(events, []);

    globalThis.fetch = async () => Response.json({ token: 't' });
    await api.login({ email: 'a@b.com', password: 'x' });
    assert.deepEqual(events, []);
});

test('requesting an email change posts the new address and the current password', async () => {
    session.start('valid', 'user@example.com');
    events.length = 0;
    let sent;
    globalThis.fetch = async (url, options = {}) => {
        sent = { path: new URL(url).pathname, method: options.method, body: options.body };
        return new Response(null, { status: 204 });
    };

    await api.requestEmailChange('novo@example.com', 'senha12345');

    assert.deepEqual(sent, {
        path: '/api/v1/profile/email',
        method: 'POST',
        body: JSON.stringify({ newEmail: 'novo@example.com', currentPassword: 'senha12345' })
    });
});

test('an email already in use is a 400 with the field error, not an expired session', async () => {
    session.start('valid', 'user@example.com');
    events.length = 0;
    globalThis.fetch = async () => new Response(JSON.stringify({
        message: 'E-mail already in use',
        fieldErrors: { newEmail: 'E-mail already in use' }
    }), { status: 400, headers: { 'Content-Type': 'application/json' } });

    await assert.rejects(api.requestEmailChange('x@example.com', 'senha12345'), (error) =>
        error instanceof ApiError && error.status === 400 && error.fieldErrors.newEmail === 'E-mail already in use');

    assert.deepEqual(events, []);
    assert.equal(session.token, 'valid');
});

test('confirming an email change is anonymous: a 400 never fires session:expired nor refreshes', async () => {
    session.clear();
    events.length = 0;
    const paths = [];
    let authorization;
    globalThis.fetch = async (url, options = {}) => {
        paths.push(new URL(url).pathname);
        authorization = options.headers?.Authorization;
        return new Response(JSON.stringify({ message: 'Invalid or expired e-mail change link' }),
            { status: 400, headers: { 'Content-Type': 'application/json' } });
    };

    await assert.rejects(api.confirmEmailChange('token-ruim'), (error) => error instanceof ApiError && error.status === 400);

    assert.deepEqual(paths, ['/api/v1/auth/email/confirm']);
    assert.equal(authorization, undefined);
    assert.deepEqual(events, []);
});

test('registering resolves with no body: the account still has to be verified', async () => {
    session.clear();
    events.length = 0;
    let sent;
    globalThis.fetch = async (url, options = {}) => {
        sent = { path: new URL(url).pathname, authorization: options.headers?.Authorization };
        return new Response(null, { status: 202 });
    };

    const result = await api.register({ name: 'Maria', email: 'maria@example.com', password: 'senha12345' });

    assert.equal(result, null);
    assert.deepEqual(sent, { path: '/api/v1/auth/register', authorization: undefined });
    assert.equal(session.active, false);
});

test('logging in to an unverified account is a 403, not an expired session', async () => {
    session.clear();
    events.length = 0;
    const paths = [];
    globalThis.fetch = async (url) => {
        paths.push(new URL(url).pathname);
        return new Response(JSON.stringify({ status: 403, message: 'E-mail not verified' }),
            { status: 403, headers: { 'Content-Type': 'application/json' } });
    };

    await assert.rejects(api.login({ email: 'maria@example.com', password: 'senha12345' }),
        (error) => error instanceof ApiError && error.status === 403);

    assert.deepEqual(paths, ['/api/v1/auth/login']);
    assert.deepEqual(events, []);
});

test('resending the verification link posts only the email, without a session', async () => {
    session.start('valid', 'other@example.com');
    events.length = 0;
    let sent;
    globalThis.fetch = async (url, options = {}) => {
        sent = { path: new URL(url).pathname, body: options.body, authorization: options.headers?.Authorization };
        return new Response(null, { status: 202 });
    };

    await api.resendVerification('maria@example.com');

    assert.deepEqual(sent, {
        path: '/api/v1/auth/verification/resend',
        body: JSON.stringify({ email: 'maria@example.com' }),
        authorization: undefined
    });
    assert.deepEqual(events, []);
});

test('confirming a verification link is anonymous: a 400 never fires session:expired nor refreshes', async () => {
    session.start('valid', 'other@example.com');
    events.length = 0;
    const paths = [];
    let authorization;
    globalThis.fetch = async (url, options = {}) => {
        paths.push(new URL(url).pathname);
        authorization = options.headers?.Authorization;
        return new Response(JSON.stringify({ message: 'Invalid or expired verification link' }),
            { status: 400, headers: { 'Content-Type': 'application/json' } });
    };

    await assert.rejects(api.confirmVerification('token-ruim'), (error) => error instanceof ApiError && error.status === 400);

    assert.deepEqual(paths, ['/api/v1/auth/verification/confirm']);
    assert.equal(authorization, undefined);
    assert.deepEqual(events, []);
    assert.equal(session.token, 'valid');
});

test('asking for a password reset posts only the email, without a session', async () => {
    session.start('valid', 'other@example.com');
    events.length = 0;
    let sent;
    globalThis.fetch = async (url, options = {}) => {
        sent = { path: new URL(url).pathname, body: options.body, authorization: options.headers?.Authorization };
        return new Response(null, { status: 202 });
    };

    await api.forgotPassword('maria@example.com');

    assert.deepEqual(sent, {
        path: '/api/v1/auth/password/forgot',
        body: JSON.stringify({ email: 'maria@example.com' }),
        authorization: undefined
    });
    assert.deepEqual(events, []);
});

test('resetting the password is anonymous: a 400 never fires session:expired nor refreshes', async () => {
    session.start('valid', 'other@example.com');
    events.length = 0;
    const sent = [];
    globalThis.fetch = async (url, options = {}) => {
        sent.push({ path: new URL(url).pathname, body: options.body, authorization: options.headers?.Authorization });
        return new Response(JSON.stringify({ message: 'Invalid or expired password reset link' }),
            { status: 400, headers: { 'Content-Type': 'application/json' } });
    };

    await assert.rejects(api.resetPassword('token-ruim', 'nova-senha-678'),
        (error) => error instanceof ApiError && error.status === 400);

    assert.deepEqual(sent, [{
        path: '/api/v1/auth/password/reset',
        body: JSON.stringify({ token: 'token-ruim', newPassword: 'nova-senha-678' }),
        authorization: undefined
    }]);
    assert.deepEqual(events, []);
    assert.equal(session.token, 'valid');
});

test('the Google config and the Google login are anonymous: a 401 or 409 never fires session:expired nor refreshes', async () => {
    session.start('valid', 'other@example.com');
    events.length = 0;
    const sent = [];
    globalThis.fetch = async (url, options = {}) => {
        sent.push({ path: new URL(url).pathname, method: options.method ?? 'GET', body: options.body, authorization: options.headers?.Authorization });
        return sent.length === 1
            ? Response.json({ clientId: 'abc.apps.googleusercontent.com' })
            : new Response(JSON.stringify({ message: 'Invalid Google credential' }),
                { status: 401, headers: { 'Content-Type': 'application/json' } });
    };

    assert.deepEqual(await api.googleConfig(), { clientId: 'abc.apps.googleusercontent.com' });
    await assert.rejects(api.loginWithGoogle('id-token'), (error) => error instanceof ApiError && error.status === 401);

    assert.deepEqual(sent, [
        { path: '/api/v1/auth/google/config', method: 'GET', body: undefined, authorization: undefined },
        { path: '/api/v1/auth/google', method: 'POST', body: JSON.stringify({ credential: 'id-token' }), authorization: undefined }
    ]);
    assert.deepEqual(events, []);
    assert.equal(session.token, 'valid');
});

test('linking and unlinking Google carry the current password with the session token', async () => {
    session.start('valid', 'user@example.com');
    const sent = [];
    globalThis.fetch = async (url, options = {}) => {
        sent.push({ path: new URL(url).pathname, method: options.method, body: options.body, authorization: options.headers?.Authorization });
        return new Response(null, { status: 204 });
    };

    await api.profile.linkGoogle('id-token', 'minha frase secreta');
    await api.profile.unlinkGoogle('minha frase secreta');

    assert.deepEqual(sent, [
        {
            path: '/api/v1/profile/google', method: 'POST', authorization: 'Bearer valid',
            body: JSON.stringify({ credential: 'id-token', currentPassword: 'minha frase secreta' })
        },
        {
            path: '/api/v1/profile/google/unlink', method: 'POST', authorization: 'Bearer valid',
            body: JSON.stringify({ currentPassword: 'minha frase secreta' })
        }
    ]);
});

test('a refused Google credential on the profile is a 400 with the field error, not an expired session', async () => {
    session.start('valid', 'user@example.com');
    events.length = 0;
    globalThis.fetch = async () => new Response(JSON.stringify({
        message: 'Invalid Google credential', fieldErrors: { credential: 'Invalid Google credential' }
    }), { status: 400, headers: { 'Content-Type': 'application/json' } });

    await assert.rejects(api.profile.linkGoogle('falso', 'senha'), (error) =>
        error instanceof ApiError && error.status === 400 && error.fieldErrors.credential === 'Invalid Google credential');

    assert.deepEqual(events, []);
    assert.equal(session.token, 'valid');
});

test('hiding the photo and using the Google photo again use their own routes', async () => {
    session.start('valid', 'user@example.com');
    const sent = [];
    globalThis.fetch = async (url, options = {}) => {
        sent.push({ path: new URL(url).pathname, method: options.method });
        return new Response(null, { status: 204 });
    };

    await api.profile.hideAvatar();
    await api.profile.useGoogleAvatar();

    assert.deepEqual(sent, [
        { path: '/api/v1/profile/avatar', method: 'DELETE' },
        { path: '/api/v1/profile/avatar/google', method: 'POST' }
    ]);
});

test('session keeps the photo URL, tells "no photo" from "not known yet", and clears it on logout', () => {
    session.start('token', 'user@example.com');
    assert.equal(session.avatar, null);

    session.setAvatar(null);
    assert.equal(session.avatar, '');

    session.setAvatar('https://lh3.example/ana.png');
    assert.equal(session.avatar, 'https://lh3.example/ana.png');

    session.clear();
    assert.equal(session.avatar, null);
});

test('the contribution flag is unknown until the profile says, is stored from it and cleared with the session', async () => {
    session.clear();
    assert.equal(session.contribution, null);

    globalThis.fetch = async () => Response.json({ name: 'Ana', admin: false, contributionEnabled: true });
    session.start('token-123', 'ana@example.com');
    await api.profile.get();
    assert.equal(session.contribution, true);

    globalThis.fetch = async () => Response.json({ name: 'Ana', admin: false, contributionEnabled: false });
    await api.profile.get();
    assert.equal(session.contribution, false);

    session.clear();
    assert.equal(session.contribution, null);
});
