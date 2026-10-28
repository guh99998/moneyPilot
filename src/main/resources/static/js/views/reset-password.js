import { api, ApiError } from '../api.js';
import { el, toast } from '../ui.js';
import { forgotPasswordForm } from './auth.js';

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
    const status = el('p', { class: 'auth-tagline', role: 'status', 'aria-live': 'polite' });
    const body = el('div');
    const card = el('div', { class: 'card auth-card' }, [
        el('img', { class: 'auth-logo', src: 'image/money-pilot-logo.png', alt: 'moneyPilot' }),
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
        const { form } = forgotPasswordForm({
            onSent: () => toast('Se houver uma conta com esse e-mail, enviamos um link novo. Confira também o spam.', 'success')
        });
        body.replaceChildren(form, loginLine('Lembrou a senha?'));
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
    }

    function showForm() {
        const newId = (part) => `f-${part}-${Math.random().toString(36).slice(2, 7)}`;
        const inputId = newId('password');
        const hintId = newId('password-hint');
        const errorId = newId('password-error');
        const errorBox = el('p', { class: 'dialog-error', hidden: true, role: 'alert' });
        const fieldError = el('span', { class: 'error', id: errorId });
        const passwordInput = el('input', {
            id: inputId,
            type: 'password',
            autocomplete: 'new-password',
            required: true,
            minlength: '8',
            'aria-describedby': `${hintId} ${errorId}`
        });
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
                errorBox.hidden = true;
                fieldError.textContent = '';
                passwordInput.removeAttribute('aria-invalid');

                try {
                    await api.resetPassword(currentToken, passwordInput.value);
                    showDone();
                } catch (error) {
                    const newPasswordError = error instanceof ApiError ? error.fieldErrors?.newPassword : null;
                    if (newPasswordError) {
                        fieldError.textContent = newPasswordError;
                        passwordInput.setAttribute('aria-invalid', 'true');
                    } else if (error instanceof ApiError && error.status === 400) {
                        showInvalidLink();
                        return;
                    } else {
                        errorBox.textContent = error?.message || 'Não foi possível salvar agora.';
                        errorBox.hidden = false;
                    }
                    submitBtn.disabled = false;
                }
            }
        }, [
            errorBox,
            el('div', { class: 'field' }, [
                el('label', { for: inputId, text: 'Nova senha' }),
                passwordInput,
                el('span', { class: 'meter-foot', id: hintId, text: 'Mínimo de 8 caracteres.' }),
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
