import { api, ApiError, session } from '../api.js';
import { el } from '../ui.js';

// O link é de uso único e a tela pode ser renderizada mais de uma vez (hashchange, login):
// a confirmação roda uma só vez e as renderizações seguintes reaproveitam o resultado.
let outcome = null;

function confirmOnce() {
    if (outcome) return outcome;

    const query = window.location.hash.split('?')[1] || '';
    const token = new URLSearchParams(query).get('token');

    // Tira o token da barra de endereço (replaceState não dispara hashchange).
    window.history.replaceState(null, '', '#/confirmar-email');

    outcome = token
        ? api.confirmEmailChange(token).then(() => ({ ok: true }), (error) => ({
            ok: false,
            message: error instanceof ApiError && error.status === 400 && error.fieldErrors?.newEmail
                ? 'Este e-mail já está em uso por outra conta. Peça a troca para outro endereço no seu perfil.'
                : error instanceof ApiError && error.status === 400
                    ? 'Link inválido ou expirado. Peça outro no seu perfil.'
                    : error?.message || 'Não foi possível confirmar agora. Tente abrir o link de novo.'
        }))
        : Promise.resolve({ ok: false, message: 'Link inválido ou expirado. Peça outro no seu perfil.' });

    return outcome;
}

export function renderConfirmEmail() {
    const status = el('p', { class: 'auth-tagline', text: 'Confirmando seu novo e-mail…' });
    const actions = el('div');
    const card = el('div', { class: 'card auth-card' }, [
        el('img', { class: 'auth-logo', src: '/image/money-pilot-logo.png', alt: 'moneyPilot' }),
        el('h2', { text: 'Troca de e-mail', style: 'text-align:center; margin:0 0 .5rem;' }),
        status,
        actions
    ]);

    confirmOnce().then((result) => {
        if (result.ok) {
            status.textContent = 'E-mail alterado. Use o novo endereço para entrar.';
            // Quem confirma já logado mantém a sessão; os outros aparelhos saíram da conta.
            const loggedIn = session.active;
            actions.append(el('button', {
                class: 'btn-primary',
                type: 'button',
                style: 'width:100%; justify-content:center;',
                text: loggedIn ? 'Ir para o painel' : 'Entrar',
                onClick: () => { window.location.hash = loggedIn ? '#/dashboard' : ''; }
            }));
        } else {
            status.textContent = result.message;
            status.classList.add('error');
            actions.append(el('button', {
                class: 'btn-ghost',
                type: 'button',
                style: 'width:100%; justify-content:center;',
                text: session.active ? 'Ir para o perfil' : 'Entrar',
                onClick: () => { window.location.hash = session.active ? '#/profile' : ''; }
            }));
        }
    });

    return el('div', { class: 'auth-wrap' }, [card]);
}
