import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom } from './fake-dom.mjs';

installFakeDom();

const { accountFields, accountPayload } = await import('../../main/resources/static/js/views/accounts.js');

test('the closing and due day fields only show up for a credit card', () => {
    const fields = accountFields();
    const closing = fields.find((field) => field.name === 'closingDay');
    const due = fields.find((field) => field.name === 'dueDay');

    for (const field of [closing, due]) {
        assert.equal(field.type, 'number');
        assert.equal(field.min, 1);
        assert.equal(field.max, 31);
        assert.equal(field.required, undefined);
        assert.deepEqual(field.showWhen, { name: 'accountType', value: 'CREDIT_CARD' });
    }
});

test('a credit card sends both days as whole numbers', () => {
    const payload = accountPayload({
        name: 'Cartão', accountType: 'CREDIT_CARD', initialBalance: '0', closingDay: '10', dueDay: '17'
    });

    assert.deepEqual(payload, {
        name: 'Cartão', accountType: 'CREDIT_CARD', initialBalance: '0', closingDay: 10, dueDay: 17
    });
});

test('a credit card without days omits them, so editing keeps the days it already has', () => {
    const payload = accountPayload({
        name: 'Cartão', accountType: 'CREDIT_CARD', initialBalance: '0', closingDay: null, dueDay: null
    });

    assert.equal('closingDay' in payload, false);
    assert.equal('dueDay' in payload, false);
});

test('another account type never sends the days, even if the hidden fields hold values', () => {
    const payload = accountPayload({
        name: 'Corrente', accountType: 'CHECKING', initialBalance: '5', closingDay: '10', dueDay: '17'
    });

    assert.deepEqual(payload, { name: 'Corrente', accountType: 'CHECKING', initialBalance: '5' });
});

test('only one day is sent as is, so the API answers with the field error', () => {
    const payload = accountPayload({
        name: 'Cartão', accountType: 'CREDIT_CARD', initialBalance: '0', closingDay: '10', dueDay: null
    });

    assert.equal(payload.closingDay, 10);
    assert.equal('dueDay' in payload, false);
});
