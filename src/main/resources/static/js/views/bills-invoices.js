import { api } from '../api.js';
import { el, fmtMoney, fmtDate, badge } from '../ui.js';
import { payableThrough, selectInvoiceCard } from './invoices.js';

/**
 * Fatura do cartão como linha de "A pagar e receber" (spec 016, regras 17 a 20). A fatura é calculada
 * pelo servidor, não é um título: por isso as linhas ficam no topo da primeira página, fora da
 * paginação, da ordenação e da seleção em lote.
 */

// Situação da fatura nesta tela: a fechada ainda não venceu, então é "a vencer".
const STATUS = {
    OPEN: { label: 'Aberta', kind: 'open' },
    CLOSED: { label: 'A vencer', kind: 'open' },
    OVERDUE: { label: 'Vencida', kind: 'overdue' }
};

/** Os filtros que a fatura nunca satisfaz: receber, baixado, cancelado e qualquer categoria. */
function filtersAllowInvoices(filters) {
    if (filters.type && filters.type !== 'PAYABLE') return false;
    if (filters.status === 'SETTLED' || filters.status === 'CANCELED') return false;
    if (filters.categoryId) return false;
    return true;
}

export function invoiceMatchesFilters(invoice, filters) {
    if (!filtersAllowInvoices(filters)) return false;
    if (filters.status === 'OVERDUE' && invoice.status !== 'OVERDUE') return false;
    if (filters.accountId && String(filters.accountId) !== String(invoice.accountId)) return false;
    if (filters.dueDateFrom && invoice.dueDate < filters.dueDateFrom) return false;
    if (filters.dueDateTo && invoice.dueDate > filters.dueDateTo) return false;
    return true;
}

/** O pagamento abate da fatura mais antiga: pagar uma fatura é quitar também as anteriores do mesmo cartão. */
function withPayAmounts(invoices) {
    const byCard = new Map();
    return invoices.map((invoice) => {
        const sameCard = byCard.get(invoice.accountId) || [];
        sameCard.push(invoice);
        byCard.set(invoice.accountId, sameCard);
        return { invoice, payAmount: payableThrough(sameCard, sameCard.length - 1) };
    });
}

/** Busca as faturas a pagar; só na primeira página e só se os filtros deixam a fatura aparecer. Nunca lança. */
export async function loadInvoiceRows(filters, page) {
    if (page > 0 || !filtersAllowInvoices(filters)) return { rows: [], failed: false };

    try {
        const invoices = await api.invoices.payable();
        const rows = withPayAmounts(invoices).filter((row) => invoiceMatchesFilters(row.invoice, filters));
        return { rows, failed: false };
    } catch {
        return { rows: [], failed: true };
    }
}

/** Linha da tabela de títulos (as mesmas 8 colunas, sem caixa de seleção). */
export function invoiceRow(row, { onPay }) {
    const { invoice } = row;
    const info = STATUS[invoice.status] || STATUS.CLOSED;

    const notes = [];
    if (invoice.status === 'OPEN') notes.push(`fecha em ${fmtDate(invoice.closingDate)}, ainda recebe compras`);
    if (Number(invoice.paid) > 0) notes.push(`falta ${fmtMoney(invoice.remaining)}`);

    return el('tr', { class: ['invoice-row', invoice.status === 'OVERDUE' ? 'is-overdue' : ''].join(' ').trim() }, [
        el('td', { class: 'check' }),
        el('td', { class: 'due', text: fmtDate(invoice.dueDate) }),
        el('td', { class: 'desc' }, [
            el('span', { class: 'desc-main', text: `Fatura do ${invoice.accountName}` }),
            notes.length ? el('span', { class: 'cell-tag', text: notes.join(' · ') }) : null
        ]),
        el('td', { class: 'muted cat', text: 'Cartão de crédito' }),
        el('td', { class: 'muted acct', text: invoice.accountName }),
        el('td', { class: 'status' }, badge(info.kind, info.label)),
        el('td', { class: 'num amount-out', text: `− ${fmtMoney(invoice.total)}` }),
        el('td', { class: 'actions' }, [
            el('a', {
                href: '#/invoices',
                class: 'btn btn-sm',
                text: 'Ver itens da fatura',
                'aria-label': `Ver itens da fatura do ${invoice.accountName} com vencimento em ${fmtDate(invoice.dueDate)}`,
                onClick: () => selectInvoiceCard(invoice.accountId)
            }),
            el('button', {
                type: 'button',
                class: 'btn-sm btn-primary',
                text: 'Pagar fatura',
                'aria-label': `Pagar fatura do ${invoice.accountName} com vencimento em ${fmtDate(invoice.dueDate)}`,
                onClick: () => onPay(row)
            })
        ])
    ]);
}
