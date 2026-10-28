import { api, ApiError, session } from '../api.js';
import { el, toast } from '../ui.js';

/** Pede um link de ativação novo. A resposta do servidor é a mesma exista ou não a conta. */
export async function resendVerification(email, button) {
    button.disabled = true;
    try {
        await api.resendVerification(email);
        toast('Enviamos um novo link. Confira também a caixa de spam.', 'success');
    } catch (error) {
        toast(error?.message || 'Não foi possível reenviar agora.', 'error');
    } finally {
        button.disabled = false;
    }
}

export function renderAuth(onAuthenticated) {
    let mode = 'login';
    let pendingEmail = '';
    const wrap = el('div', { class: 'auth-wrap' });

    function drawSent() {
        const resendBtn = el('button', {
            class: 'btn-ghost',
            type: 'button',
            style: 'width:100%; justify-content:center;',
            text: 'Reenviar link',
            onClick: () => resendVerification(pendingEmail, resendBtn)
        });

        wrap.replaceChildren(el('div', { class: 'card auth-card' }, [
            el('img', { class: 'auth-logo', src: 'image/money-pilot-logo.png', alt: 'moneyPilot' }),
            el('h2', { text: 'Confira seu e-mail', style: 'text-align:center; margin:0 0 .5rem;' }),
            el('p', {
                class: 'auth-tagline',
                text: `Enviamos um link para ${pendingEmail}. Abra-o para ativar a sua conta (vale por 24 horas).`
            }),
            resendBtn,
            el('p', { class: 'auth-switch' }, [
                el('span', { text: 'Já ativou?' }),
                el('button', { type: 'button', text: 'Entrar', onClick: () => { mode = 'login'; draw(); } })
            ])
        ]));
        resendBtn.focus();
    }

    function draw() {
        if (mode === 'sent') {
            drawSent();
            return;
        }

        const isLogin = mode === 'login';
        const errorBox = el('p', { class: 'dialog-error', hidden: true, role: 'alert' });
        const resendBtn = el('button', {
            class: 'btn-ghost',
            type: 'button',
            style: 'width:100%; justify-content:center; margin-bottom:.75rem;',
            text: 'Reenviar link de ativação',
            onClick: () => resendVerification(emailInput.value.trim(), resendBtn)
        });
        const newId = (part) => `f-${part}-${Math.random().toString(36).slice(2, 7)}`;
        const inputIds = { name: newId('name'), email: newId('email'), password: newId('password') };
        const errorIds = { name: newId('name-error'), email: newId('email-error'), password: newId('password-error') };
        const hintId = newId('password-hint');
        const fieldErrors = {
            name: el('span', { class: 'error', id: errorIds.name }),
            email: el('span', { class: 'error', id: errorIds.email }),
            password: el('span', { class: 'error', id: errorIds.password })
        };

        const nameInput = el('input', {
            id: inputIds.name, type: 'text', autocomplete: 'name', required: true, 'aria-describedby': errorIds.name
        });
        const emailInput = el('input', {
            id: inputIds.email,
            type: 'email',
            autocomplete: 'email',
            required: true,
            value: pendingEmail,
            'aria-describedby': errorIds.email
        });
        const passwordInput = el('input', {
            id: inputIds.password,
            type: 'password',
            autocomplete: isLogin ? 'current-password' : 'new-password',
            required: true,
            minlength: isLogin ? null : '8',
            'aria-describedby': isLogin ? errorIds.password : `${hintId} ${errorIds.password}`
        });
        const inputs = { name: nameInput, email: emailInput, password: passwordInput };

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
                resendBtn.remove();
                Object.values(fieldErrors).forEach((node) => { node.textContent = ''; });
                Object.values(inputs).forEach((input) => input.removeAttribute('aria-invalid'));

                const email = emailInput.value.trim();
                const password = passwordInput.value;

                try {
                    if (!isLogin) {
                        await api.register({ name: nameInput.value.trim(), email, password });
                        pendingEmail = email;
                        mode = 'sent';
                        draw();
                        return;
                    }
                    const auth = await api.login({ email, password });
                    session.start(auth.token, email);
                    onAuthenticated();
                } catch (error) {
                    if (error instanceof ApiError && error.status === 403) {
                        errorBox.textContent = 'Sua conta ainda não foi ativada. Abra o link que enviamos para o seu e-mail ou peça outro.';
                        errorBox.hidden = false;
                        errorBox.after(resendBtn);
                        submitBtn.disabled = false;
                        return;
                    }
                    if (error instanceof ApiError && error.fieldErrors) {
                        Object.entries(error.fieldErrors).forEach(([field, message]) => {
                            if (!fieldErrors[field]) return;
                            fieldErrors[field].textContent = message;
                            inputs[field].setAttribute('aria-invalid', 'true');
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
                el('label', { for: inputIds.name, text: 'Nome' }), nameInput, fieldErrors.name
            ]),
            el('div', { class: 'field' }, [
                el('label', { for: inputIds.email, text: 'E-mail' }), emailInput, fieldErrors.email
            ]),
            el('div', { class: 'field' }, [
                el('label', { for: inputIds.password, text: 'Senha' }),
                passwordInput,
                isLogin ? null : el('span', { class: 'meter-foot', id: hintId, text: 'Mínimo de 8 caracteres.' }),
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
