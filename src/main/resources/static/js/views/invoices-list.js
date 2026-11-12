/**
 * Lista única de faturas de todos os cartões (spec 016, regras 27 e 28): funções puras de montagem,
 * filtro, ordenação e resumo. Tudo roda no navegador, sobre as faturas que a API devolve por cartão.
 */

import { fmtMoney, fmtDate } from '../ui.js';

/** A lista abre em "Não pagas" (spec 028, regra 13): `UNPAID` é tudo que não está `PAID`; `''` mostra todas. */
export const UNPAID = 'UNPAID';
export const EMPTY_FILTERS = { cardId: '', status: UNPAID, dueFrom: '', dueTo: '' };

export const SORT_OPTIONS = [
    { key: 'dueDate', label: 'Vencimento', asc: 'mais antiga primeiro', desc: 'mais nova primeiro' },
    { key: 'total', label: 'Total', asc: 'menor primeiro', desc: 'maior primeiro' },
    { key: 'remaining', label: 'Falta pagar', asc: 'menor primeiro', desc: 'maior primeiro' },
    { key: 'card', label: 'Cartão', asc: 'A → Z', desc: 'Z → A' },
    { key: 'status', label: 'Situação', asc: 'vencidas primeiro', desc: 'pagas primeiro' }
];

// Ordem de urgência: a vencida primeiro, a paga por último.
const STATUS_ORDER = { OVERDUE: 0, CLOSED: 1, OPEN: 2, FUTURE: 3, PAID: 4 };

const toCents = (value) => Math.round(Number(value) * 100);

/**
 * Uma linha por fatura. `payAmount` é quanto falta pagar até aquela fatura **no mesmo cartão**, porque o
 * pagamento abate da fatura mais antiga; a fatura futura não entra na conta, já que ainda não se paga.
 */
export function buildRows(cards, invoicesByCard) {
    const rows = [];
    for (const card of cards) {
        let owed = 0;
        for (const invoice of invoicesByCard.get(card.id) || []) {
            if (invoice.status !== 'FUTURE') owed += toCents(invoice.remaining);
            rows.push({ card, invoice, payAmount: (owed / 100).toFixed(2) });
        }
    }
    return rows;
}

export function filterInvoices(rows, filters) {
    return rows.filter(({ card, invoice }) => {
        if (filters.cardId && String(card.id) !== String(filters.cardId)) return false;
        if (filters.status === UNPAID ? invoice.status === 'PAID' : filters.status && invoice.status !== filters.status) return false;
        if (filters.dueFrom && invoice.dueDate < filters.dueFrom) return false;
        if (filters.dueTo && invoice.dueDate > filters.dueTo) return false;
        return true;
    });
}

const VALUE = {
    dueDate: ({ invoice }) => invoice.dueDate,
    total: ({ invoice }) => toCents(invoice.total),
    remaining: ({ invoice }) => toCents(invoice.remaining),
    card: ({ card }) => card.name,
    status: ({ invoice }) => STATUS_ORDER[invoice.status] ?? 9
};

export function sortInvoices(rows, sort) {
    const factor = sort.dir === 'asc' ? 1 : -1;
    const value = VALUE[sort.key] || VALUE.dueDate;
    return [...rows].sort((a, b) => {
        const left = value(a);
        const right = value(b);
        const order = typeof left === 'string'
            ? left.localeCompare(right, 'pt-BR', { sensitivity: 'base' })
            : left - right;
        return order * factor
            || a.invoice.dueDate.localeCompare(b.invoice.dueDate) * factor
            || a.card.id - b.card.id;
    });
}

/** Resumo do topo, em centavos inteiros: o que há a pagar (aberta, fechada e vencida), o vencido e as abertas. */
export function summarize(rows) {
    const summary = { payableCents: 0, overdueCents: 0, overdueCount: 0, openCount: 0 };
    for (const { invoice } of rows) {
        if (invoice.status === 'FUTURE' || invoice.status === 'PAID') continue;
        const remaining = toCents(invoice.remaining);
        summary.payableCents += remaining;
        if (invoice.status === 'OVERDUE') {
            summary.overdueCents += remaining;
            summary.overdueCount += 1;
        }
        if (invoice.status === 'OPEN') summary.openCount += 1;
    }
    return summary;
}

/** Fatura aberta toda paga (spec 028, regra 17): ainda recebe compras até fechar, mas hoje não falta nada. */
export function isPaidInAdvance(invoice) {
    return invoice.status === 'OPEN' && toCents(invoice.total) > 0 && toCents(invoice.remaining) <= 0;
}

/**
 * Prévia do diálogo "Pagar fatura" (spec 028, regra 16): o valor abate da fatura mais antiga para a mais nova,
 * como no servidor (paga e futura ficam de fora). `invoices` são as do cartão, em ordem de vencimento.
 */
export function paymentPreview(invoices, amountCents) {
    if (!Number.isFinite(amountCents) || amountCents <= 0) return '';
    let left = amountCents;
    const steps = [];
    let lastRemaining = 0;
    for (const invoice of invoices) {
        if (left <= 0) break;
        if (invoice.status === 'FUTURE') continue;
        const owed = toCents(invoice.remaining);
        if (owed <= 0) continue;
        const applied = Math.min(left, owed);
        left -= applied;
        lastRemaining = owed - applied;
        steps.push({ due: fmtDate(invoice.dueDate), settles: applied === owed });
    }
    if (!steps.length) return '';

    const parts = steps.map((step) => `${step.settles ? 'quita' : 'abate'} a fatura com vencimento em ${step.due}`);
    let text = `Pagando, ${parts.join(' e ')}`;
    if (left > 0) return `${text}; sobram ${fmtMoney(left / 100)} de crédito para as próximas faturas.`;
    if (lastRemaining > 0) text += `, que vai ficar em ${fmtMoney(lastRemaining / 100)}`;
    return `${text}.`;
}
