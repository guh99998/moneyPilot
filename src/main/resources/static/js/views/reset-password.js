import { api, ApiError } from '../api.js';
import { el, toast } from '../ui.js';
import { forgotPasswordForm } from './auth.js';
import { HINT, passwordInput as passwordField } from '../password.js';

const INVALID_LINK = 'Link inválido ou expirado. Informe seu e-mail para receber outro.';

// O token sai da barra de endereço na primeira renderização e fica aqui até a senha ser trocada:
// a tela pode ser renderizada de novo (hashchange) sem perder o link. Um link novo substitui o anterior.
let currentToken = null;

function takeToken() {
    const query = window.location.hash.split('?')[1] || '';
    const token = new URLSearchParams(query).get('token');

    // replaceState não dispara hashchange.
    window.history.replaceState(null, '', '#/redefinir-senha');

    if (token) currentToken = token;
    return currentToken;
}

export function renderResetPassword() {
    const token = takeToken();
    // tabindex -1: a mensagem recebe o foco quando o conteúdo abaixo dela é trocado.
    const status = el('p', { class: 'auth-tagline', role: 'status', 'aria-live': 'polite', tabindex: '-1' });
    const body = el('div');
    const card = el('div', { class: 'card auth-card' }, [
        el('img', { class: 'auth-logo', src: '/image/money-pilot-logo.png', alt: 'moneyPilot' }),
        el('h2', { text: 'Redefinir senha', style: 'text-align:center; margin:0 0 .5rem;' }),
        status,
        body
    ]);

    const loginLine = (question) => el('p', { class: 'auth-switch' }, [
        el('span', { text: question }),
        el('button', { type: 'button', text: 'Entrar', onClick: () => { window.location.hash = ''; } })
    ]);

    function showInvalidLink() {
        currentToken = null;
        status.textContent = INVALID_LINK;
        status.classList.add('error');
        const { form, emailInput } = forgotPasswordForm({
            onSent: () => toast('Se houver uma conta com esse e-mail, enviamos um link novo. Confira também o spam.', 'success')
        });
        body.replaceChildren(form, loginLine('Lembrou a senha?'));
        emailInput.focus();
    }

    function showDone() {
        currentToken = null;
        status.textContent = 'Senha alterada. Entre com a nova senha.';
        body.replaceChildren(el('button', {
            class: 'btn-primary',
            type: 'button',
            style: 'width:100%; justify-content:center;',
            text: 'Entrar',
            onClick: () => { window.location.hash = ''; }
        }));
        status.focus();
    }

    function showForm() {
        const newId = (part) => `f-${part}-${Math.random().toString(36).slice(2, 7)}`;
        const inputId = newId('password');
        const hintId = newId('password-hint');
        const errorId = newId('password-error');
        const errorBox = el('p', { class: 'dialog-error', hidden: true, role: 'alert' });
        const fieldError = el('span', { class: 'error', id: errorId });
        // O nome e o e-mail da conta não aparecem nesta tela: a regra de senha pessoal fica com o servidor.
        const password = passwordField({
            id: inputId,
            autocomplete: 'new-password',
            describedBy: `${hintId} ${errorId}`,
            meter: true,
            minlength: '10',
            maxlength: '64'
        });
        const passwordInput = password.input;
        const submitBtn = el('button', {
            class: 'btn-primary',
            type: 'submit',
            style: 'width:100%; justify-content:center;',
            text: 'Salvar nova senha'
        });

        const form = el('form', {
            onSubmit: async (event) => {
                event.preventDefault();
                submitBtn.disabled = true;
                submitBtn.textContent = 'Salvando…';
                errorBox.hidden = true;
                fieldError.textContent = '';
                passwordInput.removeAttribute('aria-invalid');

                try {
                    await api.resetPassword(currentToken, passwordInput.value);
                    showDone();
                } catch (error) {
                    if (error instanceof ApiError && error.fieldErrors?.newPassword) {
                        fieldError.textContent = error.fieldErrors.newPassword;
                        passwordInput.setAttribute('aria-invalid', 'true');
                        passwordInput.focus();
                    } else if (error instanceof ApiError && error.status === 400) {
                        showInvalidLink();
                        return;
                    } else {
                        errorBox.textContent = error?.message || 'Não foi possível salvar agora.';
                        errorBox.hidden = false;
                    }
                    submitBtn.disabled = false;
                    submitBtn.textContent = 'Salvar nova senha';
                }
            }
        }, [
            errorBox,
            el('div', { class: 'field' }, [
                el('label', { for: inputId, text: 'Nova senha' }),
                password.control,
                el('span', { class: 'meter-foot', id: hintId, text: HINT }),
                password.meter,
                fieldError
            ]),
            submitBtn
        ]);

        status.textContent = 'Escolha uma senha nova para a sua conta.';
        body.replaceChildren(form, loginLine('Lembrou a senha?'));
        passwordInput.focus();
    }

    if (token) showForm();
    else showInvalidLink();

    return el('div', { class: 'auth-wrap' }, [card]);
}
