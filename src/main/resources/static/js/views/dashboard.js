import { api, fetchAll } from '../api.js';
import { el, fmtMoney, monthName, monthPicker, pageHead, emptyState, reportError } from '../ui.js';

const now = new Date();
let month = now.getMonth() + 1;
let year = now.getFullYear();

function statTile(label, value, tone, hint) {
    return el('div', { class: 'card stat' }, [
        el('span', { class: 'label', text: label }),
        el('span', { class: `value ${tone || ''}`, text: value }),
        hint ? el('span', { class: 'hint', text: hint }) : null
    ]);
}

/** Barras horizontais, série única (magnitude) — rótulo e valor diretos em cada barra. */
function spendingChart(rows) {
    if (!rows.length) return emptyState('Nenhuma despesa registrada neste mês.');

    const sorted = [...rows].sort((a, b) => Number(b.total) - Number(a.total));
    const max = Number(sorted[0].total) || 1;
    const sum = sorted.reduce((acc, row) => acc + Number(row.total), 0);

    return el('div', { class: 'bars' }, sorted.map((row) => {
        const share = sum ? Math.round((Number(row.total) / sum) * 100) : 0;
        return el('div', {
            class: 'bar-row',
            title: `${row.categoryName}: ${fmtMoney(row.total)} — ${share}% das despesas do mês`
        }, [
            el('span', { class: 'name', text: row.categoryName }),
            el('span', { class: 'amount', text: fmtMoney(row.total) }),
            el('div', { class: 'bar-track' }, [
                el('div', { class: 'bar-fill', style: `width: ${Math.max((Number(row.total) / max) * 100, 2)}%` })
            ])
        ]);
    }));
}

/** Medidores orçado x realizado — cor de status sempre acompanhada do percentual escrito. */
function budgetMeters(rows) {
    if (!rows.length) return emptyState('Nenhum orçamento definido para este mês.');

    return el('div', {}, rows.map((row) => {
        const limit = Number(row.budgetedAmount) || 0;
        const spent = Number(row.spentAmount) || 0;
        const pct = limit ? Math.round((spent / limit) * 100) : 0;
        const tone = pct > 100 ? 'critical' : pct > 75 ? 'warning' : 'good';
        const remaining = Number(row.remainingAmount);

        return el('div', { class: 'meter' }, [
            el('div', { class: 'meter-head' }, [
                el('span', { text: row.categoryName }),
                el('span', { class: 'pct', text: `${pct}%` })
            ]),
            el('div', { class: 'meter-track' }, [
                el('div', { class: `meter-fill ${tone}`, style: `width: ${Math.min(pct, 100)}%` })
            ]),
            el('div', {
                class: 'meter-foot',
                text: `${fmtMoney(spent)} de ${fmtMoney(limit)} · ${remaining < 0 ? `${fmtMoney(Math.abs(remaining))} acima do limite` : `${fmtMoney(remaining)} disponível`}`
            })
        ]);
    }));
}

function accountBalances(rows) {
    if (!rows.length) return emptyState('Cadastre uma conta para começar.');

    return el('div', { class: 'table-wrap' }, [
        el('table', {}, [
            el('thead', {}, el('tr', {}, [
                el('th', { text: 'Conta' }),
                el('th', { class: 'num', text: 'Saldo atual' })
            ])),
            el('tbody', {}, rows.map((row) => el('tr', {}, [
                el('td', { text: row.name }),
                el('td', { class: 'num', text: fmtMoney(row.balance) })
            ])))
        ])
    ]);
}

async function loadBalances() {
    const accounts = await fetchAll(api.accounts.list);
    const balances = await Promise.all(accounts.map(async (account) => {
        try {
            const balance = await api.accounts.balance(account.id);
            return { name: account.name, balance: balance.currentBalance };
        } catch {
            return { name: account.name, balance: account.initialBalance };
        }
    }));
    return balances;
}

export async function renderDashboard() {
    const view = el('div');

    async function load() {
        view.replaceChildren(
            pageHead('Visão geral', `${monthName(month)} de ${year}`),
            monthPicker({
                month, year, onChange: (nextMonth, nextYear) => {
                    month = nextMonth;
                    year = nextYear;
                    load();
                }
            }),
            el('div', { class: 'empty', text: 'Carregando…' })
        );

        try {
            const [summary, spending, budgets, balances] = await Promise.all([
                api.reports.monthlySummary(month, year),
                api.reports.spendingByCategory(month, year),
                api.reports.budgetVsActual(month, year),
                loadBalances()
            ]);

            const balance = Number(summary.balance);
            const totalOnAccounts = balances.reduce((acc, row) => acc + Number(row.balance ?? 0), 0);

            view.replaceChildren(
                pageHead('Visão geral', `${monthName(month)} de ${year}`),
                monthPicker({
                    month, year, onChange: (nextMonth, nextYear) => {
                        month = nextMonth;
                        year = nextYear;
                        load();
                    }
                }),
                el('div', { class: 'grid grid-3' }, [
                    statTile('Receitas do mês', fmtMoney(summary.totalIncome), 'pos'),
                    statTile('Despesas do mês', fmtMoney(summary.totalExpense), 'neg'),
                    statTile(
                        'Resultado do mês',
                        `${balance < 0 ? '−' : '+'} ${fmtMoney(Math.abs(balance))}`,
                        balance < 0 ? 'neg' : 'pos',
                        `Saldo somado das contas: ${fmtMoney(totalOnAccounts)}`
                    )
                ]),
                el('div', { class: 'grid grid-2', style: 'margin-top:1rem' }, [
                    el('div', { class: 'card' }, [
                        el('div', { class: 'card-head' }, [
                            el('h2', { text: 'Gastos por categoria' }),
                            el('span', { class: 'sub', text: 'transferências não entram' })
                        ]),
                        spendingChart(spending)
                    ]),
                    el('div', { class: 'card' }, [
                        el('div', { class: 'card-head' }, [
                            el('h2', { text: 'Orçado x realizado' }),
                            el('span', { class: 'sub', text: `${monthName(month)}` })
                        ]),
                        budgetMeters(budgets)
                    ])
                ]),
                el('div', { class: 'card', style: 'margin-top:1rem' }, [
                    el('div', { class: 'card-head' }, [el('h2', { text: 'Saldo por conta' })]),
                    accountBalances(balances)
                ])
            );
        } catch (error) {
            reportError(error);
            view.replaceChildren(
                pageHead('Visão geral', `${monthName(month)} de ${year}`),
                emptyState(error?.message || 'Não foi possível carregar os dados.')
            );
        }
    }

    await load();
    return view;
}
