import { api, fetchAll, normalizePage } from '../api.js';
import { el, fmtMoney, monthName, monthPicker, pageHead, emptyState, reportError, todayIso } from '../ui.js';
import { lineChart, groupedColumns, legend, tableToggle } from '../charts.js';
import { presetBillFilters } from './bills.js';

const now = new Date();
let month = now.getMonth() + 1;
let year = now.getFullYear();
let forecastDays = 30;

const FORECAST_PERIODS = [30, 60, 90];
const TREND_MONTHS = 6;
const SHORT_MONTHS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];
const INCOME = { name: 'Receitas', color: 'var(--chart-in)' };
const EXPENSE = { name: 'Despesas', color: 'var(--chart-out)' };

function plural(count, singular, pluralForm) {
    return `${count} ${count === 1 ? singular : pluralForm}`;
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

function card(title, sub, body, extraClass = '') {
    return el('section', { class: `card ${extraClass}` }, [
        el('div', { class: 'card-head' }, [el('h2', { text: title }), sub || null]),
        ...(Array.isArray(body) ? body : [body])
    ]);
}

function statTile(label, value, tone, hint, hintTone, href) {
    return el(href ? 'a' : 'div', { class: `card stat ${href ? 'stat-link' : ''}`, href }, [
        el('span', { class: 'label', text: label }),
        el('span', { class: `value ${tone || ''}`, text: value }),
        hint ? el('span', { class: `hint ${hintTone || ''}`, text: hint }) : null
    ]);
}

/**
 * Tile de títulos em aberto — vencido em vermelho, mas sempre com contagem e valor escritos.
 * Leva à lista já filtrada: só os vencidos quando houver, senão tudo em aberto daquele tipo.
 */
function billTile(label, side, type) {
    const overdue = Number(side.overdueCount) || 0;
    const hint = overdue
        ? `${plural(overdue, 'vencido', 'vencidos')} · ${fmtMoney(side.overdueTotal)}`
        : `${plural(Number(side.count) || 0, 'título', 'títulos')} · nenhum vencido`;
    const tile = statTile(label, fmtMoney(side.total), '', hint, overdue ? 'critical' : '', '#/bills');
    tile.addEventListener('click', () => presetBillFilters({ type, status: overdue ? 'OVERDUE' : 'OPEN' }));
    return tile;
}

// ---- destaque: o número que abre o painel ----

function hero(currentBalance, summary, forecast) {
    const buckets = forecast.buckets || [];
    const projected = buckets.length ? Number(buckets[buckets.length - 1].projectedBalance) : Number(currentBalance);
    const lowest = buckets.reduce((low, bucket) => (Number(bucket.projectedBalance) < Number(low.projectedBalance) ? bucket : low), buckets[0] || {});
    const result = Number(summary.balance) || 0;
    const delta = projected - Number(currentBalance);

    const facts = [
        el('span', {}, [
            `Em ${forecastDays} dias: `,
            el('b', { text: fmtMoney(projected) }),
            ' ',
            el('span', { class: delta < 0 ? 'down' : 'up', text: `(${signedMoney(delta)})` })
        ]),
        el('span', {}, [`Resultado de ${monthName(month)}: `, el('b', { text: signedMoney(result) })])
    ];
    if (lowest && Number(lowest.projectedBalance) < 0) {
        facts.push(el('span', { class: 'down' }, [
            'Fica negativo entre ',
            el('b', { text: `${shortDate(lowest.start)} e ${shortDate(lowest.end)}` })
        ]));
    }

    return el('section', { class: 'card hero' }, [
        el('span', { class: 'label', text: 'Saldo em contas hoje' }),
        el('span', { class: 'figure', text: fmtMoney(currentBalance) }),
        el('div', { class: 'facts' }, facts)
    ]);
}

// ---- previsão ----

function forecastNote(forecast) {
    const overdue = forecast.overdue || {};
    const count = Number(overdue.count) || 0;
    if (!count) {
        return el('p', { class: 'forecast-note', text: `Saldo de hoje mais os títulos em aberto até ${shortDate(forecast.to)}, semana a semana.` });
    }
    const parts = [];
    if (Number(overdue.payable)) parts.push(`${fmtMoney(overdue.payable)} a pagar`);
    if (Number(overdue.receivable)) parts.push(`${fmtMoney(overdue.receivable)} a receber`);
    return el('p', {
        class: 'forecast-note critical',
        text: `${plural(count, 'título vencido entra', 'títulos vencidos entram')} na primeira semana: ${parts.join(' e ')}.`
    });
}

function forecastPoints(forecast) {
    return [
        {
            label: 'hoje',
            value: forecast.currentBalance,
            title: 'Hoje',
            rows: [{ text: `Saldo: ${fmtMoney(forecast.currentBalance)}` }]
        },
        ...(forecast.buckets || []).map((bucket) => {
            const rows = [{ text: `Saldo projetado: ${fmtMoney(bucket.projectedBalance)}` }];
            if (Number(bucket.receivable)) rows.push({ color: INCOME.color, text: `A receber: ${fmtMoney(bucket.receivable)}` });
            if (Number(bucket.payable)) rows.push({ color: EXPENSE.color, text: `A pagar: ${fmtMoney(bucket.payable)}` });
            if (!Number(bucket.receivable) && !Number(bucket.payable)) rows.push({ text: 'Sem títulos no período' });
            return {
                label: shortDate(bucket.end),
                value: bucket.projectedBalance,
                title: bucket.days === 1 ? shortDate(bucket.start) : `${shortDate(bucket.start)} – ${shortDate(bucket.end)}`,
                rows
            };
        })
    ];
}

/** Card da previsão: recarrega só a si mesmo quando o período muda. */
function forecastCard(initial, onPeriodChange) {
    const section = el('section', { class: 'card span-main' });

    function render(forecast) {
        const picker = el('div', { class: 'segmented', role: 'group', 'aria-label': 'Período da previsão' },
            FORECAST_PERIODS.map((days) => el('button', {
                type: 'button',
                'aria-pressed': days === forecastDays ? 'true' : 'false',
                text: `${days} dias`,
                onClick: () => change(days)
            })));

        section.replaceChildren(
            el('div', { class: 'card-head' }, [el('h2', { text: 'Fluxo de caixa projetado' }), picker]),
            ...(forecast
                ? [
                    forecastNote(forecast),
                    lineChart({
                        points: forecastPoints(forecast),
                        height: window.matchMedia('(min-width: 1200px)').matches ? 300 : 240,
                        ariaLabel: `Saldo projetado para os próximos ${forecastDays} dias`
                    }),
                    tableToggle(['Período', 'A receber', 'A pagar', 'Saldo projetado'], (forecast.buckets || []).map((bucket) => [
                        `${shortDate(bucket.start)} – ${shortDate(bucket.end)}`,
                        fmtMoney(bucket.receivable),
                        fmtMoney(bucket.payable),
                        fmtMoney(bucket.projectedBalance)
                    ]))
                ]
                : [el('div', { class: 'skeleton' }, el('i', { class: 'tall' }))])
        );
    }

    async function change(days) {
        if (days === forecastDays) return;
        forecastDays = days;
        render(null);
        try {
            const forecast = await api.reports.cashFlowForecast(forecastDays);
            render(forecast);
            onPeriodChange(forecast);
        } catch (error) {
            reportError(error);
            section.replaceChildren(emptyState(error?.message || 'Não foi possível carregar a previsão.'));
        }
    }

    render(initial);
    return section;
}

// ---- próximos vencimentos ----

function upcomingCard(bills) {
    const today = todayIso();
    const body = bills.length
        ? el('ul', { class: 'upcoming' }, bills.map((bill) => {
            const late = bill.dueDate < today;
            const [, monthPart, day] = bill.dueDate.split('-');
            const payable = bill.type === 'PAYABLE';
            return el('li', { class: late ? 'is-overdue' : '' }, [
                el('div', { class: 'day' }, [el('b', { text: day }), el('span', { text: SHORT_MONTHS[Number(monthPart) - 1] })]),
                el('div', { class: 'what' }, [
                    el('strong', { text: bill.description || bill.categoryName }),
                    el('span', {
                        class: late ? 'late' : '',
                        text: late
                            ? `Vencido · ${payable ? 'a pagar' : 'a receber'}`
                            : `${bill.categoryName}${bill.installmentNumber ? ` · ${bill.installmentNumber}/${bill.installmentTotal}` : ''}`
                    })
                ]),
                el('span', { class: `amt ${payable ? 'out' : 'in'}`, text: `${payable ? '−' : '+'} ${fmtMoney(bill.amount)}` })
            ]);
        }))
        : emptyState('Nada em aberto. Os próximos vencimentos aparecem aqui.');

    const link = el('a', { class: 'card-link', href: '#/bills', text: 'Ver todos' });
    link.addEventListener('click', () => presetBillFilters({ status: 'OPEN' }));
    return card('Próximos vencimentos', link, body, 'span-side');
}

// ---- evolução mensal ----

function trendMonths() {
    return Array.from({ length: TREND_MONTHS }, (_, index) => {
        const date = new Date(year, month - 1 - (TREND_MONTHS - 1 - index), 1);
        return { month: date.getMonth() + 1, year: date.getFullYear() };
    });
}

function trendCard(months, summaries) {
    const groups = months.map((item, index) => ({
        label: SHORT_MONTHS[item.month - 1],
        title: `${monthName(item.month)} de ${item.year}`,
        values: [Number(summaries[index].totalIncome) || 0, Number(summaries[index].totalExpense) || 0]
    }));
    const empty = groups.every((group) => !group.values[0] && !group.values[1]);

    return card('Receitas e despesas', el('span', { class: 'sub', text: `últimos ${TREND_MONTHS} meses` }), empty
        ? emptyState('Ainda não há lançamentos nesses meses.')
        : [
            legend([INCOME, EXPENSE]),
            groupedColumns({ groups, series: [INCOME, EXPENSE], height: 230, ariaLabel: 'Receitas e despesas por mês' }),
            tableToggle(['Mês', 'Receitas', 'Despesas', 'Resultado'], months.map((item, index) => [
                `${SHORT_MONTHS[item.month - 1]}/${item.year}`,
                fmtMoney(summaries[index].totalIncome),
                fmtMoney(summaries[index].totalExpense),
                signedMoney(summaries[index].balance)
            ]))
        ], 'span-main');
}

// ---- blocos do mês ----

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
            el('span', { class: 'amount', text: `${fmtMoney(row.total)} · ${share}%` }),
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
                el('td', { class: 'num', style: Number(row.balance) < 0 ? 'color: var(--expense)' : '', text: fmtMoney(row.balance) })
            ])))
        ])
    ]);
}

async function loadBalances() {
    const accounts = await fetchAll(api.accounts.list);
    return Promise.all(accounts.map(async (account) => {
        try {
            const balance = await api.accounts.balance(account.id);
            return { name: account.name, balance: balance.currentBalance };
        } catch {
            return { name: account.name, balance: account.initialBalance };
        }
    }));
}

function skeleton() {
    return el('div', { class: 'skeleton' }, [el('i'), el('i'), el('i', { class: 'tall' }), el('i', { class: 'tall' })]);
}

export async function renderDashboard() {
    const view = el('div');

    function head() {
        return pageHead('Visão geral', `${monthName(month).replace(/^./, (c) => c.toUpperCase())} de ${year}`, [
            monthPicker({
                month, year, onChange: (nextMonth, nextYear) => {
                    month = nextMonth;
                    year = nextYear;
                    load();
                }
            })
        ]);
    }

    async function load() {
        view.replaceChildren(head(), skeleton());

        try {
            const months = trendMonths();
            const [summary, spending, budgets, balances, billsSummary, forecast, upcoming, ...trend] = await Promise.all([
                api.reports.monthlySummary(month, year),
                api.reports.spendingByCategory(month, year),
                api.reports.budgetVsActual(month, year),
                loadBalances(),
                api.reports.billsSummary(),
                api.reports.cashFlowForecast(forecastDays),
                api.bills.list({ status: 'OPEN', page: 0, size: 6 }),
                ...months.map((item) => api.reports.monthlySummary(item.month, item.year))
            ]);

            const heroSlot = el('div', { style: 'display:contents' }, hero(forecast.currentBalance, summary, forecast));
            const refreshHero = (next) => heroSlot.replaceChildren(hero(next.currentBalance, summary, next));

            view.replaceChildren(
                head(),
                el('div', { class: 'dash' }, [
                    heroSlot,
                    el('div', { class: 'kpis' }, [
                        statTile('Receitas do mês', fmtMoney(summary.totalIncome), 'pos'),
                        statTile('Despesas do mês', fmtMoney(summary.totalExpense), 'neg'),
                        billTile('A pagar', billsSummary.payable, 'PAYABLE'),
                        billTile('A receber', billsSummary.receivable, 'RECEIVABLE')
                    ]),
                    forecastCard(forecast, refreshHero),
                    upcomingCard(normalizePage(upcoming).content),
                    trendCard(months, trend),
                    card('Gastos por categoria', el('span', { class: 'sub', text: monthName(month) }), spendingChart(spending), 'span-side'),
                    card('Orçado x realizado', el('span', { class: 'sub', text: monthName(month) }), budgetMeters(budgets), 'span-half'),
                    card('Saldo por conta', null, accountBalances(balances), 'span-half')
                ])
            );
        } catch (error) {
            reportError(error);
            view.replaceChildren(head(), emptyState(error?.message || 'Não foi possível carregar os dados.'));
        }
    }

    await load();
    return view;
}
