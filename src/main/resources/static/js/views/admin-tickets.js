import { api, normalizePage } from '../api.js';
import { el, pageHead, emptyState, pager, badge, reportError, toast } from '../ui.js';

const STATUSES = [
    ['OPEN', 'Aberto'],
    ['IN_PROGRESS', 'Em andamento'],
    ['RESOLVED', 'Resolvido']
];
const STATUS_LABEL = Object.fromEntries(STATUSES);
const STATUS_BADGE = { OPEN: 'open', IN_PROGRESS: 'transfer', RESOLVED: 'settled' };
const CATEGORY_LABEL = { DUVIDA: 'Dúvida', PROBLEMA: 'Problema', SUGESTAO: 'Sugestão', OUTRO: 'Outro' };

const dateTime = new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: 'America/Sao_Paulo'
});

function fmtDateTime(instant) {
    return instant ? dateTime.format(new Date(instant)) : '—';
}

/**
 * Painel de chamados do suporte (só administrador). A mensagem é texto da pessoa: sempre entra por `text`,
 * nunca por `html`.
 */
export async function renderAdminTickets() {
    const view = el('div');
    let statusFilter = 'OPEN';
    let page = 0;

    const statusBadge = (ticket) => badge(STATUS_BADGE[ticket.status] || 'open', STATUS_LABEL[ticket.status] || ticket.status);

    function head() {
        return pageHead('Chamados', 'Mensagens enviadas pelo "Falar com o suporte"');
    }

    function filterBar() {
        const id = 'f-ticket-status';
        const select = el('select', { id, name: 'status', autocomplete: 'off' }, [
            el('option', { value: '', text: 'Todos' }),
            ...STATUSES.map(([value, label]) => el('option', { value, text: label }))
        ]);
        select.value = statusFilter;
        select.addEventListener('change', () => {
            statusFilter = select.value;
            page = 0;
            loadList();
        });
        return el('div', { class: 'filters' }, [
            el('div', { class: 'field ticket-filter' }, [el('label', { for: id, text: 'Status' }), select])
        ]);
    }

    function row(ticket) {
        return el('tr', {}, [
            el('td', { dataset: { label: 'Referência' }, text: ticket.reference }),
            el('td', { class: 'muted', dataset: { label: 'Assunto' }, text: CATEGORY_LABEL[ticket.category] || ticket.category }),
            el('td', { dataset: { label: 'Status' } }, el('div', { class: 'ticket-badges' }, [
                statusBadge(ticket),
                ticket.emailSent ? null : badge('overdue', 'E-mail não enviado')
            ])),
            el('td', { class: 'muted', dataset: { label: 'E-mail' }, text: ticket.replyTo }),
            el('td', { class: 'muted', dataset: { label: 'Data' }, text: fmtDateTime(ticket.createdAt) }),
            el('td', { class: 'actions' }, [
                el('button', {
                    class: 'btn-ghost btn-sm',
                    type: 'button',
                    text: 'Abrir',
                    'aria-label': `Abrir o chamado ${ticket.reference}`,
                    onClick: () => openTicket(ticket.id)
                })
            ])
        ]);
    }

    function table(tickets) {
        return el('div', { class: 'table-wrap table-stack' }, el('table', {}, [
            el('thead', {}, el('tr', {}, ['Referência', 'Assunto', 'Status', 'E-mail', 'Data', 'Ações'].map((label) => el('th', { text: label })))),
            el('tbody', {}, tickets.map(row))
        ]));
    }

    async function loadList() {
        view.replaceChildren(head(), filterBar(), el('div', { class: 'empty', text: 'Carregando…' }));
        try {
            const result = normalizePage(await api.admin.tickets.list(statusFilter, page));
            const content = result.content.length
                ? el('div', { class: 'card' }, [
                    table(result.content),
                    pager({
                        page: result.number,
                        totalPages: result.totalPages,
                        totalElements: result.totalElements,
                        onChange: (next) => { page = next; loadList(); }
                    })
                ])
                : el('div', { class: 'card' }, [emptyState('Nenhum chamado com esse status.')]);
            view.replaceChildren(head(), filterBar(), content);
        } catch (error) {
            reportError(error);
            view.replaceChildren(head(), filterBar(), emptyState(error?.message || 'Não foi possível carregar os chamados.'));
        }
    }

    async function openTicket(id) {
        view.replaceChildren(head(), el('div', { class: 'empty', text: 'Carregando…' }));
        try {
            showTicket(await api.admin.tickets.get(id));
        } catch (error) {
            reportError(error);
            view.replaceChildren(head(), backButton(), emptyState(error?.message || 'Não foi possível abrir o chamado.'));
        }
    }

    function backButton() {
        return el('p', {}, el('button', { class: 'btn-ghost btn-sm', type: 'button', text: 'Voltar à lista', onClick: loadList }));
    }

    function showTicket(ticket) {
        const statusId = 'f-ticket-new-status';
        const select = el('select', { id: statusId, name: 'status', autocomplete: 'off' },
            STATUSES.map(([value, label]) => el('option', { value, text: label })));
        select.value = ticket.status;

        const saveBtn = el('button', { class: 'btn-primary', type: 'submit', text: 'Salvar status' });
        const form = el('form', {
            class: 'ticket-status-form',
            onSubmit: async (event) => {
                event.preventDefault();
                saveBtn.disabled = true;
                try {
                    showTicket(await api.admin.tickets.updateStatus(ticket.id, select.value));
                    toast('Status atualizado.', 'success');
                } catch (error) {
                    reportError(error);
                    saveBtn.disabled = false;
                }
            }
        }, [
            el('div', { class: 'field' }, [el('label', { for: statusId, text: 'Status' }), select]),
            saveBtn
        ]);

        // Ao abrir ou salvar, o foco vai para o título: a tela inteira foi trocada e o foco antigo sumiu.
        const heading = el('h2', { text: `Chamado ${ticket.reference}`, tabindex: '-1' });
        const meta = (label, value) => [el('dt', { text: label }), el('dd', {}, value)];
        view.replaceChildren(
            head(),
            backButton(),
            el('div', { class: 'card' }, [
                heading,
                el('dl', { class: 'ticket-meta' }, [
                    ...meta('Status', el('div', { class: 'ticket-badges' }, [
                        statusBadge(ticket),
                        ticket.emailSent ? null : badge('overdue', 'E-mail não enviado')
                    ])),
                    ...meta('Assunto', CATEGORY_LABEL[ticket.category] || ticket.category),
                    ...meta('Aberto em', fmtDateTime(ticket.createdAt)),
                    ...(ticket.statusChangedAt ? meta('Status mudou em', fmtDateTime(ticket.statusChangedAt)) : []),
                    ...(ticket.accountName ? meta('Conta', ticket.accountName) : []),
                    ...meta('Responder para', el('a', { href: `mailto:${ticket.replyTo}`, text: ticket.replyTo }))
                ]),
                el('p', { class: 'ticket-message', text: ticket.message }),
                form
            ])
        );
        heading.focus();
    }

    await loadList();
    return view;
}
