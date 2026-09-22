import { api, ApiError, session } from '../api.js';
import { el, toast } from '../ui.js';

export function renderAuth(onAuthenticated) {
    let mode = 'login';
    const wrap = el('div', { class: 'auth-wrap' });

    function draw() {
        const isLogin = mode === 'login';
        const errorBox = el('p', { class: 'dialog-error', hidden: true });
        const fieldErrors = {
            name: el('span', { class: 'error' }),
            email: el('span', { class: 'error' }),
            password: el('span', { class: 'error' })
        };

        const nameInput = el('input', { type: 'text', autocomplete: 'name', required: true });
        const emailInput = el('input', { type: 'email', autocomplete: 'email', required: true });
        const passwordInput = el('input', {
            type: 'password',
            autocomplete: isLogin ? 'current-password' : 'new-password',
            required: true,
            minlength: isLogin ? null : '8'
        });

        const submitBtn = el('button', {
            class: 'btn-primary',
            type: 'submit',
            style: 'width:100%; justify-content:center;',
            text: isLogin ? 'Entrar' : 'Criar conta'
        });

        const form = el('form', {
            onSubmit: async (event) => {
                event.preventDefault();
                submitBtn.disabled = true;
                errorBox.hidden = true;
                Object.values(fieldErrors).forEach((node) => { node.textContent = ''; });

                const email = emailInput.value.trim();
                const password = passwordInput.value;

                try {
                    if (!isLogin) {
                        await api.register({ name: nameInput.value.trim(), email, password });
                    }
                    const auth = await api.login({ email, password });
                    session.start(auth.token, email);
                    if (!isLogin) toast('Conta criada. Bem-vindo!', 'success');
                    onAuthenticated();
                } catch (error) {
                    if (error instanceof ApiError && error.fieldErrors) {
                        Object.entries(error.fieldErrors).forEach(([field, message]) => {
                            if (fieldErrors[field]) fieldErrors[field].textContent = message;
                        });
                    }
                    errorBox.textContent = error?.message || 'Não foi possível continuar.';
                    errorBox.hidden = false;
                    submitBtn.disabled = false;
                }
            }
        }, [
            errorBox,
            isLogin ? null : el('div', { class: 'field' }, [
                el('label', { text: 'Nome' }), nameInput, fieldErrors.name
            ]),
            el('div', { class: 'field' }, [
                el('label', { text: 'E-mail' }), emailInput, fieldErrors.email
            ]),
            el('div', { class: 'field' }, [
                el('label', { text: 'Senha' }),
                passwordInput,
                isLogin ? null : el('span', { class: 'meter-foot', text: 'Mínimo de 8 caracteres.' }),
                fieldErrors.password
            ]),
            submitBtn
        ]);

        const card = el('div', { class: 'card auth-card' }, [
            el('img', { class: 'auth-logo', src: 'image/money-pilot-logo.png', alt: 'moneyPilot' }),
            form,
            el('p', { class: 'auth-switch' }, [
                el('span', { text: isLogin ? 'Ainda não tem conta?' : 'Já tem conta?' }),
                el('button', {
                    type: 'button',
                    text: isLogin ? 'Criar agora' : 'Entrar',
                    onClick: () => { mode = isLogin ? 'register' : 'login'; draw(); }
                })
            ])
        ]);

        wrap.replaceChildren(card);
        (isLogin ? emailInput : nameInput).focus();
    }

    draw();
    return wrap;
}
