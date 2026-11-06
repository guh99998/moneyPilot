import { api, fetchAll, normalizePage } from '../api.js';
import {
    el, fmtMoney, fmtDate, badge, toast, reportError, pageHead, emptyState, formDialog, todayIso
} from '../ui.js';

export const INVOICE_STATUS = {
    OPEN: { label: 'Aberta', kind: 'open' },
    CLOSED: { label: 'Fechada', kind: 'open' },
    OVERDUE: { label: 'Vencida', kind: 'overdue' },
    PAID: { label: 'Paga', kind: 'settled' },
    FUTURE: { label: 'Futura', kind: 'canceled' }
};

const toCents = (value) => Math.round(Number(value) * 100);
const fromCents = (cents) => (cents / 100).toFixed(2);

let selectedCardId = null;

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
    return el('li', { class: 'invoice-purchase' }, [
        el('span', { class: 'invoice-purchase-date', text: fmtDate(transaction.date) }),
        el('span', { class: 'invoice-purchase-desc', text: label + installment }),
        el('span', {
            class: 'invoice-purchase-amount',
            style: `color: var(--${income ? 'income' : 'expense'})`,
            text: `${income ? '+' : '−'} ${fmtMoney(transaction.amount)}`
        })
    ]);
}

/** Compras do ciclo, carregadas só quando a pessoa abre a lista. */
function purchasesDetails(card, invoice) {
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
            body.replaceChildren(result.content.length
                ? el('ul', { class: 'invoice-purchase-list' }, result.content.map(purchaseLine))
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
        el('summary', { text: `Compras de ${fmtDate(invoice.periodStart)} a ${fmtDate(invoice.periodEnd)}` }),
        body
    ]);
}

export async function renderInvoices() {
    const view = el('div');
    let accounts = [];

    function head(actions = []) {
        return pageHead('Faturas', 'Quanto está a fatura do cartão e quando ela vence', actions);
    }

    function payDialog(card, amount) {
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
                load();
            }
        });
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

    function payButton(card, invoices, index) {
        if (invoices[index].status === 'FUTURE' || Number(invoices[index].remaining) <= 0) return null;
        return el('button', {
            type: 'button',
            class: 'btn-primary btn-sm',
            text: 'Pagar fatura',
            'aria-label': `Pagar fatura com vencimento em ${fmtDate(invoices[index].dueDate)}`,
            onClick: () => payDialog(card, payableThrough(invoices, index))
        });
    }

    function currentCard(card, invoices, index) {
        const invoice = invoices[index];
        const earlierOwed = invoices.slice(0, index).reduce((sum, item) => sum + toCents(item.remaining), 0);
        const facts = [
            el('span', { text: `Fecha em ${fmtDate(invoice.closingDate)}` }),
            el('span', { text: `Vence em ${fmtDate(invoice.dueDate)}` })
        ];
        if (Number(invoice.paid) > 0) facts.push(el('span', { text: `Já pago: ${fmtMoney(invoice.paid)}` }));

        return el('section', { class: 'card invoice-current', 'aria-labelledby': 'invoice-current-title' }, [
            el('div', { class: 'card-head' }, [
                el('h2', { id: 'invoice-current-title', text: 'Fatura atual' }),
                statusBadge(invoice.status)
            ]),
            el('p', { class: 'invoice-figure', text: fmtMoney(invoice.total) }),
            el('p', { class: 'invoice-facts' }, facts),
            earlierOwed > 0
                ? el('p', { class: 'forecast-note critical', text: `Há ${fmtMoney(earlierOwed / 100)} em faturas anteriores ainda em aberto.` })
                : null,
            el('div', { class: 'invoice-actions' }, [
                payButton(card, invoices, index),
                el('button', { type: 'button', class: 'btn-sm', text: 'Nova compra', onClick: () => purchaseDialog(card) }),
                el('button', { type: 'button', class: 'btn-sm', text: 'Compra parcelada', onClick: () => installmentDialog(card) })
            ]),
            purchasesDetails(card, invoice)
        ]);
    }

    function otherInvoices(card, invoices, currentIndex) {
        const rows = invoices
            .map((invoice, index) => ({ invoice, index }))
            .filter(({ index }) => index !== currentIndex)
            .reverse();

        if (!rows.length) return null;

        return el('section', { class: 'card', 'aria-labelledby': 'invoice-list-title' }, [
            el('div', { class: 'card-head' }, [el('h2', { id: 'invoice-list-title', text: 'Outras faturas' })]),
            el('ul', { class: 'invoice-list' }, rows.map(({ invoice, index }) => el('li', { class: 'invoice-item' }, [
                el('div', { class: 'invoice-item-head' }, [
                    el('div', { class: 'invoice-item-main' }, [
                        el('strong', { text: `Vence em ${fmtDate(invoice.dueDate)}` }),
                        statusBadge(invoice.status)
                    ]),
                    el('div', { class: 'invoice-item-amounts' }, [
                        el('span', { class: 'invoice-item-total', text: fmtMoney(invoice.total) }),
                        Number(invoice.remaining) > 0 && Number(invoice.paid) > 0
                            ? el('span', { class: 'meter-foot', text: `Falta ${fmtMoney(invoice.remaining)}` })
                            : null
                    ]),
                    payButton(card, invoices, index)
                ]),
                purchasesDetails(card, invoice)
            ])))
        ]);
    }

    function cardPicker(cards, card) {
        if (cards.length < 2) return null;
        return el('div', { class: 'field invoice-picker' }, [
            el('label', { for: 'invoice-card', text: 'Cartão' }),
            el('select', {
                id: 'invoice-card',
                onChange: (event) => {
                    selectedCardId = Number(event.target.value);
                    load();
                }
            }, cards.map((option) => el('option', { value: option.id, text: option.name, selected: option.id === card.id })))
        ]);
    }

    async function load() {
        view.replaceChildren(head(), el('div', { class: 'empty', text: 'Carregando…' }));

        try {
            accounts = await fetchAll(api.accounts.list);
            const cards = accounts.filter((account) => account.type === 'CREDIT_CARD' && account.closingDay);

            if (!cards.length) {
                view.replaceChildren(head(), el('div', { class: 'card' }, [
                    emptyState('Para ver a fatura, informe o dia do fechamento e o do vencimento de um cartão de crédito.'),
                    el('p', { class: 'invoice-empty-link' }, [
                        el('a', { href: '#/accounts', text: 'Ir para Contas' })
                    ])
                ]));
                return;
            }

            const card = cards.find((option) => option.id === selectedCardId) || cards[0];
            selectedCardId = card.id;
            const invoices = await api.accounts.invoices(card.id);
            const currentIndex = invoices.findIndex((invoice) => invoice.status === 'OPEN');

            view.replaceChildren(
                head(),
                cardPicker(cards, card) || '',
                currentIndex >= 0 ? currentCard(card, invoices, currentIndex) : '',
                otherInvoices(card, invoices, currentIndex) || ''
            );
        } catch (error) {
            reportError(error);
            view.replaceChildren(head(), emptyState(error?.message || 'Não foi possível carregar as faturas.'));
        }
    }

    await load();
    return view;
}
