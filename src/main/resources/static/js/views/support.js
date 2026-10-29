import { api, ApiError, normalizePage, session } from '../api.js';
import { el, pageHead, emptyState, pager, badge, reportError } from '../ui.js';

const MAX_MESSAGE = 2000;

const CATEGORIES = [
    ['DUVIDA', 'Dúvida'],
    ['PROBLEMA', 'Problema'],
    ['SUGESTAO', 'Sugestão'],
    ['OUTRO', 'Outro']
];

const STATUS_LABEL = { OPEN: 'Aberto', IN_PROGRESS: 'Em andamento', RESOLVED: 'Resolvido' };
const STATUS_BADGE = { OPEN: 'open', IN_PROGRESS: 'transfer', RESOLVED: 'settled' };
const CATEGORY_LABEL = Object.fromEntries(CATEGORIES);

const dateTime = new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: 'America/Sao_Paulo'
});
const fmtDateTime = (instant) => (instant ? dateTime.format(new Date(instant)) : '—');

// Referência do chamado que a tela deve abrir direto (clique no alerta do sino). Vale uma vez só.
let pendingReference = null;

/** Usado pelo sino: o router não tem parâmetros, então a tela abre o detalhe deste chamado ao ser montada. */
export function presetOpenTicket(reference) {
    pendingReference = reference;
}

/**
 * Card "Meus chamados": a lista dos chamados abertos com a conta e o detalhe de cada um. A mensagem é texto
 * da pessoa: sempre entra por `text`, nunca por `html`.
 */
function myTickets() {
    const card = el('div', { class: 'card support-card' });
    let page = 0;

    const statusBadge = (ticket) => badge(STATUS_BADGE[ticket.status] || 'open', STATUS_LABEL[ticket.status] || ticket.status);
    const title = () => el('h2', { text: 'Meus chamados', tabindex: '-1' });

    function row(ticket) {
        return el('tr', {}, [
            el('td', { dataset: { label: 'Referência' }, text: ticket.reference }),
            el('td', { class: 'muted', dataset: { label: 'Assunto' }, text: CATEGORY_LABEL[ticket.category] || ticket.category }),
            el('td', { dataset: { label: 'Status' } }, statusBadge(ticket)),
            el('td', { class: 'muted', dataset: { label: 'Aberto em' }, text: fmtDateTime(ticket.createdAt) }),
            el('td', { class: 'muted', dataset: { label: 'Status mudou em' }, text: fmtDateTime(ticket.statusChangedAt) }),
            el('td', { class: 'actions' }, [
                el('button', {
                    class: 'btn-ghost btn-sm',
                    type: 'button',
                    text: 'Abrir',
                    'aria-label': `Abrir o chamado ${ticket.reference}`,
                    onClick: () => openTicket(ticket.reference)
                })
            ])
        ]);
    }

    function table(tickets) {
        return el('div', { class: 'table-wrap table-stack' }, el('table', {}, [
            el('thead', {}, el('tr', {}, ['Referência', 'Assunto', 'Status', 'Aberto em', 'Status mudou em', 'Ações'].map((label) => el('th', { text: label })))),
            el('tbody', {}, tickets.map(row))
        ]));
    }

    async function loadList() {
        card.replaceChildren(title(), el('div', { class: 'empty', text: 'Carregando…' }));
        try {
            const result = normalizePage(await api.support.mine(page));
            if (!result.content.length) {
                card.replaceChildren(title(), emptyState(
                    'Você ainda não abriu chamados por aqui. Chamados enviados sem entrar na conta não aparecem nesta lista.'));
                return;
            }
            card.replaceChildren(...[
                title(),
                table(result.content),
                pager({
                    page: result.number,
                    totalPages: result.totalPages,
                    totalElements: result.totalElements,
                    onChange: (next) => { page = next; loadList(); }
                }),
                el('p', { class: 'meter-foot', text: 'Chamados enviados sem entrar na conta não aparecem nesta lista.' })
            ].filter(Boolean));
        } catch (error) {
            reportError(error);
            card.replaceChildren(title(), emptyState(error?.message || 'Não foi possível carregar os chamados.'));
        }
    }

    function backButton() {
        return el('p', {}, el('button', { class: 'btn-ghost btn-sm', type: 'button', text: 'Voltar à lista', onClick: loadList }));
    }

    async function openTicket(reference) {
        card.replaceChildren(title(), el('div', { class: 'empty', text: 'Carregando…' }));
        try {
            const ticket = await api.support.get(reference);
            showTicket(ticket);
            // O sino dispensa o alerta deste chamado: a pessoa já viu a mudança.
            window.dispatchEvent(new CustomEvent('support:ticket-opened', { detail: { reference: ticket.reference } }));
        } catch (error) {
            reportError(error);
            card.replaceChildren(title(), backButton(), emptyState(error?.message || 'Não foi possível abrir o chamado.'));
        }
    }

    function showTicket(ticket) {
        const meta = (label, value) => [el('dt', { text: label }), el('dd', {}, value)];
        // O conteúdo do card foi trocado e o foco antigo sumiu: o foco vai para o título do chamado.
        const heading = el('h2', { text: `Chamado ${ticket.reference}`, tabindex: '-1' });
        card.replaceChildren(...[
            backButton(),
            heading,
            el('dl', { class: 'ticket-meta' }, [
                ...meta('Status', statusBadge(ticket)),
                ...meta('Assunto', CATEGORY_LABEL[ticket.category] || ticket.category),
                ...meta('Aberto em', fmtDateTime(ticket.createdAt)),
                ...(ticket.statusChangedAt ? meta('Status mudou em', fmtDateTime(ticket.statusChangedAt)) : [])
            ]),
            el('p', { class: 'ticket-message', text: ticket.message }),
            el('p', { class: 'auth-tagline', text: `Respondemos no e-mail ${ticket.replyTo}.` }),
            ticket.status === 'RESOLVED'
                ? el('p', { class: 'auth-tagline', text: `Este chamado foi resolvido. Se precisar de mais ajuda, abra um chamado novo citando a referência ${ticket.reference}.` })
                : null
        ].filter(Boolean));
        heading.focus();
    }

    function start() {
        const reference = pendingReference;
        pendingReference = null;
        return reference ? openTicket(reference) : loadList();
    }

    return {
        node: card,
        start,
        reload: () => { page = 0; return loadList(); }
    };
}

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
    const categoryInput = el('select', { id: ids.category, name: 'category', required: true, 'aria-describedby': errorIds.category },
        CATEGORIES.map(([value, label]) => el('option', { value, text: label })));
    const messageInput = el('textarea', {
        id: ids.message,
        name: 'message',
        autocomplete: 'off',
        placeholder: 'Descreva o que aconteceu ou o que você precisa…',
        rows: '6',
        maxlength: String(MAX_MESSAGE),
        required: true,
        'aria-describedby': `${counterId} ${errorIds.message}`
    });
    const counter = el('span', { class: 'meter-foot support-count', id: counterId, text: `0/${MAX_MESSAGE}` });
    const emailInput = loggedIn ? null : el('input', {
        id: ids.email,
        name: 'email',
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
    const mine = loggedIn ? myTickets() : null;

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
        mine?.reload();
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
                    // Foco no primeiro campo com erro, na ordem da tela.
                    Object.values(inputs).find((input) => input?.getAttribute('aria-invalid'))?.focus();
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
        mine.start();
        return el('div', {}, [
            pageHead('Falar com o suporte', 'Dúvida, problema ou sugestão: a resposta chega no e-mail da sua conta.'),
            el('div', { class: 'card support-card' }, [body]),
            mine.node
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
