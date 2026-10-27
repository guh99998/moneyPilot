import { api, session } from './api.js';
import { el, fmtMoney, fmtDate, todayIso, svgIcon, reportError } from './ui.js';
import { billFiltersFor, destinationFor, isBudgetAlert } from './alert-targets.js';
import { presetBillFilters } from './views/bills.js';
import { presetBudgetPeriod } from './views/budgets.js';

const BELL_ICON = svgIcon('<path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/>', 20);
const COUNT_LIMIT = 99;

// A sidebar é refeita a cada tela: o estado fica aqui e o sino atual é só a vista dele.
let alerts = [];
let bellButton = null;
let panel = null;
let loading = false;
let reloadAfter = false;

const PHRASES = {
    BILLS_OVERDUE: {
        PAYABLE: ['conta a pagar vencida', 'contas a pagar vencidas'],
        RECEIVABLE: ['conta a receber atrasada', 'contas a receber atrasadas']
    },
    BILLS_DUE_SOON: {
        PAYABLE: ['conta a pagar a vencer', 'contas a pagar a vencer'],
        RECEIVABLE: ['conta a receber a vencer', 'contas a receber a vencer']
    }
};

function describe(alert) {
    if (isBudgetAlert(alert)) {
        const amounts = `${fmtMoney(alert.spent)} de ${fmtMoney(alert.budgeted)}`;
        return alert.kind === 'BUDGET_EXCEEDED'
            ? { tone: 'critical', title: `Orçamento de ${alert.categoryName} estourado`, detail: `${amounts} (${alert.percentUsed}%)` }
            : { tone: 'warning', title: `${alert.categoryName}: ${alert.percentUsed}% do orçamento`, detail: amounts };
    }

    const [one, many] = PHRASES[alert.kind][alert.billType];
    const title = `${alert.count} ${alert.count === 1 ? one : many}`;
    if (alert.kind === 'BILLS_OVERDUE') {
        return { tone: 'critical', title, detail: fmtMoney(alert.total) };
    }
    return { tone: 'warning', title, detail: `${fmtMoney(alert.total)} · até ${fmtDate(alert.dueUntil)}` };
}

function paintBell() {
    if (!bellButton) return;
    const count = alerts.length;
    const badge = bellButton.querySelector('.bell-count');
    badge.hidden = count === 0;
    badge.textContent = count > COUNT_LIMIT ? `${COUNT_LIMIT}+` : String(count);
    bellButton.setAttribute('aria-label', count ? `Alertas: ${count}` : 'Alertas');
}

export function renderBell() {
    closePanel();
    bellButton = el('button', {
        class: 'btn-ghost bell',
        type: 'button',
        'aria-haspopup': 'dialog',
        'aria-expanded': 'false',
        onClick: () => (panel ? closePanel() : openPanel())
    }, [
        el('span', { class: 'bell-icon', 'aria-hidden': 'true', html: BELL_ICON }),
        el('span', { class: 'bell-count', 'aria-hidden': 'true' })
    ]);
    paintBell();
    return bellButton;
}

export function resetAlerts() {
    alerts = [];
    closePanel();
    paintBell();
}

/** Recarrega os alertas. O sino é um auxiliar: se falhar, fica o último estado conhecido. */
export async function refreshAlerts() {
    if (!session.active) return;
    if (loading) {
        reloadAfter = true;
        return;
    }
    loading = true;
    try {
        const data = await api.alerts.list();
        alerts = data?.alerts || [];
        paintBell();
        if (panel) drawPanel();
    } catch {
        // mantém o que já estava na tela
    } finally {
        loading = false;
        if (reloadAfter) {
            reloadAfter = false;
            refreshAlerts();
        }
    }
}

function openPanel() {
    panel = el('div', { class: 'alerts-panel', role: 'dialog', 'aria-label': 'Alertas' });
    document.body.append(panel);
    bellButton.setAttribute('aria-expanded', 'true');
    drawPanel();
    positionPanel();
}

function closePanel(focusBell = false) {
    if (!panel) return;
    panel.remove();
    panel = null;
    bellButton?.setAttribute('aria-expanded', 'false');
    if (focusBell) bellButton?.focus();
}

function positionPanel() {
    if (!panel || !bellButton) return;
    const margin = 12;
    const rect = bellButton.getBoundingClientRect();
    const left = Math.min(rect.left, window.innerWidth - panel.offsetWidth - margin);
    panel.style.top = `${Math.round(rect.bottom + 8)}px`;
    panel.style.left = `${Math.round(Math.max(margin, left))}px`;
}

function drawPanel() {
    const body = alerts.length
        ? el('ul', { class: 'alerts-list' }, alerts.map(alertItem))
        : el('p', { class: 'alerts-empty', text: 'Tudo em dia. Nada vencendo, vencido ou estourado.' });
    panel.replaceChildren(el('h2', { class: 'alerts-title', text: 'Alertas' }), body);
}

function alertItem(alert) {
    const { tone, title, detail } = describe(alert);
    return el('li', { class: `alert-item ${tone}` }, [
        el('button', { class: 'alert-main', type: 'button', onClick: () => openAlert(alert) }, [
            el('strong', { text: title }),
            el('span', { text: detail })
        ]),
        el('button', {
            class: 'btn-ghost btn-sm alert-dismiss',
            type: 'button',
            text: 'Dispensar',
            'aria-label': `Dispensar: ${title}`,
            onClick: () => dismiss(alert)
        })
    ]);
}

async function dismiss(alert) {
    try {
        await api.alerts.dismiss(alert.key);
    } catch (error) {
        reportError(error);
        refreshAlerts(); // o alerta pode ter acabado de sumir
    }
}

function openAlert(alert) {
    if (isBudgetAlert(alert)) presetBudgetPeriod(alert.month, alert.year);
    else presetBillFilters(billFiltersFor(alert, todayIso()));

    closePanel();
    const destination = destinationFor(alert);
    // O router só reage a mudança de hash: na própria tela, avisa à mão para refazer com os filtros.
    if (window.location.hash === destination) window.dispatchEvent(new HashChangeEvent('hashchange'));
    else window.location.hash = destination;
}

window.addEventListener('api:changed', refreshAlerts);
window.addEventListener('resize', positionPanel);
document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') refreshAlerts();
});
document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') closePanel(true);
});
document.addEventListener('click', (event) => {
    if (panel && !panel.contains(event.target) && !bellButton?.contains(event.target)) closePanel();
});
