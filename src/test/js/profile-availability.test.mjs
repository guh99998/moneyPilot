import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom } from './fake-dom.mjs';

installFakeDom();

const { availabilityStatus } = await import('../../main/resources/static/js/views/profile-availability.js');

test('an unchecked investment account is "Guardado"', () => {
    assert.match(availabilityStatus({ type: 'INVESTMENT', countsAsAvailable: false }), /^Guardado/);
});

test('a checked account counts in the available balance and in the forecast', () => {
    assert.equal(availabilityStatus({ type: 'CHECKING', countsAsAvailable: true }), 'Conta no saldo disponível e na previsão');
});

test('an unchecked card with closing and due days enters the forecast through its invoice, not as "Guardado"', () => {
    const text = availabilityStatus({ type: 'CREDIT_CARD', countsAsAvailable: false, closingDay: 10, dueDay: 17 });

    assert.match(text, /fatura/);
    assert.doesNotMatch(text, /Guardado/);
});

test('an unchecked card without days is not "Guardado" either, and points to Contas', () => {
    const text = availabilityStatus({ type: 'CREDIT_CARD', countsAsAvailable: false });

    assert.doesNotMatch(text, /Guardado/);
    assert.match(text, /Contas/);
});
