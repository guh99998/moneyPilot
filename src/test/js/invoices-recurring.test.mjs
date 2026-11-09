import { test } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom } from './fake-dom.mjs';

installFakeDom();

const {
    chargeDates, projectedCharges, recurringPurchasePayload, previewText
} = await import('../../main/resources/static/js/views/invoices-recurring.js');

const base = { dayOfMonth: 20, startDate: '2026-01-20', endDate: null, generatedUntil: null };

test('chargeDates lists one charge per month from the start up to the limit', () => {
    assert.deepEqual(chargeDates(base, '2026-03-31'), ['2026-01-20', '2026-02-20', '2026-03-20']);
});

test('chargeDates clamps the day to the last day of short months', () => {
    const dates = chargeDates({ ...base, dayOfMonth: 31, startDate: '2026-01-31' }, '2026-04-30');
    assert.deepEqual(dates, ['2026-01-31', '2026-02-28', '2026-03-31', '2026-04-30']);
});

test('chargeDates skips what was already generated, by month, like the backend', () => {
    const dates = chargeDates({ ...base, generatedUntil: '2026-02-20' }, '2026-04-30');
    assert.deepEqual(dates, ['2026-03-20', '2026-04-20']);
    // Trocar o dia depois de gerar não cobra o mesmo mês duas vezes.
    assert.deepEqual(chargeDates({ ...base, dayOfMonth: 25, generatedUntil: '2026-02-20' }, '2026-03-31'), ['2026-03-25']);
});

test('chargeDates respects the start and the end dates', () => {
    assert.deepEqual(chargeDates({ ...base, dayOfMonth: 5, startDate: '2026-01-10', endDate: '2026-03-04' }, '2026-06-30'),
        ['2026-02-05']);
});

test('projectedCharges returns the not yet generated charges inside the invoice cycle', () => {
    const invoice = { periodStart: '2026-03-10', periodEnd: '2026-04-09' };
    const purchases = [
        { ...base, id: 1, description: 'Streaming', amount: '39.90', active: true },
        { ...base, id: 2, description: 'Desativada', amount: '10.00', active: false },
        { ...base, id: 3, description: 'Já gerada', amount: '5.00', active: true, generatedUntil: '2026-03-20' }
    ];

    const charges = projectedCharges(purchases, invoice, '2026-03-10');

    assert.deepEqual(charges.map((charge) => [charge.date, charge.description, charge.amount]), [['2026-03-20', 'Streaming', '39.90']]);
});

test('projectedCharges ignores charges before invoicesFrom', () => {
    const invoice = { periodStart: '2026-01-10', periodEnd: '2026-02-09' };
    const purchases = [{ ...base, id: 1, description: 'Streaming', amount: '39.90', active: true }];

    assert.deepEqual(projectedCharges(purchases, invoice, '2026-03-10'), []);
    assert.equal(projectedCharges(purchases, invoice, '2026-01-10').length, 1);
});

test('recurringPurchasePayload builds the request for the card', () => {
    const payload = recurringPurchasePayload({
        categoryId: '3', description: 'Streaming', amount: '39.90', dayOfMonth: '20', startDate: '2026-11-01', endDate: null
    }, 7);

    assert.deepEqual(payload, {
        accountId: 7, categoryId: 3, description: 'Streaming', amount: '39.90', dayOfMonth: 20, startDate: '2026-11-01', endDate: null
    });
});

test('previewText shows the next charges and warns about the ones launched right away', () => {
    const text = previewText({ dayOfMonth: '20', startDate: '2026-01-20', endDate: '' }, '2026-03-15');

    assert.match(text, /20\/03\/2026/);
    assert.match(text, /2 cobranças/); // 20/01 e 20/02 são lançadas agora
});

test('previewText asks for the day and the start while they are missing', () => {
    assert.match(previewText({ dayOfMonth: '', startDate: '2026-01-20', endDate: '' }, '2026-03-15'), /dia/i);
});
