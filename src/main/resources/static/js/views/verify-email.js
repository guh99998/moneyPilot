import { api, ApiError } from '../api.js';
import { el } from '../ui.js';
import { resendVerification } from './auth.js';

const INVALID_LINK = 'Link inválido ou expirado. Informe seu e-mail para receber outro.';

// O link é de uso único e a tela pode ser renderizada mais de uma vez (hashchange, login):
// cada token é confirmado uma só vez e as renderizações seguintes reaproveitam o resultado.
// Um link novo (outro token) na mesma aba é confirmado de novo.
let confirmation = null;

function confirm(token) {
    return api.confirmVerification(token).then(() => ({ ok: true }), (error) => ({
        ok: false,
        invalid: error instanceof ApiError && error.status === 400,
        message: error instanceof ApiError && error.status === 400
            ? INVALID_LINK
            : error?.message || 'Não foi possível ativar agora. Tente abrir o link de novo.'
    }));
}

function confirmOnce() {
    const query = window.location.hash.split('?')[1] || '';
    const token = new URLSearchParams(query).get('token');

    // Tira o token da barra de endereço (replaceState não dispara hashchange).
    window.history.replaceState(null, '', '#/verificar-email');

    if (token && confirmation?.token !== token) {
        confirmation = { token, outcome: confirm(token) };
    } else if (!confirmation) {
        confirmation = { token: null, outcome: Promise.resolve({ ok: false, invalid: true, message: INVALID_LINK }) };
    }

    return confirmation.outcome;
}

function resendForm() {
    const emailInput = el('input', { type: 'email', autocomplete: 'email', required: true });
    const submitBtn = el('button', {
        class: 'btn-primary',
        type: 'submit',
        style: 'width:100%; justify-content:center;',
        text: 'Enviar novo link'
    });

    return el('form', {
        onSubmit: (event) => {
            event.preventDefault();
            resendVerification(emailInput.value.trim(), submitBtn);
        }
    }, [
        el('div', { class: 'field' }, [el('label', { text: 'E-mail' }), emailInput]),
        submitBtn
    ]);
}

export function renderVerifyEmail() {
    const status = el('p', { class: 'auth-tagline', role: 'status', 'aria-live': 'polite', text: 'Ativando sua conta…' });
    const actions = el('div');
    const card = el('div', { class: 'card auth-card' }, [
        el('img', { class: 'auth-logo', src: 'image/money-pilot-logo.png', alt: 'moneyPilot' }),
        el('h2', { text: 'Ativação da conta', style: 'text-align:center; margin:0 0 .5rem;' }),
        status,
        actions
    ]);

    confirmOnce().then((result) => {
        if (result.ok) {
            // O link não abre sessão: quem ativou entra com e-mail e senha.
            status.textContent = 'Conta verificada. Já pode entrar com seu e-mail e senha.';
            actions.append(el('button', {
                class: 'btn-primary',
                type: 'button',
                style: 'width:100%; justify-content:center;',
                text: 'Entrar',
                onClick: () => { window.location.hash = ''; }
            }));
            return;
        }

        status.textContent = result.message;
        status.classList.add('error');
        if (result.invalid) actions.append(resendForm());
        actions.append(el('p', { class: 'auth-switch' }, [
            el('span', { text: 'Já ativou?' }),
            el('button', { type: 'button', text: 'Entrar', onClick: () => { window.location.hash = ''; } })
        ]));
    });

    return el('div', { class: 'auth-wrap' }, [card]);
}
