import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';

import { installFakeDom, findAll, findByTag, findByText } from './fake-dom.mjs';

const { toasts } = installFakeDom();

const { openOnboarding, markLogin, maybeOpenOnboarding } = await import('../../main/resources/static/js/views/onboarding.js');
const { session } = await import('../../main/resources/static/js/api.js');

const STEPS = (a, t, b) => [
    { key: 'ACCOUNT', done: a },
    { key: 'TRANSACTION', done: t },
    { key: 'BUDGET', done: b }
];

const state = (steps, extra = {}) => ({ visible: true, eligible: true, showOnStart: true, steps, ...extra });

let requests;

function startServer(handler = () => new Response(null, { status: 204 })) {
    requests = [];
    globalThis.fetch = async (url, options = {}) => {
        const path = new URL(url).pathname;
        requests.push({ path, method: options.method || 'GET', body: options.body });
        return handler(path, options);
    };
}

const dialogs = () => findAll(document.body, (node) => node.tagName === 'DIALOG');
const openDialog = () => dialogs().find((dialog) => dialog.hasAttribute('open'));
const title = (dialog) => findByTag(dialog, 'h2')[0];
const button = (dialog, text) => findByText(dialog, 'button', text);

beforeEach(() => {
    session.clear();
    session.start('token-123', 'ana@example.com');
    document.body.replaceChildren();
    window.location.hash = '';
    toasts.replaceChildren();
    startServer();
});

// CA9: dialog nativo, modal, rotulado, com o foco no título

test('the pop-up is a native modal dialog labelled by its title, and the title takes the focus', () => {
    openOnboarding(state(STEPS(false, false, false)));

    const dialog = openDialog();
    assert.ok(dialog, 'a dialog is open');
    assert.equal(dialog.modal, true, 'opened with showModal');
    assert.equal(dialog.getAttribute('aria-labelledby'), title(dialog).getAttribute('id'));
    assert.equal(document.activeElement, title(dialog));
});

test('closing the dialog (Esc does the same) removes it and gives the focus back', () => {
    const origin = document.createElement('button');
    origin.focus();
    openOnboarding(state(STEPS(false, false, false)));

    openDialog().close();

    assert.equal(dialogs().length, 0);
    assert.equal(document.activeElement, origin);
});

// CA1: abre no primeiro passo pendente

test('it opens on the first pending step', () => {
    openOnboarding(state(STEPS(true, false, false)));

    assert.match(title(openDialog()).textContent, /Passo 2 de 3/);
});

test('with everything done (reopened from the help) it opens on step 1', () => {
    openOnboarding(state(STEPS(true, true, true), { visible: false, eligible: false }));

    assert.match(title(openDialog()).textContent, /Passo 1 de 3/);
});

test('each step says its state in words and links to its screen', () => {
    openOnboarding(state(STEPS(true, false, false)));
    const dialog = openDialog();

    assert.match(dialog.textContent, /A fazer/);
    button(dialog, 'Voltar').dispatch('click');
    assert.match(dialog.textContent, /Feito/);
    assert.equal(findByTag(dialog, 'a')[0].getAttribute('href'), '#/accounts');
});

test('following the step link closes the dialog', async () => {
    openOnboarding(state(STEPS(false, false, false)));
    const dialog = openDialog();

    await findByTag(dialog, 'a')[0].dispatch('click');

    assert.equal(dialogs().length, 0);
});

// CA2: Avançar, Voltar, Dispensar por passo

test('Voltar does not exist on step 1, Avançar does not exist on step 3, Dispensar always exists', async () => {
    openOnboarding(state(STEPS(false, false, false)));
    const dialog = openDialog();

    assert.equal(button(dialog, 'Voltar'), undefined);
    assert.ok(button(dialog, 'Avançar'));
    assert.ok(button(dialog, 'Dispensar'));

    await button(dialog, 'Avançar').dispatch('click');
    assert.match(title(dialog).textContent, /Passo 2 de 3/);
    assert.ok(button(dialog, 'Voltar'));
    assert.ok(button(dialog, 'Avançar'));

    await button(dialog, 'Avançar').dispatch('click');
    assert.match(title(dialog).textContent, /Passo 3 de 3/);
    assert.equal(button(dialog, 'Avançar'), undefined);
    assert.ok(button(dialog, 'Voltar'));
    assert.ok(button(dialog, 'Dispensar'));
});

test('Avançar and Voltar navigate without leaving the dialog, and the focus follows to the title', async () => {
    openOnboarding(state(STEPS(false, false, false)));
    const dialog = openDialog();

    await button(dialog, 'Avançar').dispatch('click');
    assert.equal(openDialog(), dialog);
    assert.equal(document.activeElement, title(dialog));

    await button(dialog, 'Voltar').dispatch('click');
    assert.match(title(dialog).textContent, /Passo 1 de 3/);
    assert.equal(document.activeElement, title(dialog));
    assert.equal(requests.length, 0);
});

// CA3: Dispensar só fecha

test('Dispensar only closes: no call to the API', async () => {
    openOnboarding(state(STEPS(false, false, false)));

    await button(openDialog(), 'Dispensar').dispatch('click');

    assert.equal(dialogs().length, 0);
    assert.deepEqual(requests, []);
});

// CA4: caixinha "Mostrar ao iniciar"

const checkbox = (dialog) => findByTag(dialog, 'input').find((input) => input.getAttribute('type') === 'checkbox');

test('the checkbox reflects the saved value and has a label', () => {
    openOnboarding(state(STEPS(false, false, false), { showOnStart: false }));
    const dialog = openDialog();

    const box = checkbox(dialog);
    assert.equal(box.checked, false);
    const label = findByTag(dialog, 'label').find((item) => item.getAttribute('for') === box.getAttribute('id'));
    assert.match(label.textContent, /Mostrar ao iniciar/);
});

test('unchecking the box saves the preference', async () => {
    openOnboarding(state(STEPS(false, false, false)));
    const dialog = openDialog();
    const box = checkbox(dialog);

    box.checked = false;
    await box.dispatch('change');

    assert.deepEqual(requests.map(({ path, method, body }) => ({ path, method, body })), [
        { path: '/api/v1/onboarding/preferences', method: 'PUT', body: JSON.stringify({ showOnStart: false }) }
    ]);
    assert.equal(box.checked, false);
});

test('when saving fails the box goes back to its previous value and the error shows in the dialog', async () => {
    startServer(() => Response.json({ message: 'Não foi possível salvar.' }, { status: 500 }));
    openOnboarding(state(STEPS(false, false, false)));
    const dialog = openDialog();
    const box = checkbox(dialog);

    box.checked = false;
    await box.dispatch('change');

    assert.equal(box.checked, true);
    const error = findAll(dialog, (node) => node.className.split(' ').includes('dialog-error'))[0];
    assert.equal(error.hidden, false);
    assert.match(error.textContent, /Não foi possível salvar/);
});

test('the checkbox is not shown when it would have no effect (not eligible)', () => {
    openOnboarding(state(STEPS(true, true, true), { visible: false, eligible: false }));

    assert.equal(checkbox(openDialog()), undefined);
});

// Abertura no login e pela ajuda

test('after a login the pop-up opens when the server says visible', async () => {
    startServer(() => Response.json(state(STEPS(false, false, false))));

    markLogin();
    await maybeOpenOnboarding();

    assert.ok(openDialog());
    assert.deepEqual(requests.map((request) => request.path), ['/api/v1/onboarding']);
});

test('after a login nothing opens when visible is false', async () => {
    startServer(() => Response.json(state(STEPS(false, false, false), { visible: false, showOnStart: false })));

    markLogin();
    await maybeOpenOnboarding();

    assert.equal(openDialog(), undefined);
});

test('without a login (F5, route change, a second render) it neither asks the server nor opens', async () => {
    startServer(() => Response.json(state(STEPS(false, false, false))));

    await maybeOpenOnboarding();

    assert.equal(requests.length, 0);
    assert.equal(openDialog(), undefined);
});

test('the login is consumed synchronously: two renders in a row open one pop-up', async () => {
    startServer(() => Response.json(state(STEPS(false, false, false))));

    markLogin();
    await Promise.all([maybeOpenOnboarding(), maybeOpenOnboarding()]);

    assert.equal(requests.length, 1);
    assert.equal(dialogs().length, 1);
});

test('a failure to fetch shows neither the pop-up nor an error', async () => {
    startServer(() => Response.json({ message: 'Erro' }, { status: 500 }));

    markLogin();
    await maybeOpenOnboarding();

    assert.equal(openDialog(), undefined);
    assert.equal(toasts.children.length, 0);
});

test('the help link (#/dashboard?primeiros-passos) opens in any state and removes the parameter', async () => {
    startServer(() => Response.json(state(STEPS(true, true, true), { visible: false, eligible: false, showOnStart: false })));
    window.location.hash = '#/dashboard?primeiros-passos';

    await maybeOpenOnboarding();

    assert.ok(openDialog());
    assert.match(title(openDialog()).textContent, /Passo 1 de 3/);
    assert.equal(window.location.hash, '#/dashboard');
});

test('after the help link was used, a second render does not open it again', async () => {
    startServer(() => Response.json(state(STEPS(false, false, false))));
    window.location.hash = '#/dashboard?primeiros-passos';

    await maybeOpenOnboarding();
    openDialog().close();
    await maybeOpenOnboarding();

    assert.equal(openDialog(), undefined);
    assert.equal(requests.length, 1);
});

test('the checkbox is never disabled while saving, so the keyboard focus is not lost', async () => {
    let release;
    const gate = new Promise((resolve) => { release = resolve; });
    startServer(async () => { await gate; return new Response(null, { status: 204 }); });
    openOnboarding(state(STEPS(false, false, false)));
    const box = checkbox(openDialog());

    box.checked = false;
    const saving = box.dispatch('change');

    assert.equal(box.disabled, false);
    release();
    await saving;
});

test('two quick changes are saved in order and the last one wins', async () => {
    openOnboarding(state(STEPS(false, false, false)));
    const box = checkbox(openDialog());

    box.checked = false;
    const first = box.dispatch('change');
    box.checked = true;
    const second = box.dispatch('change');
    await Promise.all([first, second]);

    assert.deepEqual(requests.map((request) => request.body), [
        JSON.stringify({ showOnStart: false }),
        JSON.stringify({ showOnStart: true })
    ]);
});
