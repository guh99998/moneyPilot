import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom } from './fake-dom.mjs';

installFakeDom();

const { ensureRecurringBills } = await import('../../main/resources/static/js/views/bills.js');

test('the session sync also materializes the recurring card purchases, once per session', async () => {
    const calls = [];
    globalThis.fetch = async (url, options = {}) => {
        calls.push(`${options.method || 'GET'} ${new URL(url, 'http://localhost').pathname}`);
        return Response.json([]);
    };

    await ensureRecurringBills({ force: true });
    await ensureRecurringBills();

    assert.equal(calls.filter((call) => call === 'POST /api/v1/bills/materialize').length, 2);
    assert.equal(calls.filter((call) => call === 'POST /api/v1/recurring-purchases/materialize').length, 1);
});
