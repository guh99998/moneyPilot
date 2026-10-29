import { test } from 'node:test';
import assert from 'node:assert/strict';
import { legacyAppUrl, sessionLinks } from '../../main/resources/static/js/site.js';

// CA3
test('a legacy route hash goes to /app/ keeping the whole hash', () => {
    assert.equal(legacyAppUrl('#/transacoes'), '/app/#/transacoes');
    assert.equal(legacyAppUrl('#/verificar-email?token=abc'), '/app/#/verificar-email?token=abc');
});

test('no hash, a bare hash or a section anchor stay on the site', () => {
    assert.equal(legacyAppUrl(''), null);
    assert.equal(legacyAppUrl('#'), null);
    assert.equal(legacyAppUrl('#secao'), null);
    assert.equal(legacyAppUrl(undefined), null);
});

// CA1
test('without a session the top shows Entrar and Criar conta', () => {
    assert.deepEqual(sessionLinks(false).map((link) => link.label), ['Entrar', 'Criar conta']);
});

// CA2
test('with a session the top shows only Abrir o app', () => {
    assert.deepEqual(sessionLinks(true).map((link) => link.label), ['Abrir o app']);
});
