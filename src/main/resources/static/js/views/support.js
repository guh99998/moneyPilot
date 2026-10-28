import { api, ApiError, session } from '../api.js';
import { el, pageHead } from '../ui.js';

const MAX_MESSAGE = 2000;

const CATEGORIES = [
    ['DUVIDA', 'Dúvida'],
    ['PROBLEMA', 'Problema'],
    ['SUGESTAO', 'Sugestão'],
    ['OUTRO', 'Outro']
];

/**
 * "Falar com o suporte": o chamado vira um e-mail para o suporte. Logado, o servidor usa o e-mail da conta;
 * sem login (quem não consegue entrar) a pessoa informa o e-mail para receber a resposta.
 */
export function renderSupport() {
    const loggedIn = session.active;
    const newId = (part) => `f-${part}-${Math.random().toString(36).slice(2, 7)}`;
    const ids = { category: newId('category'), message: newId('message'), email: newId('email') };
    const errorIds = { category: newId('category-error'), message: newId('message-error'), email: newId('email-error') };
    const counterId = newId('message-count');
    const fieldErrors = {
        category: el('span', { class: 'error', id: errorIds.category }),
        message: el('span', { class: 'error', id: errorIds.message }),
        email: el('span', { class: 'error', id: errorIds.email })
    };

    const errorBox = el('p', { class: 'dialog-error', hidden: true, role: 'alert' });
    const categoryInput = el('select', { id: ids.category, required: true, 'aria-describedby': errorIds.category },
        CATEGORIES.map(([value, label]) => el('option', { value, text: label })));
    const messageInput = el('textarea', {
        id: ids.message,
        rows: '6',
        maxlength: String(MAX_MESSAGE),
        required: true,
        'aria-describedby': `${counterId} ${errorIds.message}`
    });
    const counter = el('span', { class: 'meter-foot support-count', id: counterId, text: `0/${MAX_MESSAGE}` });
    const emailInput = loggedIn ? null : el('input', {
        id: ids.email,
        type: 'email',
        autocomplete: 'email',
        spellcheck: 'false',
        autocapitalize: 'none',
        required: true,
        'aria-describedby': errorIds.email
    });
    const inputs = { category: categoryInput, message: messageInput, email: emailInput };

    messageInput.addEventListener('input', () => {
        counter.textContent = `${messageInput.value.length}/${MAX_MESSAGE}`;
    });

    const submitBtn = el('button', {
        class: 'btn-primary',
        type: 'submit',
        style: 'width:100%; justify-content:center;',
        text: 'Enviar'
    });

    let sending = false;
    const body = el('div');

    function showSent({ reference, replyTo }) {
        // A mensagem recebe o foco: um role=status criado já com o texto não é anunciado.
        const message = el('p', {
            class: 'auth-tagline',
            role: 'status',
            tabindex: '-1',
            text: `Recebemos sua mensagem (chamado ${reference}). Respondemos no e-mail ${replyTo}.`
        });
        body.replaceChildren(message);
        message.focus();
    }

    const form = el('form', {
        onSubmit: async (event) => {
            event.preventDefault();
            if (sending) return;
            sending = true;
            submitBtn.disabled = true;
            submitBtn.textContent = 'Enviando…';
            errorBox.hidden = true;
            Object.values(fieldErrors).forEach((node) => { node.textContent = ''; });
            Object.values(inputs).forEach((input) => input?.removeAttribute('aria-invalid'));

            const payload = { category: categoryInput.value, message: messageInput.value };
            if (!loggedIn) payload.email = emailInput.value.trim();

            try {
                showSent(await api.sendSupportTicket(payload));
                return;
            } catch (error) {
                let shownOnField = false;
                if (error instanceof ApiError && error.fieldErrors) {
                    Object.entries(error.fieldErrors).forEach(([field, message]) => {
                        if (!fieldErrors[field] || !inputs[field]) return;
                        fieldErrors[field].textContent = message;
                        inputs[field].setAttribute('aria-invalid', 'true');
                        shownOnField = true;
                    });
                }
                if (!shownOnField) {
                    errorBox.textContent = error?.message || 'Não foi possível enviar agora. Tente de novo.';
                    errorBox.hidden = false;
                }
            }
            // Os campos mantêm o que foi digitado para a pessoa tentar de novo.
            sending = false;
            submitBtn.disabled = false;
            submitBtn.textContent = 'Enviar';
        }
    }, [
        errorBox,
        el('div', { class: 'field' }, [
            el('label', { for: ids.category, text: 'Assunto' }), categoryInput, fieldErrors.category
        ]),
        el('div', { class: 'field' }, [
            el('label', { for: ids.message, text: 'Mensagem' }), messageInput, counter, fieldErrors.message
        ]),
        loggedIn ? null : el('div', { class: 'field' }, [
            el('label', { for: ids.email, text: 'Seu e-mail (para a resposta)' }), emailInput, fieldErrors.email
        ]),
        submitBtn
    ]);
    body.append(form);

    if (loggedIn) {
        return el('div', {}, [
            pageHead('Falar com o suporte', 'Dúvida, problema ou sugestão: a resposta chega no e-mail da sua conta.'),
            el('div', { class: 'card support-card' }, [body])
        ]);
    }

    return el('div', { class: 'auth-wrap' }, [
        el('div', { class: 'card auth-card' }, [
            el('img', { class: 'auth-logo', src: 'image/money-pilot-logo.png', alt: 'moneyPilot' }),
            el('h2', { text: 'Falar com o suporte', style: 'text-align:center; margin:0 0 .5rem;' }),
            el('p', { class: 'auth-tagline', text: 'Conte o que está acontecendo. Respondemos no e-mail que você informar.' }),
            body,
            backToLogin()
        ])
    ]);
}

function backToLogin() {
    return el('p', { class: 'auth-switch' }, [
        el('button', { type: 'button', text: 'Voltar para o login', onClick: () => { window.location.hash = ''; } })
    ]);
}
