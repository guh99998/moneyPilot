import { api, ApiError, session } from '../api.js';
import { el, toast } from '../ui.js';
import { HINT, passwordInput as passwordField } from '../password.js';
import { googleButton, googleErrorMessage } from '../google.js';

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

/**
 * Formulário de "esqueci a senha": só o e-mail. A resposta do servidor é a mesma exista ou não a conta,
 * então `onSent` recebe o e-mail informado e a tela mostra a mensagem genérica. Devolve o formulário e o campo,
 * para a tela decidir o foco.
 */
export function forgotPasswordForm({ email = '', onSent }) {
    const inputId = `f-email-${Math.random().toString(36).slice(2, 7)}`;
    const errorBox = el('p', { class: 'dialog-error', hidden: true, role: 'alert' });
    const emailInput = el('input', {
        id: inputId, type: 'email', autocomplete: 'email', spellcheck: 'false', autocapitalize: 'none', required: true, value: email
    });
    const submitBtn = el('button', {
        class: 'btn-primary',
        type: 'submit',
        style: 'width:100%; justify-content:center;',
        text: 'Enviar link'
    });

    const form = el('form', {
        onSubmit: async (event) => {
            event.preventDefault();
            submitBtn.disabled = true;
            submitBtn.textContent = 'Enviando…';
            errorBox.hidden = true;
            const typed = emailInput.value.trim();
            try {
                await api.forgotPassword(typed);
                onSent(typed);
            } catch (error) {
                errorBox.textContent = error?.message || 'Não foi possível enviar agora.';
                errorBox.hidden = false;
                submitBtn.disabled = false;
                submitBtn.textContent = 'Enviar link';
            }
        }
    }, [
        errorBox,
        el('div', { class: 'field' }, [el('label', { for: inputId, text: 'E-mail' }), emailInput]),
        submitBtn
    ]);

    return { form, emailInput };
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

    function loginButton() {
        return el('button', { type: 'button', text: 'Entrar', onClick: () => { mode = 'login'; draw(); } });
    }

    function drawForgot() {
        const { form, emailInput } = forgotPasswordForm({
            email: pendingEmail,
            onSent: (email) => {
                pendingEmail = email;
                mode = 'forgot-sent';
                draw();
            }
        });

        wrap.replaceChildren(el('div', { class: 'card auth-card' }, [
            el('img', { class: 'auth-logo', src: 'image/money-pilot-logo.png', alt: 'moneyPilot' }),
            el('h2', { text: 'Redefinir senha', style: 'text-align:center; margin:0 0 .5rem;' }),
            el('p', { class: 'auth-tagline', text: 'Informe o e-mail da sua conta. Enviaremos um link para criar uma senha nova.' }),
            form,
            el('p', { class: 'auth-switch' }, [el('span', { text: 'Lembrou a senha?' }), loginButton()])
        ]));
        emailInput.focus();
    }

    function drawForgotSent() {
        // A mensagem recebe o foco: um role=status criado já com o texto não é anunciado.
        const message = el('p', {
            class: 'auth-tagline',
            role: 'status',
            tabindex: '-1',
            text: `Se houver uma conta com ${pendingEmail}, enviamos um link para redefinir a senha (vale por 30 minutos). Confira também a caixa de spam.`
        });
        wrap.replaceChildren(el('div', { class: 'card auth-card' }, [
            el('img', { class: 'auth-logo', src: 'image/money-pilot-logo.png', alt: 'moneyPilot' }),
            el('h2', { text: 'Confira seu e-mail', style: 'text-align:center; margin:0 0 .5rem;' }),
            message,
            el('p', { class: 'auth-switch' }, [el('span', { text: 'Já redefiniu?' }), loginButton()])
        ]));
        message.focus();
    }

    function draw() {
        if (mode === 'sent') {
            drawSent();
            return;
        }
        if (mode === 'forgot') {
            drawForgot();
            return;
        }
        if (mode === 'forgot-sent') {
            drawForgotSent();
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
            spellcheck: 'false',
            autocapitalize: 'none',
            required: true,
            value: pendingEmail,
            'aria-describedby': errorIds.email
        });
        // Cadastro: olho + medidor com o nome e o e-mail do formulário. Login: só o olho.
        const passwordControl = passwordField({
            id: inputIds.password,
            autocomplete: isLogin ? 'current-password' : 'new-password',
            describedBy: isLogin ? errorIds.password : `${hintId} ${errorIds.password}`,
            meter: !isLogin,
            getContext: () => ({ email: emailInput.value.trim(), name: nameInput.value.trim() }),
            minlength: isLogin ? null : '10',
            maxlength: isLogin ? null : '64'
        });
        const passwordInput = passwordControl.input;
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
                passwordControl.control,
                isLogin ? null : el('span', { class: 'meter-foot', id: hintId, text: HINT }),
                passwordControl.meter,
                fieldErrors.password
            ]),
            submitBtn
        ]);

        // O Google já confirmou o e-mail; o sucesso segue o caminho do login por senha.
        const google = googleButton({
            text: isLogin ? 'signin_with' : 'signup_with',
            separator: true,
            onCredential: async (credential) => {
                errorBox.hidden = true;
                try {
                    const auth = await api.loginWithGoogle(credential);
                    session.start(auth.token, auth.email);
                    onAuthenticated();
                } catch (error) {
                    errorBox.textContent = googleErrorMessage(error);
                    errorBox.hidden = false;
                }
            }
        });

        const card = el('div', { class: 'card auth-card' }, [
            el('img', { class: 'auth-logo', src: 'image/money-pilot-logo.png', alt: 'moneyPilot' }),
            form,
            google,
            isLogin ? el('p', { class: 'auth-note', text: 'Criou a conta com o Google? Use Entrar com Google.' }) : null,
            el('p', { class: 'auth-switch' }, [
                el('span', { text: isLogin ? 'Ainda não tem conta?' : 'Já tem conta?' }),
                el('button', {
                    type: 'button',
                    text: isLogin ? 'Criar agora' : 'Entrar',
                    onClick: () => { mode = isLogin ? 'register' : 'login'; draw(); }
                })
            ]),
            isLogin ? el('p', { class: 'auth-switch', style: 'margin-top:.25rem;' }, [
                el('span', { text: 'Esqueceu a senha?' }),
                el('button', {
                    type: 'button',
                    text: 'Redefinir',
                    onClick: () => { pendingEmail = emailInput.value.trim(); mode = 'forgot'; draw(); }
                })
            ]) : null,
            isLogin ? el('p', { class: 'auth-switch', style: 'margin-top:.25rem;' }, [
                el('span', { text: 'Não consegue entrar?' }),
                el('button', {
                    type: 'button',
                    text: 'Falar com o suporte',
                    onClick: () => { window.location.hash = '#/suporte'; }
                })
            ]) : null
        ]);

        wrap.replaceChildren(card);
        (isLogin ? emailInput : nameInput).focus();
    }

    draw();
    return wrap;
}
