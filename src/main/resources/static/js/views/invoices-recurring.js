import { api } from '../api.js';
import { el, fmtMoney, fmtDate, badge, toast, reportError, formDialog, confirmDialog, emptyState, todayIso } from '../ui.js';

// Assinatura mensal no cartão (spec 016, regras 31 a 38). As funções puras espelham o backend
// (RecurringPurchaseSchedule): uma cobrança por mês, dia limitado ao fim do mês, e o que já foi gerado
// se mede em meses, a partir de generatedUntil.

const pad = (value) => String(value).padStart(2, '0');

function clampedIso(year, monthIndex, day) {
    const last = new Date(Date.UTC(year, monthIndex + 1, 0)).getUTCDate();
    const target = new Date(Date.UTC(year, monthIndex, Math.min(day, last)));
    return `${target.getUTCFullYear()}-${pad(target.getUTCMonth() + 1)}-${pad(target.getUTCDate())}`;
}

function monthIndexOf(iso) {
    const [year, month] = iso.split('-').map(Number);
    return year * 12 + (month - 1);
}

/** Cobranças ainda não geradas, do início (ou do mês seguinte à última gerada) até a data limite, inclusive. */
export function chargeDates({ dayOfMonth, startDate, endDate = null, generatedUntil = null }, upTo) {
    const limit = endDate && endDate < upTo ? endDate : upTo;
    let month = monthIndexOf(startDate);
    if (generatedUntil) month = Math.max(month, monthIndexOf(generatedUntil) + 1);

    const dates = [];
    for (let guard = 0; guard < 1200; guard++, month++) {
        const date = clampedIso(Math.floor(month / 12), month % 12, Number(dayOfMonth));
        if (date > limit) break;
        if (date >= startDate) dates.push(date);
    }
    return dates;
}

/** Cobranças previstas de um ciclo da fatura: compras ativas, ainda não geradas e a partir de invoicesFrom. */
export function projectedCharges(purchases, invoice, invoicesFrom) {
    const charges = [];
    purchases.filter((purchase) => purchase.active).forEach((purchase) => {
        chargeDates(purchase, invoice.periodEnd)
            .filter((date) => date >= invoice.periodStart && (!invoicesFrom || date >= invoicesFrom))
            .forEach((date) => charges.push({
                date,
                description: purchase.description || purchase.categoryName || 'Assinatura mensal',
                amount: purchase.amount,
                purchaseId: purchase.id
            }));
    });
    return charges.sort((a, b) => a.date.localeCompare(b.date));
}

export function recurringPurchasePayload(values, cardId) {
    return {
        accountId: cardId,
        categoryId: Number(values.categoryId),
        description: values.description,
        amount: values.amount,
        dayOfMonth: Number(values.dayOfMonth),
        startDate: values.startDate,
        endDate: values.endDate || null
    };
}

function addMonthsIso(iso, months) {
    const index = monthIndexOf(iso) + months;
    return clampedIso(Math.floor(index / 12), index % 12, Number(iso.slice(8, 10)));
}

/** Prévia do diálogo: o que é lançado agora (início no passado) e as próximas cobranças. */
export function previewText({ dayOfMonth, startDate, endDate }, today) {
    const day = Number(dayOfMonth);
    if (!Number.isInteger(day) || day < 1 || day > 31 || !startDate) {
        return 'Escolha o dia do mês e o início para ver as cobranças. Em mês mais curto, o dia 31 cobra no último dia.';
    }
    const dates = chargeDates({ dayOfMonth: day, startDate, endDate: endDate || null }, addMonthsIso(today, 4));
    const past = dates.filter((date) => date <= today);
    const next = dates.filter((date) => date > today).slice(0, 3);
    if (!past.length && !next.length) return 'Nenhuma cobrança entre o início e o fim escolhidos.';

    const parts = [];
    if (past.length) {
        const plural = past.length > 1;
        parts.push(`${past.length} ${plural ? 'cobranças já vencidas serão lançadas' : 'cobrança já vencida será lançada'} agora.`);
    }
    if (next.length) parts.push(`Próximas cobranças: ${next.map(fmtDate).join(', ')}${next.length === 3 ? '…' : ''}`);
    return parts.join(' ');
}

export function openRecurringPurchaseDialog({ card, purchase, categories, onSaved }) {
    const values = purchase || {};
    formDialog({
        title: purchase ? 'Editar assinatura mensal' : `Assinatura mensal · ${card.name}`,
        fields: [
            {
                name: 'categoryId', label: 'Categoria', type: 'select', required: true,
                options: categories.map((category) => ({ value: category.id, label: category.name }))
            },
            { name: 'description', label: 'Descrição', type: 'text', placeholder: 'Ex.: Streaming de vídeo' },
            { name: 'amount', label: 'Valor de cada cobrança', type: 'number', step: '0.01', min: '0.01', required: true },
            {
                name: 'dayOfMonth', label: 'Cobra todo dia', type: 'number', step: '1', min: 1, max: 31, required: true,
                hint: 'Em mês mais curto vale o último dia.'
            },
            {
                name: 'startDate', label: 'Começa em', type: 'date', required: true,
                hint: 'No passado, as cobranças desde essa data são lançadas agora.'
            },
            { name: 'endDate', label: 'Termina em (opcional)', type: 'date' }
        ],
        values,
        preview: (current) => previewText(current, todayIso()),
        submitLabel: purchase ? 'Salvar' : 'Criar assinatura mensal',
        onSubmit: async (submitted) => {
            const payload = recurringPurchasePayload(submitted, purchase ? purchase.accountId : card.id);
            if (purchase) await api.recurringPurchases.update(purchase.id, payload);
            else await api.recurringPurchases.create(payload);
            // Gera já as cobranças vencidas, para a fatura mostrar o que é real e o que é previsto.
            await api.recurringPurchases.materialize();
            toast(purchase ? 'Assinatura mensal atualizada.' : 'Assinatura mensal criada.', 'success');
            onSaved();
        }
    });
}

function periodText(purchase) {
    return purchase.endDate
        ? `de ${fmtDate(purchase.startDate)} a ${fmtDate(purchase.endDate)}`
        : `desde ${fmtDate(purchase.startDate)}`;
}

/** Lista das assinaturas mensais de todos os cartões, com editar, desativar e apagar. */
export function recurringPurchasesSection({ purchases, loadCategories, cards, onChanged }) {
    const titleId = 'recurring-purchases-title';

    async function edit(purchase) {
        const categories = await loadCategories();
        if (!categories) return;
        const card = cards.find((item) => item.id === purchase.accountId);
        openRecurringPurchaseDialog({ card, purchase, categories, onSaved: onChanged });
    }

    async function deactivate(purchase) {
        try {
            await api.recurringPurchases.deactivate(purchase.id);
            toast('Assinatura mensal desativada.', 'success');
            onChanged();
        } catch (error) {
            reportError(error);
        }
    }

    function remove(purchase) {
        confirmDialog({
            title: 'Apagar assinatura mensal',
            message: `"${purchase.description || purchase.categoryName}" deixa de cobrar. As cobranças já lançadas continuam nas faturas.`,
            confirmLabel: 'Apagar',
            onConfirm: async () => {
                await api.recurringPurchases.remove(purchase.id);
                toast('Assinatura mensal apagada.', 'success');
                onChanged();
            }
        });
    }

    function item(purchase) {
        const name = purchase.description || purchase.categoryName;
        return el('li', { class: 'recurring-item' }, [
            el('div', { class: 'recurring-info' }, [
                el('span', { class: 'desc-main', text: name }),
                el('span', { class: 'meter-foot', text: `${purchase.accountName} · todo dia ${purchase.dayOfMonth} · ${periodText(purchase)}` })
            ]),
            el('span', { class: 'recurring-amount', text: fmtMoney(purchase.amount) }),
            purchase.active ? badge('open', 'Ativa') : badge('canceled', 'Desativada'),
            el('div', { class: 'recurring-actions' }, [
                el('button', { type: 'button', class: 'btn-sm', text: 'Editar', 'aria-label': `Editar ${name}`, onClick: () => edit(purchase) }),
                purchase.active
                    ? el('button', { type: 'button', class: 'btn-sm', text: 'Desativar', 'aria-label': `Desativar ${name}`, onClick: () => deactivate(purchase) })
                    : null,
                el('button', { type: 'button', class: 'btn-sm', text: 'Apagar', 'aria-label': `Apagar ${name}`, onClick: () => remove(purchase) })
            ])
        ]);
    }

    return el('section', { class: 'card recurring-purchases', 'aria-labelledby': titleId }, [
        el('div', { class: 'card-head' }, [el('h2', { id: titleId, text: 'Assinaturas mensais' })]),
        purchases.length
            ? el('ul', { class: 'recurring-list' }, purchases.map(item))
            : emptyState('Nenhuma assinatura mensal. Cadastre assinaturas como streaming e academia pelo botão do cartão.')
    ]);
}
