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
