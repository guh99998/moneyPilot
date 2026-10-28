import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

import { installFakeDom, findAll, findByTag } from './fake-dom.mjs';

installFakeDom();

const MODULE = '../../main/resources/static/js/password.js';
const { assess, isSequenceOrRepetition, loadCommonPasswords, passwordInput, MESSAGES } = await import(MODULE);

const LIST_FILE = new URL('../../main/resources/static/data/common-passwords.txt', import.meta.url);
const common = new Set(['senha12345', 'qwertyuiop', 'basketball']);

test('an empty field has no level', () => {
    assert.equal(assess('').level, null);
});

test('fewer than 10 characters is weak and says how many are missing', () => {
    const short = assess('curta1234');
    assert.equal(short.level, 'fraca');
    assert.equal(short.ok, false);
    assert.match(short.hint, /Falta 1 caractere\./);
    assert.match(assess('curta').hint, /Faltam 5 caracteres\./);
});

test('more than 64 characters or 72 bytes is weak', () => {
    assert.equal(assess('a1'.repeat(33)).hint, MESSAGES.tooLong);
    assert.equal(assess('🔒'.repeat(20)).hint, MESSAGES.tooLong);
});

test('a password from the leaked list is weak, whatever the case, and the text names public leaked lists', () => {
    for (const password of ['senha12345', 'SENHA12345', 'Senha12345']) {
        const result = assess(password, { common });
        assert.equal(result.level, 'fraca');
        assert.equal(result.hint, MESSAGES.leaked);
    }
    assert.match(MESSAGES.leaked, /listas públicas de senhas vazadas na internet/);
    assert.doesNotMatch(MESSAGES.leaked, /comum n[oa]/);
});

test('without the list the leaked check is skipped, the other rules still apply', () => {
    assert.notEqual(assess('senha12345').hint, MESSAGES.leaked);
    assert.equal(assess('aaaaaaaaaa').hint, MESSAGES.sequence);
});

test('repetitions, sequences and keyboard rows are weak', () => {
    for (const password of ['aaaaaaaaaa', 'ZZZZZZZZZZ', '2345678901', '7890123456', '9876543210', 'abcdefghij',
        'JIHGFEDCBA', 'qwertyuiop', 'POIUYTREWQ']) {
        assert.equal(assess(password).hint, MESSAGES.sequence, password);
    }
    for (const password of ['1234567891', 'xyzabcdefg', 'abcdefghik', '12345abcde', 'o céu está azul hoje']) {
        assert.equal(isSequenceOrRepetition(password), false, password);
    }
});

test('the e-mail, its local part and a part of the name are weak, ignoring case and accents', () => {
    const context = { email: 'Gustavo.Lopes@example.com', name: 'João da Silva' };

    for (const password of ['ola gustavo.lopes@example.com ola', 'xxGUSTAVO.LOPESxx', 'tudo sobre JOAO', 'amor da SÍLVA']) {
        assert.equal(assess(password, context).hint, MESSAGES.personal, password);
    }
});

test('short parts of the name and of the e-mail do not count', () => {
    const context = { email: 'jo@example.com', name: 'Ana Luz' };

    assert.equal(assess('banana na janela', context).ok, true);
    assert.equal(assess('uma frase com jo no meio', context).ok, true);
    assert.equal(assess('uma frase jo@example.com', context).hint, MESSAGES.personal);
});

test('levels: reasonable under 16 characters, strong from 16, or from 14 with a space or variety', () => {
    assert.equal(assess('gatoverde').level, 'fraca');
    assert.equal(assess('gatoverdea').level, 'razoável');
    assert.equal(assess('gatoverdeazulz').level, 'razoável');
    assert.equal(assess('gato verde azul').level, 'forte');
    assert.equal(assess('Gato-verde-az1').level, 'forte');
    assert.equal(assess('gatoverdeazulzin').level, 'forte');
    assert.equal(assess('o céu está azul hoje').level, 'forte');
});

test('every level carries a hint, and a passing password is ok', () => {
    const fair = assess('gatoverdea');
    assert.deepEqual([fair.ok, fair.hint], [true, MESSAGES.reasonable]);
    const strong = assess('uma frase bem comprida');
    assert.deepEqual([strong.ok, strong.hint], [true, MESSAGES.strong]);
});

test('a common word inside a longer phrase is not weak', () => {
    assert.equal(assess('minha senha12345 favorita', { common }).ok, true);
});

test('the real list parses as the server reads it: lowercase, no comments, 10 or more characters', async () => {
    globalThis.fetch = async () => new Response(readFileSync(LIST_FILE, 'utf8'));
    const list = await loadCommonPasswords();

    assert.ok(list.has('senha12345'));
    assert.ok(list.has('1234567890'));
    assert.ok([...list].every((password) => [...password].length >= 10 && password === password.toLowerCase()
        && !password.startsWith('#')));
});

test('a failed download is an empty list, is not kept, and the next call tries again', async () => {
    let calls = 0;
    globalThis.fetch = async () => { calls += 1; throw new TypeError('offline'); };
    // Um módulo novo, sem a lista que o teste anterior deixou no cache.
    const { loadCommonPasswords: freshLoad } = await import(`${MODULE}?failed-download`);

    assert.equal((await freshLoad()).size, 0);
    assert.equal((await freshLoad()).size, 0);
    assert.equal(calls, 2);

    globalThis.fetch = async () => { calls += 1; return new Response('# c\r\nsenha12345\r\n\r\nQWERTYUIOP\r\n'); };
    const list = await freshLoad();
    assert.deepEqual([...list].sort(), ['qwertyuiop', 'senha12345']);

    await freshLoad();
    assert.equal(calls, 3);
});

test('a non-200 answer is an empty list too', async () => {
    globalThis.fetch = async () => new Response('nope', { status: 404 });
    const { loadCommonPasswords: freshLoad } = await import(`${MODULE}?not-found`);

    assert.equal((await freshLoad()).size, 0);
});

// ---------- campo ----------

const SMALL_LIST = 'senha12345\nqwertyuiop\n';

function field(options = {}) {
    globalThis.fetch = async () => new Response(SMALL_LIST);
    return passwordInput({ id: 'pw', autocomplete: 'new-password', ...options });
}

const eyeOf = ({ control }) => findByTag(control, 'button')[0];
const part = (meter, name) => findAll(meter, (node) => node.className === name)[0].textContent;
const fillOf = (meter) => findAll(meter, (node) => node.className.startsWith('strength-fill'))[0].className;

test('the eye button shows and hides the password and says so to screen readers', async () => {
    const result = field();
    const eye = eyeOf(result);

    assert.equal(eye.getAttribute('type'), 'button');
    assert.equal(eye.getAttribute('aria-label'), 'Mostrar senha');
    assert.equal(eye.getAttribute('aria-pressed'), 'false');
    assert.equal(result.input.getAttribute('type'), 'password');

    await eye.dispatch('click');

    assert.equal(result.input.getAttribute('type'), 'text');
    assert.equal(eye.getAttribute('aria-label'), 'Esconder senha');
    assert.equal(eye.getAttribute('aria-pressed'), 'true');

    await eye.dispatch('click');

    assert.equal(result.input.getAttribute('type'), 'password');
    assert.equal(eye.getAttribute('aria-label'), 'Mostrar senha');
    assert.equal(eye.getAttribute('aria-pressed'), 'false');
});

test('the eye icon is hidden from screen readers and the field is not autocorrected', () => {
    const { input, control } = field();

    assert.match(eyeOf({ control }).innerHTML, /aria-hidden="true"/);
    assert.equal(input.getAttribute('spellcheck'), 'false');
    assert.equal(input.getAttribute('autocapitalize'), 'none');
});

test('without the meter there is only the eye', () => {
    const result = field({ describedBy: 'hint err' });

    assert.equal(result.meter, null);
    assert.equal(result.input.getAttribute('aria-describedby'), 'hint err');
    assert.equal(findByTag(result.control, 'button').length, 1);
});

test('the meter is hidden while empty, linked by aria-describedby and is not a live region', () => {
    const { input, meter } = field({ meter: true, describedBy: 'hint err' });

    assert.equal(meter.hidden, true);
    assert.equal(input.getAttribute('aria-describedby'), `hint err ${meter.getAttribute('id')}`);
    assert.deepEqual(findAll(meter, (node) => node.getAttribute('aria-live') || node.getAttribute('role')), []);
    assert.equal(meter.getAttribute('aria-live'), null);
    assert.equal(meter.getAttribute('role'), null);
});

test('the meter changes as the password is typed', async () => {
    const { input, meter } = field({ meter: true });

    input.value = 'curta';
    await input.dispatch('input');
    assert.equal(meter.hidden, false);
    assert.match(part(meter, 'strength-label'), /fraca/);
    assert.match(part(meter, 'strength-hint'), /Faltam 5 caracteres/);
    assert.match(fillOf(meter), /weak/);

    input.value = 'gatoverdea';
    await input.dispatch('input');
    assert.match(part(meter, 'strength-label'), /razoável/);
    assert.match(fillOf(meter), /fair/);

    input.value = 'uma frase bem comprida';
    await input.dispatch('input');
    assert.match(part(meter, 'strength-label'), /forte/);
    assert.match(fillOf(meter), /strong/);

    input.value = '';
    await input.dispatch('input');
    assert.equal(meter.hidden, true);
});

test('the meter warns about a leaked password, naming public lists, using the downloaded list', async () => {
    const { input, meter } = field({ meter: true });

    input.value = 'Senha12345';
    await input.dispatch('input');

    assert.match(meter.textContent, /fraca/);
    assert.match(meter.textContent, /listas públicas de senhas vazadas na internet/);
});

test('the meter uses the name and e-mail the form has right now', async () => {
    let context = { email: 'ana.paula@example.com', name: 'Ana Paula' };
    const { input, meter } = field({ meter: true, getContext: () => context });

    input.value = 'tudo sobre a paula';
    await input.dispatch('input');
    assert.match(meter.textContent, /não pode conter seu e-mail nem seu nome/);

    context = { email: '', name: '' };
    await input.dispatch('input');
    assert.match(meter.textContent, /razoável|forte/);
});

test('without the list (offline) the meter still works with the other rules', async () => {
    globalThis.fetch = async () => { throw new TypeError('offline'); };
    const { passwordInput: offlineField } = await import(`${MODULE}?offline`);
    const { input, meter } = offlineField({ id: 'pw-offline', autocomplete: 'new-password', meter: true });

    input.value = 'aaaaaaaaaa';
    await input.dispatch('input');

    assert.match(meter.textContent, /Sequências e repetições/);
});

test('refresh() updates the meter after the value is changed by code', async () => {
    const result = field({ meter: true });
    result.input.value = 'uma frase bem comprida';
    await result.input.dispatch('input');
    assert.equal(result.meter.hidden, false);

    result.input.value = '';
    await result.refresh();

    assert.equal(result.meter.hidden, true);
});
