import { api } from '../api.js';
import { el, fmtMoney, monthName, pageHead, emptyState, reportError } from '../ui.js';
import { lineChart, groupedColumns, legend } from '../charts.js';
import { presetBillFilters } from './bills.js';

const SHORT_MONTHS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];
const PERIODS = [3, 6, 12];
const INCOME = { name: 'A receber', color: 'var(--chart-in)' };
const EXPENSE = { name: 'A pagar', color: 'var(--chart-out)' };

let months = 6;

function plural(count, singular, pluralForm) {
    return `${count} ${count === 1 ? singular : pluralForm}`;
}

function signedMoney(value) {
    const number = Number(value) || 0;
    if (number === 0) return fmtMoney(0);
    return `${number < 0 ? '−' : '+'} ${fmtMoney(Math.abs(number))}`;
}

function monthLabel(bucket) {
    return `${SHORT_MONTHS[bucket.month - 1]}/${String(bucket.year).slice(2)}`;
}

function monthTitle(bucket) {
    return `${monthName(bucket.month).replace(/^./, (c) => c.toUpperCase())} de ${bucket.year}`;
}

function card(title, sub, body, extraClass = '') {
    return el('section', { class: `card ${extraClass}` }, [
        el('div', { class: 'card-head' }, [el('h2', { text: title }), sub || null]),
        ...(Array.isArray(body) ? body : [body])
    ]);
}

function statTile(label, value, tone, hint, hintTone) {
    return el('div', { class: 'card stat' }, [
        el('span', { class: 'label', text: label }),
        el('span', { class: `value ${tone || ''}`, text: value }),
        hint ? el('span', { class: `hint ${hintTone || ''}`, text: hint }) : null
    ]);
}

function overdueNote(overdue) {
    const count = Number(overdue?.count) || 0;
    if (!count) return null;
    const parts = [];
    if (Number(overdue.payable)) parts.push(`${fmtMoney(overdue.payable)} a pagar`);
    if (Number(overdue.receivable)) parts.push(`${fmtMoney(overdue.receivable)} a receber`);
    return el('p', {
        class: 'forecast-note critical',
        text: `${plural(count, 'título vencido entra', 'títulos vencidos entram')} no primeiro mês: ${parts.join(' e ')}.`
    });
}

/** Abre a lista de títulos já filtrada pelo vencimento daquele mês. */
function billsLink(bucket) {
    const link = el('a', { class: 'card-link', href: '#/bills', text: 'Ver títulos' });
    link.addEventListener('click', () => presetBillFilters({ status: 'OPEN', dueDateFrom: bucket.start, dueDateTo: bucket.end }));
    return link;
}

function monthsTable(forecast) {
    return el('div', { class: 'table-wrap' }, [
        el('table', {}, [
            el('thead', {}, el('tr', {}, [
                el('th', { text: 'Mês' }),
                el('th', { class: 'num', text: 'A receber' }),
                el('th', { class: 'num', text: 'A pagar' }),
                el('th', { class: 'num', text: 'Resultado' }),
                el('th', { class: 'num', text: 'Saldo no fim do mês' }),
                el('th', { text: '' })
            ])),
            el('tbody', {}, forecast.months.map((bucket) => el('tr', {}, [
                el('td', { text: monthTitle(bucket) }),
                el('td', { class: 'num', text: fmtMoney(bucket.receivable) }),
                el('td', { class: 'num', text: fmtMoney(bucket.payable) }),
                el('td', { class: 'num', style: Number(bucket.net) < 0 ? 'color: var(--expense)' : '', text: signedMoney(bucket.net) }),
                el('td', { class: 'num', style: Number(bucket.projectedBalance) < 0 ? 'color: var(--expense)' : '', text: fmtMoney(bucket.projectedBalance) }),
                el('td', {}, Number(bucket.billCount) ? billsLink(bucket) : null)
            ])))
        ])
    ]);
}

function forecastView(forecast) {
    const list = forecast.months;
    const last = list[list.length - 1];
    const totalReceivable = list.reduce((sum, bucket) => sum + Number(bucket.receivable), 0);
    const totalPayable = list.reduce((sum, bucket) => sum + Number(bucket.payable), 0);
    const lowest = list.reduce((low, bucket) => (Number(bucket.projectedBalance) < Number(low.projectedBalance) ? bucket : low), list[0]);
    const negative = Number(lowest.projectedBalance) < 0;

    const groups = list.map((bucket) => ({
        label: monthLabel(bucket),
        title: monthTitle(bucket),
        values: [Number(bucket.receivable) || 0, Number(bucket.payable) || 0]
    }));
    const empty = groups.every((group) => !group.values[0] && !group.values[1]);

    const balancePoints = [
        { label: 'hoje', value: forecast.currentBalance, title: 'Hoje', rows: [{ text: `Saldo: ${fmtMoney(forecast.currentBalance)}` }] },
        ...list.map((bucket) => ({
            label: monthLabel(bucket),
            value: bucket.projectedBalance,
            title: `Fim de ${monthTitle(bucket)}`,
            rows: [
                { text: `Saldo projetado: ${fmtMoney(bucket.projectedBalance)}` },
                { color: INCOME.color, text: `A receber: ${fmtMoney(bucket.receivable)}` },
                { color: EXPENSE.color, text: `A pagar: ${fmtMoney(bucket.payable)}` }
            ]
        }))
    ];

    return el('div', { class: 'dash' }, [
        el('div', { class: 'kpis span-full' }, [
            statTile('Saldo em contas hoje', fmtMoney(forecast.currentBalance)),
            statTile('A receber no período', fmtMoney(totalReceivable), 'pos'),
            statTile('A pagar no período', fmtMoney(totalPayable), 'neg'),
            statTile(
                `Saldo em ${monthName(last.month)}`,
                fmtMoney(last.projectedBalance),
                '',
                negative ? `Fica negativo em ${monthName(lowest.month)}` : null,
                'critical'
            )
        ]),
        card('Contas por mês', el('span', { class: 'sub', text: `próximos ${months} meses` }), empty
            ? emptyState('Nenhum título em aberto nesses meses.')
            : [
                overdueNote(forecast.overdue),
                legend([INCOME, EXPENSE]),
                groupedColumns({ groups, series: [INCOME, EXPENSE], height: 260, ariaLabel: 'Contas a receber e a pagar por mês' })
            ].filter(Boolean), 'span-main'),
        card('Saldo projetado', el('span', { class: 'sub', text: 'fim de cada mês' }), lineChart({
            points: balancePoints,
            height: 260,
            ariaLabel: `Saldo projetado para os próximos ${months} meses`
        }), 'span-side'),
        card('Mês a mês', el('span', { class: 'sub', text: 'inclui recorrências ainda não lançadas' }), monthsTable(forecast), 'span-full')
    ]);
}

export async function renderForecast() {
    const view = el('div');

    function head() {
        const picker = el('div', { class: 'segmented', role: 'group', 'aria-label': 'Período da previsão' },
            PERIODS.map((count) => el('button', {
                type: 'button',
                'aria-pressed': count === months ? 'true' : 'false',
                text: `${count} meses`,
                onClick: () => {
                    if (count === months) return;
                    months = count;
                    load();
                }
            })));
        return pageHead('Previsão mensal', 'O que entra e sai nos próximos meses, com o saldo projetado', [picker]);
    }

    async function load() {
        view.replaceChildren(head(), el('div', { class: 'skeleton' }, [el('i'), el('i'), el('i', { class: 'tall' })]));
        try {
            const forecast = await api.reports.monthlyForecast(months);
            view.replaceChildren(head(), forecastView(forecast));
        } catch (error) {
            reportError(error);
            view.replaceChildren(head(), emptyState(error?.message || 'Não foi possível carregar a previsão.'));
        }
    }

    await load();
    return view;
}
