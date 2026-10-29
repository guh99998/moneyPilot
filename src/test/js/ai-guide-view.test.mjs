import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll, findByTag, findByText } from './fake-dom.mjs';

installFakeDom();

const { renderAiGuide, assistantPrompt } = await import('../../main/resources/static/js/views/ai-guide.js');
const { session } = await import('../../main/resources/static/js/api.js');

beforeEach(() => {
    session.clear();
    window.location.hash = '';
    delete globalThis.navigator;
});

const textOf = (view) => view.textContent;
const promptBox = (view) => findByTag(view, 'textarea')[0];

// Spec 021 CA5
test('points to the help tutorial instead of repeating the technical steps', () => {
    const view = renderAiGuide();
    const text = textOf(view);
    const hrefs = findAll(view, (node) => node.tagName === 'A').map((a) => a.getAttribute('href'));

    assert.ok(hrefs.includes('/ajuda/usando-com-ia.html'));
    assert.ok(text.includes('Guia completo na central de ajuda'));
    for (const technical of ['1. Entrar', '2. Renovar', 'Cuidados', 'Swagger', 'Criar senha']) {
        assert.ok(!text.includes(technical), `should not repeat: ${technical}`);
    }
    assert.ok(!hrefs.includes('/swagger-ui.html'));
});

test('shows the ready-made assistant text with the app URLs', () => {
    const view = renderAiGuide();

    assert.equal(promptBox(view).value, assistantPrompt('http://localhost'));
    assert.ok(promptBox(view).value.includes('http://localhost/v3/api-docs'));
    assert.ok(promptBox(view).value.includes('"refreshTokenInBody": true'));
});

test('the copy button puts the assistant text on the clipboard', async () => {
    let copied = null;
    globalThis.navigator = { clipboard: { writeText: async (text) => { copied = text; } } };
    const view = renderAiGuide();

    await findByText(view, 'button', 'Copiar texto').dispatch('click');

    assert.equal(copied, assistantPrompt('http://localhost'));
    assert.ok(textOf(view).includes('Texto copiado.'));
});

test('when the clipboard is unavailable, the text is selected for Ctrl+C', async () => {
    const view = renderAiGuide();
    let selected = false;
    promptBox(view).select = () => { selected = true; };

    await findByText(view, 'button', 'Copiar texto').dispatch('click');

    assert.equal(selected, true);
    assert.ok(textOf(view).includes('Ctrl+C'));
});

test('without a session it offers the way back to the login; with a session it sits inside the app', async () => {
    const standalone = renderAiGuide();
    await findByText(standalone, 'button', 'Voltar para o login').dispatch('click');
    assert.equal(window.location.hash, '');

    session.start('token-x', 'ana@exemplo.com');
    const inApp = renderAiGuide();
    assert.equal(findByText(inApp, 'button', 'Voltar para o login'), undefined);
});
