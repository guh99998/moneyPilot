import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';

import { installFakeDom, findAll } from './fake-dom.mjs';

installFakeDom();

const { HELP_PAGES, HELP_LABEL, helpUrl, helpLink, routeOf } = await import('../../main/resources/static/js/help.js');
const { pageHead, el } = await import('../../main/resources/static/js/ui.js');

const AJUDA = new URL('../../main/resources/static/ajuda/', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');

beforeEach(() => { window.location.hash = ''; });

// CA3
test('every mapped screen opens its own tutorial', () => {
    assert.equal(helpUrl('dashboard'), '/ajuda/visao-geral.html');
    assert.equal(helpUrl('transactions'), '/ajuda/lancamentos.html');
    assert.equal(helpUrl('bills'), '/ajuda/a-pagar-e-receber.html');
    assert.equal(helpUrl('forecast'), '/ajuda/previsao.html');
    assert.equal(helpUrl('accounts'), '/ajuda/contas.html');
    assert.equal(helpUrl('categories'), '/ajuda/categorias.html');
    assert.equal(helpUrl('budgets'), '/ajuda/orcamentos.html');
    assert.equal(helpUrl('profile'), '/ajuda/perfil-e-seguranca.html');
    assert.equal(helpUrl('suporte'), '/ajuda/suporte.html');
    assert.equal(helpUrl('usando-com-ia'), '/ajuda/usando-com-ia.html');
});

test('every file in the map exists in static/ajuda', () => {
    for (const file of Object.values(HELP_PAGES)) {
        assert.ok(existsSync(AJUDA + file), file);
    }
});

// CA8
test('a screen without a tutorial opens the help index', () => {
    assert.equal(helpUrl('apoie'), '/ajuda/');
    assert.equal(helpUrl('admin/chamados'), '/ajuda/');
    assert.equal(helpUrl('tela-nova'), '/ajuda/');
    assert.equal(helpUrl(''), '/ajuda/');
    assert.equal(helpUrl('constructor'), '/ajuda/');
});

test('the route comes from the hash without the query', () => {
    assert.equal(routeOf('#/bills'), 'bills');
    assert.equal(routeOf('#/bills?status=OPEN'), 'bills');
    assert.equal(routeOf('#/admin/chamados'), 'admin/chamados');
    assert.equal(routeOf(''), '');
});

// CA6
test('the help link has an accessible name, opens in a new tab and hides the visible "?" from readers', () => {
    const link = helpLink('accounts', el);

    assert.equal(link.tagName, 'A');
    assert.equal(link.getAttribute('href'), '/ajuda/contas.html');
    assert.equal(link.getAttribute('target'), '_blank');
    assert.equal(link.getAttribute('rel'), 'noopener');
    assert.equal(link.getAttribute('aria-label'), HELP_LABEL);
    assert.ok(HELP_LABEL.startsWith('Ajuda sobre esta tela'));
    assert.equal(link.textContent, '?');
    assert.equal(findAll(link, (node) => node.getAttribute('aria-hidden') === 'true').length, 1);
});

// CA3, CA8
test('every screen head gets the "?" for the current route', () => {
    window.location.hash = '#/budgets';
    let link = findAll(pageHead('Orçamentos'), (node) => node.tagName === 'A')[0];
    assert.equal(link.getAttribute('href'), '/ajuda/orcamentos.html');

    window.location.hash = '#/apoie';
    link = findAll(pageHead('Apoie o projeto'), (node) => node.tagName === 'A')[0];
    assert.equal(link.getAttribute('href'), '/ajuda/');
});
