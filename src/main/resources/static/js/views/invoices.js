import { api, fetchAll, normalizePage } from '../api.js';
import {
    el, fmtMoney, fmtDate, badge, toast, reportError, pageHead, emptyState, formDialog, todayIso,
    collapsibleFilters, isCommittedDate, sortHeader, sortSelect, stackable
} from '../ui.js';
import { EMPTY_FILTERS, SORT_OPTIONS, buildRows, filterInvoices, sortInvoices, summarize } from './invoices-list.js';
import { openRecurringPurchaseDialog, projectedCharges, recurringPurchasesSection } from './invoices-recurring.js';

export const INVOICE_STATUS = {
    OPEN: { label: 'Aberta', kind: 'open' },
    CLOSED: { label: 'Fechada', kind: 'open' },
    OVERDUE: { label: 'Vencida', kind: 'overdue' },
    PAID: { label: 'Paga', kind: 'settled' },
    FUTURE: { label: 'Futura', kind: 'canceled' }
};

const toCents = (value) => Math.round(Number(value) * 100);
const fromCents = (cents) => (cents / 100).toFixed(2);

const DEFAULT_SORT = { key: 'dueDate', dir: 'desc' };
let filters = { ...EMPTY_FILTERS };
let sort = { ...DEFAULT_SORT };

/**
 * O pagamento abate da fatura mais antiga para a mais nova (regra 6 da spec 016), então pagar uma
 * fatura de verdade é quitar também as anteriores: este é o valor até ela, em centavos inteiros.
 */
export function payableThrough(invoices, index) {
    let cents = 0;
    for (let i = 0; i <= index; i++) cents += toCents(invoices[i].remaining);
    return fromCents(cents);
}

export function payInvoicePayload(values, cardId) {
    return {
        fromAccountId: Number(values.fromAccountId),
        toAccountId: cardId,
        amount: values.amount,
        date: values.date,
        description: values.description || 'Pagamento da fatura'
    };
}

export function installmentPayload(values, cardId) {
    return {
        accountId: cardId,
        categoryId: Number(values.categoryId),
        description: values.description,
        totalAmount: values.totalAmount,
        installments: Number(values.installments),
        date: values.date
    };
}

export function purchasePayload(values, cardId) {
    return {
        accountId: cardId,
        categoryId: Number(values.categoryId),
        amount: values.amount,
        type: 'EXPENSE',
        description: values.description,
        date: values.date
    };
}

/**
 * Abre a tela de Faturas já filtrada neste cartão (usado pela linha da fatura em A pagar e receber e em
 * Próximos vencimentos). Os outros filtros e a ordenação voltam ao padrão; id vazio limpa tudo.
 */
export function selectInvoiceCard(id) {
    filters = { ...EMPTY_FILTERS, cardId: id === '' || id === null || id === undefined ? '' : String(id) };
    sort = { ...DEFAULT_SORT };
}

/**
 * Diálogo de pagar fatura: uma transferência de outra conta para o cartão, não uma nova despesa.
 * `accounts` são todas as contas da pessoa; `onDone` roda depois que o pagamento foi registrado.
 */
export function openPayInvoiceDialog({ card, amount, accounts, onDone }) {
    const sources = accounts.filter((account) => account.type !== 'CREDIT_CARD' && account.id !== card.id);
    if (!sources.length) {
        toast('Cadastre uma conta (que não seja cartão) para pagar a fatura.', 'error');
        return;
    }
    formDialog({
        title: 'Pagar fatura',
        fields: [
            {
                name: 'fromAccountId', label: 'Pagar com', type: 'select', required: true,
                options: sources.map((account) => ({ value: account.id, label: account.name }))
            },
            {
                name: 'amount', label: 'Valor', type: 'number', step: '0.01', min: '0.01', required: true,
                hint: 'Pode ser parcial: o que sobrar continua na fatura. É uma transferência para o cartão, não uma nova despesa.'
            },
            { name: 'date', label: 'Data', type: 'date', required: true },
            { name: 'description', label: 'Descrição (opcional)', type: 'text', placeholder: 'Pagamento da fatura' }
        ],
        values: { amount, date: todayIso() },
        submitLabel: 'Pagar',
        onSubmit: async (values) => {
            await api.transactions.transfer(payInvoicePayload(values, card.id));
            toast('Pagamento registrado.', 'success');
            onDone?.();
        }
    });
}

function statusBadge(status) {
    const info = INVOICE_STATUS[status] || { label: status, kind: 'open' };
    return badge(info.kind, info.label);
}

function purchaseLine(transaction) {
    const isPayment = Boolean(transaction.transferGroupId);
    const income = transaction.type === 'INCOME';
    const label = isPayment ? 'Pagamento recebido' : (transaction.description || transaction.categoryName || '—');
    const installment = transaction.installmentNumber
        ? ` (${transaction.installmentNumber}/${transaction.installmentTotal})`
        : '';
    const recurring = transaction.recurringPurchaseId ? ' · recorrente' : '';
    return el('li', { class: 'invoice-purchase' }, [
        el('span', { class: 'invoice-purchase-date', text: fmtDate(transaction.date) }),
        el('span', { class: 'invoice-purchase-desc', text: label + installment + recurring }),
        el('span', {
            class: 'invoice-purchase-amount',
            style: `color: var(--${income ? 'income' : 'expense'})`,
            text: `${income ? '+' : '−'} ${fmtMoney(transaction.amount)}`
        })
    ]);
}

/** Cobrança de compra recorrente que ainda não virou despesa: aparece na fatura, mas não está gravada. */
function projectedLine(charge) {
    return el('li', { class: 'invoice-purchase is-projected' }, [
        el('span', { class: 'invoice-purchase-date', text: fmtDate(charge.date) }),
        el('span', { class: 'invoice-purchase-desc' }, [
            `${charge.description} · recorrente `,
            badge('canceled', 'Prevista')
        ]),
        el('span', { class: 'invoice-purchase-amount', style: 'color: var(--expense)', text: `− ${fmtMoney(charge.amount)}` })
    ]);
}

/** Compras do ciclo, carregadas só quando a pessoa abre a lista; `projected` são as cobranças previstas. */
function purchasesDetails(card, invoice, projected = []) {
    const body = el('div', { class: 'invoice-purchases-body', 'aria-live': 'polite' });
    let loaded = false;

    async function load() {
        body.replaceChildren(el('p', { class: 'meter-foot', text: 'Carregando…' }));
        try {
            const result = normalizePage(await api.transactions.list({
                accountId: card.id,
                from: invoice.periodStart,
                to: invoice.periodEnd,
                page: 0,
                size: 100,
                sort: ['date,asc', 'id,asc']
            }));
            const lines = [...result.content.map(purchaseLine), ...projected.map(projectedLine)];
            body.replaceChildren(lines.length
                ? el('ul', { class: 'invoice-purchase-list' }, lines)
                : el('p', { class: 'meter-foot', text: 'Nenhum lançamento neste ciclo.' }));
        } catch (error) {
            loaded = false;
            body.replaceChildren(el('p', { class: 'dialog-error', role: 'alert', text: error?.message || 'Não foi possível carregar as compras.' }));
        }
    }

    return el('details', {
        class: 'invoice-purchases',
        onToggle: (event) => {
            if (event.target.open && !loaded) {
                loaded = true;
                load();
            }
        }
    }, [
        el('summary', {
            text: 'Ver compras',
            'aria-label': `Ver compras de ${fmtDate(invoice.periodStart)} a ${fmtDate(invoice.periodEnd)}`
        }),
        body
    ]);
}

const STAT_ICON_CLASS = { neg: 'neg', pos: 'pos' };

function stat(label, value, tone) {
    return el('div', { class: 'stat' }, [
        el('span', { class: 'label', text: label }),
        ' ',
        el('span', { class: `value ${STAT_ICON_CLASS[tone] || ''}`.trim(), text: value })
    ]);
}

export async function renderInvoices() {
    const view = el('div');
    let accounts = [];
    let cards = [];
    let byCard = new Map();
    let allRows = [];
    let recurring = [];

    function head() {
        return pageHead('Faturas', 'Quanto está a fatura de cada cartão e quando ela vence');
    }

    async function expenseCategories() {
        try {
            return (await fetchAll(api.categories.list)).filter((category) => category.type === 'EXPENSE');
        } catch (error) {
            reportError(error);
            return null;
        }
    }

    async function purchaseDialog(card) {
        const categories = await expenseCategories();
        if (!categories) return;
        formDialog({
            title: 'Nova compra',
            fields: [
                {
                    name: 'categoryId', label: 'Categoria', type: 'select', required: true,
                    options: categories.map((category) => ({ value: category.id, label: category.name }))
                },
                { name: 'description', label: 'Descrição', type: 'text', placeholder: 'Ex.: Mercado' },
                { name: 'amount', label: 'Valor', type: 'number', step: '0.01', min: '0.01', required: true },
                {
                    name: 'date', label: 'Data da compra', type: 'date', required: true,
                    hint: 'A compra entra na fatura do ciclo dessa data.'
                }
            ],
            values: { date: todayIso() },
            submitLabel: 'Lançar compra',
            onSubmit: async (values) => {
                await api.transactions.create(purchasePayload(values, card.id));
                toast('Compra lançada.', 'success');
                load();
            }
        });
    }

    async function recurringDialog(card) {
        const categories = await expenseCategories();
        if (!categories) return;
        openRecurringPurchaseDialog({ card, categories, onSaved: load });
    }

    function projectedOf(card, invoice) {
        return projectedCharges(recurring.filter((purchase) => purchase.accountId === card.id), invoice, card.invoicesFrom);
    }

    async function installmentDialog(card) {
        const categories = await expenseCategories();
        if (!categories) return;
        formDialog({
            title: 'Compra parcelada',
            fields: [
                {
                    name: 'categoryId', label: 'Categoria', type: 'select', required: true,
                    options: categories.map((category) => ({ value: category.id, label: category.name }))
                },
                { name: 'description', label: 'Descrição', type: 'text', placeholder: 'Ex.: Notebook' },
                {
                    name: 'totalAmount', label: 'Valor total', type: 'number', step: '0.01', min: '0.02', required: true,
                    hint: 'O centavo que sobrar da divisão fica na primeira parcela.'
                },
                { name: 'installments', label: 'Parcelas', type: 'number', step: '1', min: 2, max: 60, required: true },
                {
                    name: 'date', label: 'Data da compra', type: 'date', required: true,
                    hint: 'As outras parcelas caem no mesmo dia dos meses seguintes. Compra em andamento: use a data original.'
                }
            ],
            values: { date: todayIso(), installments: 2 },
            submitLabel: 'Lançar parcelas',
            onSubmit: async (values) => {
                await api.transactions.createInstallments(installmentPayload(values, card.id));
                toast('Compra parcelada lançada.', 'success');
                load();
            }
        });
    }

    function payButton({ card, invoice, payAmount }) {
        if (invoice.status === 'FUTURE' || Number(invoice.remaining) <= 0) return null;
        return el('button', {
            type: 'button',
            class: 'btn-primary btn-sm',
            text: 'Pagar fatura',
            'aria-label': `Pagar fatura do ${card.name} com vencimento em ${fmtDate(invoice.dueDate)}`,
            onClick: () => openPayInvoiceDialog({ card, amount: payAmount, accounts, onDone: load })
        });
    }

    function summaryCard() {
        const totals = summarize(allRows);
        return el('section', { class: 'card invoice-summary', 'aria-label': 'Resumo das faturas' }, [
            stat('A pagar', fmtMoney(totals.payableCents / 100)),
            stat('Vencido', fmtMoney(totals.overdueCents / 100), totals.overdueCents > 0 ? 'neg' : ''),
            stat('Faturas', `${totals.openCount} ${totals.openCount === 1 ? 'aberta' : 'abertas'}`)
        ]);
    }

    function currentCard(card) {
        const invoices = byCard.get(card.id) || [];
        const index = invoices.findIndex((invoice) => invoice.status === 'OPEN');
        if (index < 0) return null;
        const invoice = invoices[index];
        const row = { card, invoice, payAmount: payableThrough(invoices, index) };
        const earlierOwed = invoices.slice(0, index).reduce((sum, item) => sum + toCents(item.remaining), 0);
        const facts = [
            el('span', { text: `Fecha em ${fmtDate(invoice.closingDate)}` }),
            el('span', { text: `Vence em ${fmtDate(invoice.dueDate)}` })
        ];
        if (Number(invoice.paid) > 0) facts.push(el('span', { text: `Já pago: ${fmtMoney(invoice.paid)}` }));
        if (Number(invoice.projectedTotal) > 0) facts.push(el('span', { text: `Previsto: ${fmtMoney(invoice.projectedTotal)} em compras recorrentes` }));
        const titleId = `invoice-current-${card.id}`;

        return el('section', { class: 'card invoice-current', 'aria-labelledby': titleId }, [
            el('div', { class: 'card-head' }, [
                el('h2', { id: titleId, text: `Fatura atual · ${card.name}` }),
                statusBadge(invoice.status)
            ]),
            el('p', { class: 'invoice-figure', text: fmtMoney(invoice.total) }),
            el('p', { class: 'invoice-facts' }, facts),
            card.invoicesFrom ? el('p', { class: 'meter-foot', text: `Faturas contam desde ${fmtDate(card.invoicesFrom)}` }) : null,
            earlierOwed > 0
                ? el('p', { class: 'forecast-note critical', text: `Há ${fmtMoney(earlierOwed / 100)} em faturas anteriores ainda em aberto.` })
                : null,
            el('div', { class: 'invoice-actions' }, [
                payButton(row),
                el('button', { type: 'button', class: 'btn-sm', text: 'Nova compra', onClick: () => purchaseDialog(card) }),
                el('button', { type: 'button', class: 'btn-sm', text: 'Compra parcelada', onClick: () => installmentDialog(card) }),
                el('button', { type: 'button', class: 'btn-sm', text: 'Compra recorrente', onClick: () => recurringDialog(card) })
            ])
        ]);
    }

    function selectField(label, key, options, allLabel) {
        const id = `invoice-filter-${key}`;
        const select = el('select', {
            id,
            onChange: () => { filters[key] = select.value; render(); }
        }, [
            el('option', { value: '', text: allLabel }),
            ...options.map((option) => el('option', { value: option.value, text: option.label }))
        ]);
        select.value = String(filters[key]);
        return el('div', { class: 'field' }, [el('label', { for: id, text: label }), select]);
    }

    function dateField(label, key) {
        const id = `invoice-filter-${key}`;
        const input = el('input', {
            id,
            type: 'date',
            value: filters[key],
            min: '1900-01-01',
            max: '2200-12-31',
            onChange: () => {
                if (!isCommittedDate(input.value)) return;
                filters[key] = input.value;
                render();
            }
        });
        return el('div', { class: 'field' }, [el('label', { for: id, text: label }), input]);
    }

    function filterBar() {
        const active = Object.entries(filters).filter(([name, value]) => value && value !== EMPTY_FILTERS[name]).length;
        return collapsibleFilters('invoices', el('div', { class: 'filters' }, [
            selectField('Cartão', 'cardId', cards.map((card) => ({ value: card.id, label: card.name })), 'Todos'),
            selectField('Situação', 'status', Object.entries(INVOICE_STATUS).map(([value, info]) => ({ value, label: info.label })), 'Todas'),
            dateField('Vence de', 'dueFrom'),
            dateField('até', 'dueTo'),
            el('button', {
                type: 'button',
                class: 'btn-sm',
                text: 'Limpar',
                onClick: () => { selectInvoiceCard(''); render(); }
            })
        ]), active);
    }

    function changeSort(next) {
        sort = next;
        render();
    }

    function table(rows) {
        return stackable(el('div', { class: 'table-wrap' }, [
            el('table', { class: 'invoices-table' }, [
                el('thead', {}, el('tr', {}, [
                    sortHeader({ label: 'Vencimento', key: 'dueDate', sort, onSort: changeSort }),
                    sortHeader({ label: 'Cartão', key: 'card', sort, onSort: changeSort }),
                    sortHeader({ label: 'Situação', key: 'status', sort, onSort: changeSort }),
                    sortHeader({ label: 'Total', key: 'total', sort, onSort: changeSort, numeric: true }),
                    sortHeader({ label: 'Falta pagar', key: 'remaining', sort, onSort: changeSort, numeric: true }),
                    el('th', { text: '' })
                ])),
                el('tbody', {}, rows.map((row) => el('tr', { class: row.invoice.status === 'OVERDUE' ? 'is-overdue' : null }, [
                    el('td', { class: 'due', text: fmtDate(row.invoice.dueDate) }),
                    el('td', { class: 'desc' }, [
                        el('span', { class: 'desc-main', text: row.card.name }),
                        purchasesDetails(row.card, row.invoice, projectedOf(row.card, row.invoice))
                    ]),
                    el('td', { class: 'status' }, statusBadge(row.invoice.status)),
                    el('td', { class: 'num' }, [
                        fmtMoney(row.invoice.total),
                        Number(row.invoice.projectedTotal) > 0
                            ? el('span', { class: 'meter-foot', text: `Inclui ${fmtMoney(row.invoice.projectedTotal)} previsto` })
                            : null
                    ]),
                    el('td', { class: 'num', text: Number(row.invoice.remaining) > 0 ? fmtMoney(row.invoice.remaining) : '—' }),
                    el('td', { class: 'actions' }, payButton(row))
                ])))
            ])
        ]));
    }

    function render() {
        const rows = sortInvoices(filterInvoices(allRows, filters), sort);
        view.replaceChildren(
            head(),
            summaryCard(),
            ...cards.map(currentCard).filter(Boolean),
            recurringPurchasesSection({ purchases: recurring, loadCategories: expenseCategories, cards, onChanged: load }),
            filterBar(),
            el('div', { class: 'sort-only' }, sortSelect({ options: SORT_OPTIONS, sort, onSort: changeSort })),
            el('div', { class: 'card' }, [rows.length ? table(rows) : emptyState('Nenhuma fatura com esses filtros.')])
        );
    }

    async function load() {
        view.replaceChildren(head(), el('div', { class: 'empty', text: 'Carregando…' }));

        try {
            accounts = await fetchAll(api.accounts.list);
            cards = accounts.filter((account) => account.type === 'CREDIT_CARD' && account.closingDay);

            if (!cards.length) {
                view.replaceChildren(head(), el('div', { class: 'card' }, [
                    emptyState('Para ver a fatura, informe o dia do fechamento e o do vencimento de um cartão de crédito.'),
                    el('p', { class: 'invoice-empty-link' }, [
                        el('a', { href: '#/accounts', text: 'Ir para Contas' })
                    ])
                ]));
                return;
            }

            const lists = await Promise.all(cards.map((card) => api.accounts.invoices(card.id)));
            recurring = await fetchAll(api.recurringPurchases.list).catch((error) => { reportError(error); return []; });
            byCard = new Map(cards.map((card, index) => [card.id, lists[index]]));
            allRows = buildRows(cards, byCard);
            render();
        } catch (error) {
            reportError(error);
            view.replaceChildren(head(), emptyState(error?.message || 'Não foi possível carregar as faturas.'));
        }
    }

    await load();
    return view;
}
