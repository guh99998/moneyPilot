import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll, findByTag, findByText } from './fake-dom.mjs';

installFakeDom();

const { renderContribution } = await import('../../main/resources/static/js/views/contribution.js');
const { session } = await import('../../main/resources/static/js/api.js');

const KEY = '00000000-0000-4000-8000-000000000000';
const textOf = (view) => findAll(view, () => true).map((node) => node.textContent).join(' ');

function startServer(response) {
    globalThis.fetch = async () => (response.status === 200
        ? Response.json(response.body)
        : Response.json({ message: 'Resource not found' }, { status: response.status }));
}

function setClipboard(writeText) {
    Object.defineProperty(globalThis, 'navigator', { value: { clipboard: writeText ? { writeText } : undefined }, configurable: true });
}

beforeEach(() => {
    session.clear();
    session.start('token-123', 'ana@example.com');
});

test('the first thing on the screen says the project is free and the support is optional', async () => {
    startServer({ status: 200, body: { key: KEY, qrImage: null } });
    const view = await renderContribution();

    const card = findAll(view, (node) => node.className.split(' ').includes('contribution-card'))[0];
    assert.equal(card.children[0].textContent, 'O moneyPilot é um projeto gratuito, e sempre vai ser.');
    assert.match(card.children[1].textContent, /totalmente opcional/);
    assert.match(textOf(view), /Pagar um cafezinho\?/);
});

test('shows the key in a read-only field with a label and the QR code with alternative text', async () => {
    startServer({ status: 200, body: { key: KEY, qrImage: 'data:image/png;base64,AAAA' } });
    const view = await renderContribution();

    const input = findByTag(view, 'input')[0];
    assert.equal(input.value, KEY);
    assert.equal(input.hasAttribute('readonly'), true);
    assert.equal(findByTag(view, 'label')[0].getAttribute('for'), input.getAttribute('id'));
    const image = findByTag(view, 'img')[0];
    assert.equal(image.getAttribute('src'), 'data:image/png;base64,AAAA');
    assert.equal(image.getAttribute('alt'), 'QR code da chave Pix');
});

test('without a QR code image there is no broken image, only the key', async () => {
    startServer({ status: 200, body: { key: KEY, qrImage: null } });
    const view = await renderContribution();

    assert.equal(findByTag(view, 'img').length, 0);
    assert.equal(findByTag(view, 'input')[0].value, KEY);
});

test('"Copiar chave" copies the key and confirms it', async () => {
    startServer({ status: 200, body: { key: KEY, qrImage: null } });
    let copied;
    setClipboard(async (text) => { copied = text; });
    const view = await renderContribution();

    await findByText(view, 'button', 'Copiar chave').dispatch('click');

    assert.equal(copied, KEY);
    assert.equal(findAll(view, (node) => node.getAttribute('role') === 'status')[0].textContent, 'Chave copiada.');
});

test('when the browser refuses to copy, the key stays selectable and the screen says so', async () => {
    startServer({ status: 200, body: { key: KEY, qrImage: null } });
    setClipboard(async () => { throw new Error('denied'); });
    const view = await renderContribution();

    await findByText(view, 'button', 'Copiar chave').dispatch('click');

    assert.match(findAll(view, (node) => node.getAttribute('role') === 'status')[0].textContent, /Não foi possível copiar/);
    assert.equal(findByTag(view, 'input')[0].value, KEY);
});

test('without a clipboard API the same fallback applies', async () => {
    startServer({ status: 200, body: { key: KEY, qrImage: null } });
    setClipboard(null);
    const view = await renderContribution();

    await findByText(view, 'button', 'Copiar chave').dispatch('click');

    assert.match(findAll(view, (node) => node.getAttribute('role') === 'status')[0].textContent, /Não foi possível copiar/);
});

test('when the server says there is no support configured the screen shows a message instead of a key', async () => {
    startServer({ status: 404 });
    const view = await renderContribution();

    assert.equal(findByTag(view, 'input').length, 0);
    assert.equal(findAll(view, (node) => node.className === 'empty').length, 1);
});
