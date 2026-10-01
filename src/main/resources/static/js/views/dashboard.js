import { api, fetchAll } from '../api.js';
import { el, fmtMoney, monthName, monthPicker, pageHead, emptyState, reportError } from '../ui.js';

const now = new Date();
let month = now.getMonth() + 1;
let year = now.getFullYear();
let forecastDays = 30;

const FORECAST_PERIODS = [30, 60, 90];

function statTile(label, value, tone, hint, hintTone) {
    return el('div', { class: 'card stat' }, [
        el('span', { class: 'label', text: label }),
        el('span', { class: `value ${tone || ''}`, text: value }),
        hint ? el('span', { class: `hint ${hintTone || ''}`, text: hint }) : null
    ]);
}

function plural(count, singular, pluralForm) {
    return `${count} ${count === 1 ? singular : pluralForm}`;
}

/** Tile de títulos em aberto — vencido em vermelho, mas sempre com contagem e valor escritos. */
function billTile(label, side) {
    const overdue = Number(side.overdueCount) || 0;
    const hint = overdue
        ? `${plural(overdue, 'vencido', 'vencidos')} · ${fmtMoney(side.overdueTotal)}`
        : `${plural(Number(side.count) || 0, 'título', 'títulos')} · nenhum vencido`;
    return statTile(label, fmtMoney(side.total), '', hint, overdue ? 'critical' : '');
}

function shortDate(isoDate) {
    const [, monthPart, day] = String(isoDate).split('-');
    return `${day}/${monthPart}`;
}

function signedMoney(value) {
    const number = Number(value) || 0;
    if (number === 0) return fmtMoney(0);
    return `${number < 0 ? '−' : '+'} ${fmtMoney(Math.abs(number))}`;
}

/** Saldo projetado por bloco de 7 dias — barra proporcional ao maior saldo, número sempre escrito. */
function forecastChart(forecast) {
    const buckets = forecast.buckets || [];
    const max = Math.max(...buckets.map((bucket) => Math.abs(Number(bucket.projectedBalance) || 0)), 0) || 1;

    return el('div', { class: 'bars' }, buckets.map((bucket) => {
        const projected = Number(bucket.projectedBalance) || 0;
        const negative = projected < 0;
        const period = bucket.days === 1
            ? shortDate(bucket.start)
            : `${shortDate(bucket.start)} – ${shortDate(bucket.end)}`;
        const moves = [];
        if (Number(bucket.receivable)) moves.push(`a receber ${fmtMoney(bucket.receivable)}`);
        if (Number(bucket.payable)) moves.push(`a pagar ${fmtMoney(bucket.payable)}`);

        return el('div', {
            class: 'bar-row',
            title: `${period}: saldo projetado ${fmtMoney(projected)} (${signedMoney(bucket.net)} no período)`
        }, [
            el('span', { class: 'name', text: period }),
            el('span', { class: `amount ${negative ? 'neg' : ''}`, text: fmtMoney(projected) }),
            el('div', { class: 'bar-track' }, [
                el('div', {
                    class: `bar-fill ${negative ? 'neg' : ''}`,
                    style: `width: ${Math.max((Math.abs(projected) / max) * 100, 2)}%`
                })
            ]),
            el('span', {
                class: 'detail',
                text: moves.length ? `${moves.join(' · ')} → ${signedMoney(bucket.net)}` : 'sem títulos no período'
            })
        ]);
    }));
}

function forecastNote(forecast) {
    const overdue = forecast.overdue || {};
    const count = Number(overdue.count) || 0;
    if (!count) {
        return el('p', {
            class: 'forecast-note',
            text: `Parte do saldo de hoje, ${fmtMoney(forecast.currentBalance)}, e soma os títulos em aberto até ${shortDate(forecast.to)}.`
        });
    }
    const parts = [];
    if (Number(overdue.payable)) parts.push(`${fmtMoney(overdue.payable)} a pagar`);
    if (Number(overdue.receivable)) parts.push(`${fmtMoney(overdue.receivable)} a receber`);
    return el('p', {
        class: 'forecast-note critical',
        text: `${plural(count, 'título vencido entra', 'títulos vencidos entram')} no primeiro período: ${parts.join(' e ')}.`
    });
}

/** Card da previsão: recarrega só a si mesmo quando o período muda. */
function forecastCard(initial) {
    const card = el('div', { class: 'card', style: 'margin-top:1rem' });

    function render(forecast) {
        const picker = el('div', { class: 'segmented', role: 'group', 'aria-label': 'Período da previsão' },
            FORECAST_PERIODS.map((days) => el('button', {
                type: 'button',
                'aria-pressed': days === forecastDays ? 'true' : 'false',
                text: `${days} dias`,
                onClick: () => change(days)
            })));

        card.replaceChildren(
            el('div', { class: 'card-head' }, [el('h2', { text: 'Fluxo de caixa projetado' }), picker]),
            forecast ? forecastNote(forecast) : '',
            forecast ? forecastChart(forecast) : el('div', { class: 'empty', text: 'Carregando…' })
        );
    }

    async function change(days) {
        if (days === forecastDays) return;
        forecastDays = days;
        render(null);
        try {
            render(await api.reports.cashFlowForecast(forecastDays));
        } catch (error) {
            reportError(error);
            card.replaceChildren(emptyState(error?.message || 'Não foi possível carregar a previsão.'));
        }
    }

    render(initial);
    return card;
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
            const [summary, spending, budgets, balances, billsSummary, forecast] = await Promise.all([
                api.reports.monthlySummary(month, year),
                api.reports.spendingByCategory(month, year),
                api.reports.budgetVsActual(month, year),
                loadBalances(),
                api.reports.billsSummary(),
                api.reports.cashFlowForecast(forecastDays)
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
                    billTile('A pagar', billsSummary.payable),
                    billTile('A receber', billsSummary.receivable)
                ]),
                forecastCard(forecast),
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
