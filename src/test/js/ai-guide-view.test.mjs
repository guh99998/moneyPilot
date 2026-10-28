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

test('opens without a session and shows the step-by-step', () => {
    const view = renderAiGuide();
    const text = textOf(view);

    for (const step of ['1. Entrar', '2. Renovar', '3. Listar contas e categorias', '4. Lançar uma despesa', '5. Sair']) {
        assert.ok(text.includes(step), `missing step: ${step}`);
    }
    assert.ok(text.includes('refreshTokenInBody'));
    assert.ok(text.includes('http://localhost/api/v1/auth/login'), 'examples use the app origin');
    assert.ok(text.includes('Criar senha'), 'explains Google-only accounts');
    assert.ok(text.includes('encerra as sessões da API'), 'warns that changing the password ends API sessions');
});

test('links to the Swagger UI and to the OpenAPI contract', () => {
    const view = renderAiGuide();
    const hrefs = findAll(view, (node) => node.tagName === 'A').map((a) => a.getAttribute('href'));

    assert.ok(hrefs.includes('/swagger-ui.html'));
    assert.ok(hrefs.includes('/v3/api-docs'));
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
