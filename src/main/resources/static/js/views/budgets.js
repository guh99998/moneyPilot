import { api, fetchAll, normalizePage } from '../api.js';
import {
    el, fmtMoney, monthName, monthPicker, toast, reportError,
    pageHead, emptyState, formDialog, confirmDialog, stackable
} from '../ui.js';

const now = new Date();
let month = now.getMonth() + 1;
let year = now.getFullYear();

const MONTH_OPTIONS = Array.from({ length: 12 }, (_, index) => ({
    value: index + 1,
    label: monthName(index + 1).charAt(0).toUpperCase() + monthName(index + 1).slice(1)
}));

export async function renderBudgets() {
    const view = el('div');
    const categories = await fetchAll(api.categories.list);
    const expenseCategories = categories.filter((category) => category.type === 'EXPENSE');

    function head() {
        return pageHead('Orçamentos', 'Um teto de gasto por categoria, mês a mês', [
            el('button', {
                class: 'btn-primary',
                text: 'Novo orçamento',
                disabled: !expenseCategories.length,
                onClick: () => openDialog()
            })
        ]);
    }

    function picker() {
        return monthPicker({
            month, year, onChange: (nextMonth, nextYear) => {
                month = nextMonth;
                year = nextYear;
                load();
            }
        });
    }

    function openDialog(budget) {
        formDialog({
            title: budget ? 'Editar orçamento' : 'Novo orçamento',
            fields: [
                {
                    name: 'categoryId', label: 'Categoria', type: 'select', required: true,
                    options: expenseCategories.map((category) => ({ value: category.id, label: category.name })),
                    hint: 'Orçamento se aplica a categorias de despesa.'
                },
                { name: 'amountLimit', label: 'Limite do mês', type: 'number', step: '0.01', min: '0.01', required: true },
                { name: 'month', label: 'Mês', type: 'select', options: MONTH_OPTIONS, required: true },
                { name: 'year', label: 'Ano', type: 'number', min: '2000', max: '2100', required: true }
            ],
            values: budget
                ? { categoryId: budget.categoryId, amountLimit: budget.amountLimit, month: budget.month, year: budget.year }
                : { month, year },
            onSubmit: async (values) => {
                const payload = {
                    categoryId: Number(values.categoryId),
                    amountLimit: values.amountLimit,
                    month: Number(values.month),
                    year: Number(values.year)
                };
                if (budget) await api.budgets.update(budget.id, payload);
                else await api.budgets.create(payload);
                toast(budget ? 'Orçamento atualizado.' : 'Orçamento criado.', 'success');
                month = payload.month;
                year = payload.year;
                load();
            }
        });
    }

    function askDelete(budget) {
        confirmDialog({
            title: 'Excluir orçamento',
            message: `O limite de ${fmtMoney(budget.amountLimit)} para "${budget.categoryName}" em ${monthName(budget.month)}/${budget.year} será removido.`,
            confirmLabel: 'Excluir',
            onConfirm: async () => {
                await api.budgets.remove(budget.id);
                toast('Orçamento excluído.', 'success');
                load();
            }
        });
    }

    function table(rows) {
        return stackable(el('div', { class: 'table-wrap' }, [
            el('table', {}, [
                el('thead', {}, el('tr', {}, [
                    el('th', { text: 'Categoria' }),
                    el('th', { class: 'num', text: 'Limite' }),
                    el('th', { class: 'num', text: 'Gasto' }),
                    el('th', { class: 'num', text: 'Saldo' }),
                    el('th', { text: '' })
                ])),
                el('tbody', {}, rows.map((row) => {
                    const hasActual = row.spentAmount !== undefined && row.spentAmount !== null;
                    const remaining = hasActual ? Number(row.remainingAmount) : null;
                    return el('tr', {}, [
                        el('td', { text: row.categoryName || `#${row.categoryId}` }),
                        el('td', { class: 'num', text: fmtMoney(row.amountLimit) }),
                        el('td', { class: 'num muted', text: hasActual ? fmtMoney(row.spentAmount) : '—' }),
                        el('td', {
                            class: 'num',
                            style: hasActual && remaining < 0 ? 'color: var(--expense)' : '',
                            text: hasActual
                                ? (remaining < 0 ? `− ${fmtMoney(Math.abs(remaining))}` : fmtMoney(remaining))
                                : '—'
                        }),
                        el('td', { class: 'actions' }, [
                            el('button', { class: 'btn-ghost btn-sm', text: 'Editar', onClick: () => openDialog(row) }),
                            el('button', { class: 'btn-ghost btn-sm btn-danger', text: 'Excluir', onClick: () => askDelete(row) })
                        ])
                    ]);
                }))
            ])
        ]));
    }

    async function load() {
        view.replaceChildren(head(), picker(), el('div', { class: 'empty', text: 'Carregando…' }));

        try {
            const [budgetPage, actuals] = await Promise.all([
                api.budgets.list({ month, year, page: 0, size: 100 }),
                api.reports.budgetVsActual(month, year).catch(() => [])
            ]);

            const spentByCategory = new Map(actuals.map((row) => [String(row.categoryId), row]));
            const rows = normalizePage(budgetPage).content.map((budget) => {
                const actual = spentByCategory.get(String(budget.categoryId));
                return {
                    ...budget,
                    categoryName: budget.categoryName || actual?.categoryName,
                    spentAmount: actual?.spentAmount,
                    remainingAmount: actual?.remainingAmount
                };
            });

            view.replaceChildren(head(), picker(), el('div', { class: 'card' }, [
                rows.length
                    ? table(rows)
                    : emptyState(`Nenhum orçamento para ${monthName(month)} de ${year}.`)
            ]));
        } catch (error) {
            reportError(error);
            view.replaceChildren(head(), picker(), emptyState(error?.message || 'Não foi possível carregar os orçamentos.'));
        }
    }

    await load();
    return view;
}
