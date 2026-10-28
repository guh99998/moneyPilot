import { el, svgIcon } from './ui.js';

/**
 * Regras e medidor de senha nova. As regras espelham o servidor (`NewPasswordValidator` e `PasswordPolicy`), que é
 * quem recusa de verdade: aqui o medidor só informa, para a pessoa ver o motivo antes de enviar.
 */

export const MIN_LENGTH = 10;
export const MAX_LENGTH = 64;
const MAX_BYTES = 72;
const MIN_PART_LENGTH = 4;
const STRONG_LENGTH = 16;
const STRONG_VARIED_LENGTH = 14;
const KEYBOARD_ROWS = ['qwertyuiop', 'asdfghjkl', 'zxcvbnm'];

export const HINT = `De ${MIN_LENGTH} a ${MAX_LENGTH} caracteres.`;

export const MESSAGES = {
    tooLong: 'A senha é longa demais. Use no máximo 64 caracteres e menos acentos ou emoji.',
    leaked: 'Essa senha aparece em listas públicas de senhas vazadas na internet. Escolha outra, de preferência uma frase.',
    sequence: 'Sequências e repetições, como 1234567890 ou aaaaaaaaaa, são fáceis de adivinhar. Escolha outra.',
    personal: 'A senha não pode conter seu e-mail nem seu nome.',
    reasonable: 'Boa. Uma frase com 16 caracteres ou mais deixa a senha ainda mais forte.',
    strong: 'Senha forte.'
};

const LEVEL_CLASS = { fraca: 'weak', 'razoável': 'fair', forte: 'strong' };

let commonPromise = null;

/**
 * Baixa a lista de senhas comuns uma vez e a guarda em memória. Se falhar, o medidor segue sem essa checagem
 * (o servidor recusa do mesmo jeito) e a próxima chamada tenta de novo.
 */
export function loadCommonPasswords() {
    if (!commonPromise) {
        commonPromise = fetch('/data/common-passwords.txt')
            .then((response) => (response.ok ? response.text() : Promise.reject(new Error(String(response.status)))))
            .then((text) => new Set(text.split('\n')
                .map((line) => line.replace(/\r$/, ''))
                .filter((line) => line && !line.startsWith('#'))
                .map((line) => line.toLowerCase())))
            .catch(() => {
                commonPromise = null;
                return new Set();
            });
    }
    return commonPromise;
}

const length = (text) => [...text].length;

// Sem acentos e sem diferença de maiúsculas.
const normalize = (text) => text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();

const isDigit = (char) => char >= '0' && char <= '9';
const isLetter = (char) => char >= 'a' && char <= 'z';

// Os dígitos dão a volta (…890123…); as letras não.
function isRun(lower) {
    const chars = [...lower];
    const digits = chars.every(isDigit);
    if (!digits && !chars.every(isLetter)) return false;

    const steps = (step) => chars.every((char, i) => {
        if (i === 0) return true;
        const delta = char.charCodeAt(0) - chars[i - 1].charCodeAt(0);
        return delta === step || (digits && (((delta - step) % 10) + 10) % 10 === 0);
    });
    return steps(1) || steps(-1);
}

/** Um caractere repetido, dígitos ou letras em sequência, ou um trecho de linha de teclado. */
export function isSequenceOrRepetition(password) {
    const lower = password.toLowerCase();

    return new Set(lower).size === 1
        || isRun(lower)
        || KEYBOARD_ROWS.some((row) => row.includes(lower) || [...row].reverse().join('').includes(lower));
}

function containsPersonal(password, { email, name }) {
    const normalized = normalize(password);
    const parts = [];

    if (email) {
        const mail = normalize(email);
        const local = mail.split('@')[0];
        parts.push(mail);
        if (local.length >= MIN_PART_LENGTH) parts.push(local);
    }
    if (name) {
        parts.push(...normalize(name).split(/[^\p{L}]+/u).filter((part) => part.length >= MIN_PART_LENGTH));
    }

    return parts.some((part) => normalized.includes(part));
}

function variety(password) {
    const classes = [/\p{Ll}/u, /\p{Lu}/u, /\p{N}/u, /[^\p{L}\p{N}\s]/u].filter((pattern) => pattern.test(password)).length;
    return /\s/.test(password) || classes >= 3;
}

/**
 * Avalia uma senha nova. `level` é `null` com o campo vazio; `ok` diz se passa em todas as regras.
 * `common` é o conjunto de `loadCommonPasswords()`; sem ele a regra de senha vazada é pulada.
 */
export function assess(password, { email = '', name = '', common = null } = {}) {
    if (!password) return { level: null, ok: false, hint: HINT };

    const size = length(password);
    if (size < MIN_LENGTH) {
        const missing = MIN_LENGTH - size;
        return { level: 'fraca', ok: false, hint: `${HINT} ${missing === 1 ? 'Falta 1 caractere.' : `Faltam ${missing} caracteres.`}` };
    }
    if (size > MAX_LENGTH || new TextEncoder().encode(password).length > MAX_BYTES) {
        return { level: 'fraca', ok: false, hint: MESSAGES.tooLong };
    }
    if (common?.has(password.toLowerCase())) return { level: 'fraca', ok: false, hint: MESSAGES.leaked };
    if (isSequenceOrRepetition(password)) return { level: 'fraca', ok: false, hint: MESSAGES.sequence };
    if (containsPersonal(password, { email, name })) return { level: 'fraca', ok: false, hint: MESSAGES.personal };

    const strong = size >= STRONG_LENGTH || (size >= STRONG_VARIED_LENGTH && variety(password));
    return strong
        ? { level: 'forte', ok: true, hint: MESSAGES.strong }
        : { level: 'razoável', ok: true, hint: MESSAGES.reasonable };
}

// O botão já tem aria-label: o desenho é decorativo para o leitor de tela.
const icon = (shapes) => svgIcon(shapes, 20).replace('<svg ', '<svg aria-hidden="true" focusable="false" ');

const EYE = '<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>';
const EYE_OFF = '<path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 '
    + '9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>'
    + '<line x1="1" y1="1" x2="23" y2="23"/>';

/**
 * Campo de senha com botão de olho dentro e, se `meter`, o medidor de força ligado por `aria-describedby`.
 * O medidor não é uma live region, para o leitor de tela não anunciar a cada tecla. `getContext` devolve o
 * `{ email, name }` do momento (a regra de senha pessoal). Devolve o campo (`control`, para pôr no formulário),
 * o `input`, o `meter` (ou `null`) e `refresh()`, para atualizar o medidor depois de mudar o valor por código.
 */
export function passwordInput({ id, autocomplete, describedBy = '', meter = false, getContext = () => ({}), ...attributes }) {
    const input = el('input', {
        id,
        type: 'password',
        autocomplete,
        required: true,
        // Com a senha à mostra, o navegador não deve corrigir nem pôr maiúscula.
        spellcheck: 'false',
        autocapitalize: 'none',
        autocorrect: 'off',
        ...attributes
    });

    const toggle = el('button', {
        class: 'password-toggle',
        type: 'button',
        'aria-label': 'Mostrar senha',
        'aria-pressed': 'false',
        html: icon(EYE),
        onClick: () => {
            const show = input.getAttribute('type') === 'password';
            input.setAttribute('type', show ? 'text' : 'password');
            toggle.setAttribute('aria-label', show ? 'Esconder senha' : 'Mostrar senha');
            toggle.setAttribute('aria-pressed', String(show));
            toggle.innerHTML = icon(show ? EYE_OFF : EYE);
        }
    });

    const control = el('div', { class: 'password-field' }, [input, toggle]);

    let meterNode = null;
    let refresh = async () => {};
    if (meter) {
        const meterId = `${id}-strength`;
        const fill = el('span', { class: 'strength-fill' });
        const label = el('span', { class: 'strength-label' });
        const hint = el('span', { class: 'strength-hint' });
        meterNode = el('div', { class: 'strength', id: meterId, hidden: true }, [
            el('div', { class: 'strength-track' }, [fill]),
            el('p', { class: 'strength-text' }, [label, hint])
        ]);

        input.setAttribute('aria-describedby', [describedBy, meterId].filter(Boolean).join(' '));

        refresh = async () => {
            const typed = input.value;
            const common = typed ? await loadCommonPasswords() : null;
            // A lista chega depois; se a pessoa continuou digitando, quem vale é a última tecla.
            if (typed !== input.value) return;

            const result = assess(typed, { ...getContext(), common });
            meterNode.hidden = result.level === null;
            fill.className = `strength-fill ${LEVEL_CLASS[result.level] || ''}`.trim();
            label.textContent = result.level ? `Força: ${result.level}. ` : '';
            hint.textContent = result.hint;
        };
        input.addEventListener('input', refresh);
    } else if (describedBy) {
        input.setAttribute('aria-describedby', describedBy);
    }

    return { control, input, meter: meterNode, refresh: () => refresh() };
}
