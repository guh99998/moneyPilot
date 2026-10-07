import { api, fetchAll, normalizePage, session } from '../api.js';
import {
    el, clear, fmtMoney, fmtDate, badge, toast, reportError,
    pageHead, emptyState, pager, collapsibleFilters, sortHeader, sortSelect, confirmDialog, todayIso, stackable
} from '../ui.js';

const EMPTY_FILTERS = { type: '', status: 'OPEN', categoryId: '', accountId: '', dueDateFrom: '', dueDateTo: '' };
const filters = { ...EMPTY_FILTERS };
let page = 0;
let sort = { key: 'dueDate', dir: 'asc' };

const SORT_OPTIONS = [
    { key: 'dueDate', label: 'Vencimento', asc: 'mais antigo primeiro', desc: 'mais distante primeiro' },
    { key: 'description', label: 'Descrição', asc: 'A → Z', desc: 'Z → A' },
    { key: 'amount', label: 'Valor', asc: 'menor primeiro', desc: 'maior primeiro' }
];

// Seleção sobrevive à troca de página: guarda o título inteiro para somar sem refazer a busca.
const selected = new Map();

const TYPE_LABEL = { PAYABLE: 'A pagar', RECEIVABLE: 'A receber' };
const STATUS_LABEL = { OPEN: 'Em aberto', OVERDUE: 'Vencido', SETTLED: 'Baixado', CANCELED: 'Cancelado' };
const CATEGORY_TYPE_FOR = { PAYABLE: 'EXPENSE', RECEIVABLE: 'INCOME' };
const BULK_LIMIT = 200;

/** Usado pelo dashboard: abre a tela já filtrada (o router não tem parâmetros). */
export function presetBillFilters(preset) {
    Object.assign(filters, EMPTY_FILTERS, preset);
    page = 0;
    selected.clear();
}

function isOverdue(bill) {
    return bill.status === 'OPEN' && bill.dueDate < todayIso();
}

export function billStatusBadge(bill) {
    if (isOverdue(bill)) return badge('overdue', 'Vencido');
    if (bill.status === 'SETTLED') return badge('settled', 'Baixado');
    if (bill.status === 'CANCELED') return badge('canceled', 'Cancelado');
    return badge('open', 'Em aberto');
}

// ---- dinheiro em centavos: a prévia de parcelas não pode errar por ponto flutuante ----

function toCents(value) {
    const [whole, fraction = ''] = String(value).trim().replace(',', '.').split('.');
    const sign = whole.startsWith('-') ? -1 : 1;
    return sign * (Math.abs(Number(whole || 0)) * 100 + Number((fraction + '00').slice(0, 2)));
}

function centsToDecimal(cents) {
    return (cents / 100).toFixed(2);
}

function daysInMonth(year, monthIndex) {
    return new Date(Date.UTC(year, monthIndex + 1, 0)).getUTCDate();
}

/** Mesmo cálculo do backend: sempre a partir da data original, com o dia limitado ao fim do mês. */
function installmentDueDate(firstIso, offset) {
    const [year, month, day] = firstIso.split('-').map(Number);
    const target = new Date(Date.UTC(year, month - 1 + offset, 1));
    const targetYear = target.getUTCFullYear();
    const targetMonth = target.getUTCMonth();
    const clampedDay = Math.min(day, daysInMonth(targetYear, targetMonth));
    return `${targetYear}-${String(targetMonth + 1).padStart(2, '0')}-${String(clampedDay).padStart(2, '0')}`;
}

function nextDayIso(iso) {
    const [year, month, day] = iso.split('-').map(Number);
    return new Date(Date.UTC(year, month - 1, day + 1)).toISOString().slice(0, 10);
}

/** Próximos vencimentos de uma recorrência — mesmo clamp do dueDateFor do backend (dia 31 vira o último dia do mês). */
function recurrenceDueDates({ dayOfMonth, startDate, endDate }, count) {
    const dates = [];
    const startMonth = `${startDate.slice(0, 7)}-01`;
    for (let offset = 0; dates.length < count && offset < count + 1; offset++) {
        const monthStart = installmentDueDate(startMonth, offset);
        const due = installmentDueDate(`${monthStart.slice(0, 8)}${String(dayOfMonth).padStart(2, '0')}`, 0);
        if (due < startDate) continue;
        if (endDate && due > endDate) break;
        dates.push(due);
    }
    return dates;
}

// Recorrência só vira título quando um mês é pedido ao backend. O app pede o mês atual e o próximo
// uma vez por sessão; a chamada é idempotente (o backend pula o que já existe), mas sequencial:
// duas em paralelo para o mesmo mês esbarrariam no UNIQUE(recurrence_id, due_date).
let materializedKey = null;

export async function ensureRecurringBills({ force = false } = {}) {
    const currentMonth = todayIso().slice(0, 7);
    const key = `${session.email}|${currentMonth}`;
    if (!force && materializedKey === key) return;
    const nextMonth = installmentDueDate(`${currentMonth}-01`, 1).slice(0, 7);
    await api.bills.materialize(currentMonth);
    await api.bills.materialize(nextMonth);
    materializedKey = key;
}

/** Divisão DOWN com o resíduo em centavos na primeira parcela — igual ao createInstallmentPlan. */
function splitInstallments(totalCents, count) {
    const base = Math.floor(totalCents / count);
    const residue = totalCents - base * count;
    return Array.from({ length: count }, (_, index) => (index === 0 ? base + residue : base));
}

// ---- diálogos ----

function field(label, input, hint) {
    return el('div', { class: 'field' }, [
        el('label', { text: label }),
        input,
        hint || null
    ]);
}

function accountOptions(accounts, selectedId, { allowNone } = {}) {
    return [
        allowNone ? el('option', { value: '', text: 'Sem conta definida', selected: !selectedId }) : null,
        ...accounts.map((account) => el('option', {
            value: account.id,
            text: account.name,
            selected: String(account.id) === String(selectedId ?? '')
        }))
    ];
}

/** Monta o esqueleto comum dos diálogos: título, caixa de erro, corpo e ações. */
function openDialog({ title, body, submitLabel = 'Salvar', onSubmit, focus }) {
    const dialog = el('dialog');
    const errorBox = el('p', { class: 'dialog-error', hidden: true });
    const submitBtn = el('button', { class: 'btn-primary', type: 'submit', text: submitLabel });

    const form = el('form', {
        class: 'dialog-body',
        onSubmit: async (event) => {
            event.preventDefault();
            submitBtn.disabled = true;
            errorBox.hidden = true;
            try {
                await onSubmit();
                dialog.close();
            } catch (error) {
                errorBox.textContent = error?.message || 'Não foi possível salvar.';
                errorBox.hidden = false;
                submitBtn.disabled = false;
            }
        }
    }, [
        el('h2', { text: title }),
        errorBox,
        ...body,
        el('div', { class: 'dialog-actions' }, [
            el('button', { type: 'button', text: 'Cancelar', onClick: () => dialog.close() }),
            submitBtn
        ])
    ]);

    dialog.append(form);
    dialog.addEventListener('close', () => dialog.remove());
    document.body.append(dialog);
    dialog.showModal();
    focus?.focus();
    return { dialog, submitBtn };
}

/** Tipo e categoria andam juntos: a API recusa título a pagar com categoria de receita. */
function typeAndCategory({ type, categoryId, categories }) {
    const typeSelect = el('select', { required: true }, Object.entries(TYPE_LABEL).map(([value, label]) => el('option', {
        value, text: label, selected: value === (type || 'PAYABLE')
    })));
    const categorySelect = el('select', { required: true });

    function fill() {
        const wanted = CATEGORY_TYPE_FOR[typeSelect.value];
        const options = categories.filter((category) => category.type === wanted);
        clear(categorySelect);
        if (!options.length) {
            categorySelect.append(el('option', { value: '', text: 'Nenhuma categoria deste tipo' }));
            return;
        }
        options.forEach((category) => categorySelect.append(el('option', {
            value: category.id,
            text: category.name,
            selected: String(category.id) === String(categoryId ?? '')
        })));
    }

    typeSelect.addEventListener('change', fill);
    fill();
    return { typeSelect, categorySelect };
}

function billDialog({ bill, accounts, categories, onSaved }) {
    const values = bill || {};
    const { typeSelect, categorySelect } = typeAndCategory({ type: values.type, categoryId: values.categoryId, categories });
    const amountInput = el('input', { type: 'number', step: '0.01', min: '0.01', required: true, value: values.amount ?? '' });
    const dueInput = el('input', { type: 'date', required: true, value: values.dueDate ?? todayIso() });
    const accountSelect = el('select', {}, accountOptions(accounts, values.accountId, { allowNone: true }));
    const descriptionInput = el('input', { type: 'text', maxlength: '255', value: values.description ?? '' });

    openDialog({
        title: bill ? 'Editar título' : 'Novo título',
        focus: descriptionInput,
        body: [
            field('Descrição', descriptionInput),
            el('div', { class: 'form-row' }, [field('Tipo', typeSelect), field('Valor', amountInput)]),
            field('Categoria', categorySelect),
            el('div', { class: 'form-row' }, [field('Vencimento', dueInput), field('Conta prevista', accountSelect)])
        ],
        onSubmit: async () => {
            const payload = {
                type: typeSelect.value,
                categoryId: Number(categorySelect.value),
                amount: amountInput.value,
                dueDate: dueInput.value,
                accountId: accountSelect.value ? Number(accountSelect.value) : null,
                description: descriptionInput.value.trim() || null
            };
            if (bill) await api.bills.update(bill.id, payload);
            else await api.bills.create(payload);
            toast(bill ? 'Título atualizado.' : 'Título criado.', 'success');
            onSaved();
        }
    });
}

function installmentPlanDialog({ accounts, categories, onSaved }) {
    const { typeSelect, categorySelect } = typeAndCategory({ categories });
    const totalInput = el('input', { type: 'number', step: '0.01', min: '0.01', required: true });
    const countInput = el('input', { type: 'number', step: '1', min: '2', max: '360', required: true, value: '2' });
    const firstDueInput = el('input', { type: 'date', required: true, value: todayIso() });
    const accountSelect = el('select', {}, accountOptions(accounts, null, { allowNone: true }));
    const descriptionInput = el('input', { type: 'text', maxlength: '255' });
    const preview = el('div', { class: 'installment-preview', 'aria-live': 'polite' });

    let valid = false;

    function renderPreview() {
        const count = Number(countInput.value);
        const totalCents = totalInput.value ? toCents(totalInput.value) : 0;
        const firstDue = firstDueInput.value;
        valid = false;

        if (!totalCents || !Number.isInteger(count) || count < 2 || count > 360 || !firstDue) {
            preview.replaceChildren(el('p', { class: 'preview-hint', text: 'Preencha valor total, número de parcelas e primeiro vencimento para ver a divisão.' }));
            submit.disabled = false;
            return;
        }

        if (totalCents < count) {
            preview.replaceChildren(el('p', {
                class: 'preview-error',
                text: `Com ${count} parcelas o total precisa ser pelo menos ${fmtMoney(centsToDecimal(count))} — cada parcela tem no mínimo R$ 0,01.`
            }));
            submit.disabled = true;
            return;
        }

        valid = true;
        submit.disabled = false;
        const parts = splitInstallments(totalCents, count);
        const rows = parts.map((cents, index) => ({ number: index + 1, cents, due: installmentDueDate(firstDue, index) }));
        // Lista longa: mostra o começo e o fim, que é onde moram o resíduo e o clamp de data.
        const shown = rows.length <= 6 ? rows : [...rows.slice(0, 3), null, ...rows.slice(-2)];
        const sum = parts.reduce((acc, cents) => acc + cents, 0);

        preview.replaceChildren(
            el('table', { class: 'preview-table' }, [
                el('thead', {}, el('tr', {}, [
                    el('th', { text: 'Parcela' }),
                    el('th', { text: 'Vencimento' }),
                    el('th', { class: 'num', text: 'Valor' })
                ])),
                el('tbody', {}, shown.map((row) => (row
                    ? el('tr', {}, [
                        el('td', { text: `${row.number}/${count}` }),
                        el('td', { text: fmtDate(row.due) }),
                        el('td', { class: 'num', text: fmtMoney(centsToDecimal(row.cents)) })
                    ])
                    : el('tr', { class: 'gap' }, [el('td', { colspan: '3', text: `… mais ${rows.length - 5} parcelas iguais` })])
                )))
            ]),
            el('p', {
                class: 'preview-hint',
                text: parts[0] !== parts[1]
                    ? `A primeira parcela leva ${fmtMoney(centsToDecimal(parts[0] - parts[1]))} a mais para a soma fechar em ${fmtMoney(centsToDecimal(sum))}.`
                    : `Soma das parcelas: ${fmtMoney(centsToDecimal(sum))}.`
            })
        );
    }

    [totalInput, countInput, firstDueInput].forEach((input) => input.addEventListener('input', renderPreview));

    const { submitBtn: submit } = openDialog({
        title: 'Novo parcelamento',
        submitLabel: 'Criar parcelas',
        focus: descriptionInput,
        body: [
            field('Descrição', descriptionInput),
            el('div', { class: 'form-row' }, [field('Tipo', typeSelect), field('Categoria', categorySelect)]),
            el('div', { class: 'form-row' }, [field('Valor total', totalInput), field('Parcelas', countInput)]),
            el('div', { class: 'form-row' }, [field('Primeiro vencimento', firstDueInput), field('Conta prevista', accountSelect)]),
            preview
        ],
        onSubmit: async () => {
            if (!valid) throw new Error('Confira o valor total e o número de parcelas.');
            const created = await api.bills.createInstallments({
                type: typeSelect.value,
                categoryId: Number(categorySelect.value),
                totalAmount: totalInput.value,
                installments: Number(countInput.value),
                firstDueDate: firstDueInput.value,
                accountId: accountSelect.value ? Number(accountSelect.value) : null,
                description: descriptionInput.value.trim() || null
            });
            toast(`${created.length} parcelas criadas.`, 'success');
            onSaved();
        }
    });

    renderPreview();
}

function settleDialog({ bill, accounts, onSaved }) {
    const accountSelect = el('select', { required: true }, accountOptions(accounts, bill.accountId ?? accounts[0]?.id));
    const amountInput = el('input', { type: 'number', step: '0.01', min: '0.01', required: true, value: bill.amount });
    const dateInput = el('input', { type: 'date', required: true, max: todayIso(), value: todayIso() });
    const warning = el('p', { class: 'preview-hint', hidden: true });

    amountInput.addEventListener('input', () => {
        const paid = amountInput.value ? toCents(amountInput.value) : 0;
        const due = toCents(bill.amount);
        warning.hidden = !paid || paid === due;
        // Armadilha conhecida: baixar por menos quita o título inteiro, sem gerar resíduo.
        // Por mais (juros, multa) o lançamento registra o que saiu de fato.
        warning.textContent = paid < due
            ? `O título fica quitado por inteiro: a diferença de ${fmtMoney(centsToDecimal(due - paid))} não vira um novo título.`
            : `${fmtMoney(centsToDecimal(paid - due))} acima do valor do título (juros ou multa). O lançamento registra o valor pago.`;
    });

    const verb = bill.type === 'PAYABLE' ? 'Pagar' : 'Receber';
    openDialog({
        title: `${verb}: ${bill.description || bill.categoryName}`,
        submitLabel: `Confirmar ${verb.toLowerCase()}`,
        focus: amountInput,
        body: [
            el('p', { class: 'dialog-lead', text: `Vencimento ${fmtDate(bill.dueDate)} · valor ${fmtMoney(bill.amount)}` }),
            field(bill.type === 'PAYABLE' ? 'Sai da conta' : 'Entra na conta', accountSelect),
            el('div', { class: 'form-row' }, [
                field(bill.type === 'PAYABLE' ? 'Pago em' : 'Recebido em', dateInput),
                field('Valor efetivo', amountInput)
            ]),
            warning
        ],
        onSubmit: async () => {
            await api.bills.settle(bill.id, { accountId: Number(accountSelect.value), amount: amountInput.value, settledOn: dateInput.value });
            toast('Baixa registrada — o lançamento já está no extrato.', 'success');
            onSaved();
        }
    });
}

function bulkSettleDialog({ bills, accounts, onSaved }) {
    const accountSelect = el('select', { required: true }, accountOptions(accounts, accounts[0]?.id));
    const dateInput = el('input', { type: 'date', required: true, max: todayIso(), value: todayIso() });
    const payable = bills.filter((bill) => bill.type === 'PAYABLE').reduce((acc, bill) => acc + toCents(bill.amount), 0);
    const receivable = bills.filter((bill) => bill.type === 'RECEIVABLE').reduce((acc, bill) => acc + toCents(bill.amount), 0);
    const lines = [];
    if (payable) lines.push(`${fmtMoney(centsToDecimal(payable))} a pagar`);
    if (receivable) lines.push(`${fmtMoney(centsToDecimal(receivable))} a receber`);

    openDialog({
        title: `Baixar ${bills.length} ${bills.length === 1 ? 'título' : 'títulos'}`,
        submitLabel: 'Baixar todos',
        focus: accountSelect,
        body: [
            el('p', { class: 'dialog-lead', text: `${lines.join(' e ')}, cada um pelo valor integral.` }),
            el('div', { class: 'form-row' }, [field('Conta', accountSelect), field('Data da baixa', dateInput)]),
            el('p', { class: 'preview-hint', text: 'É tudo ou nada: se um título falhar, nenhum é baixado e o erro diz qual foi.' })
        ],
        onSubmit: async () => {
            await api.bills.bulkSettle({ billIds: bills.map((bill) => bill.id), accountId: Number(accountSelect.value), settledOn: dateInput.value });
            toast(`${bills.length} ${bills.length === 1 ? 'título baixado' : 'títulos baixados'}.`, 'success');
            selected.clear();
            onSaved();
        }
    });
}

/** Como o confirmDialog, mas com mais de um caminho: cada escolha é um botão com sua própria ação. */
function choiceDialog({ title, message, choices }) {
    const dialog = el('dialog');
    const buttons = choices.map((choice) => el('button', {
        type: 'button',
        class: choice.class || '',
        text: choice.label,
        onClick: async () => {
            buttons.forEach((button) => { button.disabled = true; });
            try {
                await choice.run();
            } catch (error) {
                reportError(error);
            }
            dialog.close();
        }
    }));

    dialog.append(el('div', { class: 'dialog-body' }, [
        el('h2', { text: title }),
        el('p', { class: 'dialog-lead', text: message }),
        el('div', { class: 'dialog-actions dialog-actions-wrap' }, [
            el('button', { type: 'button', text: 'Voltar', onClick: () => dialog.close() }),
            ...buttons
        ])
    ]));

    dialog.addEventListener('close', () => dialog.remove());
    document.body.append(dialog);
    dialog.showModal();
}

function recurrenceDialog({ recurrence, accounts, categories, onSaved }) {
    const values = recurrence || {};
    const { typeSelect, categorySelect } = typeAndCategory({ type: values.type, categoryId: values.categoryId, categories });
    const descriptionInput = el('input', { type: 'text', maxlength: '255', required: true, placeholder: 'Ex.: Conta de telefone', value: values.description ?? '' });
    const amountInput = el('input', { type: 'number', step: '0.01', min: '0.01', required: true, value: values.amount ?? '' });
    const dayInput = el('input', { type: 'number', step: '1', min: '1', max: '31', required: true, value: values.dayOfMonth ?? '' });
    const accountSelect = el('select', {}, accountOptions(accounts, values.accountId, { allowNone: true }));
    const startInput = el('input', { type: 'date', required: true, value: values.startDate ?? todayIso() });
    const endInput = el('input', { type: 'date', value: values.endDate ?? '' });
    const preview = el('p', { class: 'preview-hint', 'aria-live': 'polite' });

    function renderPreview() {
        const day = Number(dayInput.value);
        // O backend exige fim estritamente depois do início.
        endInput.min = startInput.value ? nextDayIso(startInput.value) : '';
        if (!Number.isInteger(day) || day < 1 || day > 31 || !startInput.value) {
            preview.textContent = 'Todo mês, no dia escolhido. Em meses mais curtos, o dia 31 vence no último dia do mês.';
            return;
        }
        const dates = recurrenceDueDates({ dayOfMonth: day, startDate: startInput.value, endDate: endInput.value || null }, 3);
        preview.textContent = dates.length
            ? `Próximos vencimentos: ${dates.map(fmtDate).join(', ')}${dates.length === 3 ? '…' : ''}`
            : 'Nenhum vencimento entre o início e o fim escolhidos.';
    }

    [dayInput, startInput, endInput].forEach((input) => input.addEventListener('input', renderPreview));

    openDialog({
        title: recurrence ? 'Editar recorrência' : 'Nova recorrência',
        submitLabel: recurrence ? 'Salvar' : 'Criar recorrência',
        focus: descriptionInput,
        body: [
            field('Descrição', descriptionInput),
            el('div', { class: 'form-row' }, [field('Tipo', typeSelect), field('Valor', amountInput)]),
            el('div', { class: 'form-row' }, [field('Categoria', categorySelect), field('Vence todo dia', dayInput)]),
            el('div', { class: 'form-row' }, [field('Começa em', startInput), field('Termina em (opcional)', endInput)]),
            field('Conta prevista', accountSelect),
            preview,
            el('p', {
                class: 'preview-hint',
                text: recurrence
                    ? 'Mudanças valem para os próximos títulos gerados. Os que já existem não mudam: edite-os direto na lista.'
                    : 'Os títulos são gerados para o mês atual e o próximo. Meses anteriores ao atual não são criados.'
            })
        ],
        onSubmit: async () => {
            const payload = {
                type: typeSelect.value,
                categoryId: Number(categorySelect.value),
                description: descriptionInput.value.trim(),
                amount: amountInput.value,
                dayOfMonth: Number(dayInput.value),
                startDate: startInput.value,
                endDate: endInput.value || null,
                accountId: accountSelect.value ? Number(accountSelect.value) : null
            };
            if (recurrence) await api.billRecurrences.update(recurrence.id, payload);
            else await api.billRecurrences.create(payload);
            await ensureRecurringBills({ force: true });
            toast(recurrence ? 'Recorrência atualizada.' : 'Recorrência criada — os títulos já aparecem na lista.', 'success');
            onSaved();
        }
    });

    renderPreview();
}

function recurrencesDialog({ accounts, categories, onChanged }) {
    const dialog = el('dialog', { class: 'dialog-wide' });
    const list = el('div', { class: 'recurrence-list' }, el('p', { class: 'preview-hint', text: 'Carregando…' }));

    async function refresh() {
        try {
            const items = await fetchAll(api.billRecurrences.list);
            // Ativas primeiro; dentro de cada grupo, pela ordem do dia de vencimento.
            items.sort((a, b) => (b.active - a.active) || (a.dayOfMonth - b.dayOfMonth));
            list.replaceChildren(...(items.length ? items.map(item) : [emptyState('Nenhuma recorrência. Cadastre contas fixas como aluguel, telefone ou salário.')]));
        } catch (error) {
            list.replaceChildren(emptyState(error?.message || 'Não foi possível carregar as recorrências.'));
        }
    }

    function changed() {
        refresh();
        onChanged();
    }

    function item(recurrence) {
        const sign = recurrence.type === 'PAYABLE' ? '−' : '+';
        const period = recurrence.endDate
            ? `de ${fmtDate(recurrence.startDate)} a ${fmtDate(recurrence.endDate)}`
            : `desde ${fmtDate(recurrence.startDate)}`;

        return el('div', { class: `recurrence-item ${recurrence.active ? '' : 'is-inactive'}` }, [
            el('div', { class: 'recurrence-info' }, [
                el('span', { class: 'desc-main', text: recurrence.description || recurrence.categoryName }),
                el('span', { class: 'cell-tag', text: `todo dia ${recurrence.dayOfMonth} · ${recurrence.categoryName} · ${period}` })
            ]),
            el('span', {
                class: `num amount-${recurrence.type === 'PAYABLE' ? 'out' : 'in'}`,
                text: `${sign} ${fmtMoney(recurrence.amount)}`
            }),
            el('div', { class: 'recurrence-actions' }, recurrence.active
                ? [
                    el('button', {
                        class: 'btn-ghost btn-sm',
                        text: 'Editar',
                        onClick: () => recurrenceDialog({ recurrence, accounts, categories, onSaved: changed })
                    }),
                    el('button', { class: 'btn-ghost btn-sm', text: 'Encerrar', onClick: () => askDeactivate(recurrence) }),
                    el('button', { class: 'btn-ghost btn-sm btn-danger', text: 'Excluir', onClick: () => askRemove(recurrence) })
                ]
                : [badge('canceled', 'Encerrada')])
        ]);
    }

    function askDeactivate(recurrence) {
        confirmDialog({
            title: 'Encerrar recorrência',
            message: `"${recurrence.description || recurrence.categoryName}" deixa de gerar títulos novos. Os que já foram gerados continuam na lista; cancele os que não vão acontecer.`,
            confirmLabel: 'Encerrar',
            onConfirm: async () => {
                await api.billRecurrences.deactivate(recurrence.id);
                toast('Recorrência encerrada.', 'success');
                changed();
            }
        });
    }

    function askRemove(recurrence) {
        confirmDialog({
            title: 'Excluir recorrência',
            message: `"${recurrence.description || recurrence.categoryName}" será removida. Só dá para excluir uma recorrência que ainda não gerou títulos; se já gerou, use Encerrar.`,
            confirmLabel: 'Excluir',
            onConfirm: async () => {
                try {
                    await api.billRecurrences.remove(recurrence.id);
                } catch (error) {
                    if (error?.status === 409) throw new Error('Essa recorrência já gerou títulos. Use Encerrar para parar de gerar novos.');
                    throw error;
                }
                toast('Recorrência excluída.', 'success');
                changed();
            }
        });
    }

    dialog.append(el('div', { class: 'dialog-body' }, [
        el('h2', { text: 'Recorrências' }),
        el('p', { class: 'dialog-lead', text: 'Contas que se repetem todo mês no mesmo dia. O app gera os títulos do mês atual e do próximo sozinho.' }),
        list,
        el('div', { class: 'dialog-actions' }, [
            el('button', { type: 'button', text: 'Fechar', onClick: () => dialog.close() }),
            el('button', {
                type: 'button',
                class: 'btn-primary',
                text: 'Nova recorrência',
                onClick: () => recurrenceDialog({ accounts, categories, onSaved: changed })
            })
        ])
    ]));

    dialog.addEventListener('close', () => dialog.remove());
    document.body.append(dialog);
    dialog.showModal();
    refresh();
}

// ---- tela ----

export async function renderBills() {
    const view = el('div');
    const [accounts, categories] = await Promise.all([
        fetchAll(api.accounts.list),
        fetchAll(api.categories.list)
    ]);
    let rows = [];

    function selectField(label, key, options, allLabel) {
        const select = el('select', {
            onChange: () => { filters[key] = select.value; page = 0; load(); }
        }, [
            el('option', { value: '', text: allLabel }),
            ...options.map((option) => el('option', {
                value: option.value,
                text: option.label,
                selected: String(option.value) === String(filters[key])
            }))
        ]);
        return el('div', { class: 'field' }, [el('label', { text: label }), select]);
    }

    function dateField(label, key) {
        const input = el('input', {
            type: 'date',
            value: filters[key],
            onChange: () => { filters[key] = input.value; page = 0; load(); }
        });
        return el('div', { class: 'field' }, [el('label', { text: label }), input]);
    }

    function filterBar() {
        const active = Object.entries(filters).filter(([name, value]) => value && value !== EMPTY_FILTERS[name]).length;
        return collapsibleFilters('bills', el('div', { class: 'filters' }, [
            selectField('Tipo', 'type', Object.entries(TYPE_LABEL).map(([value, label]) => ({ value, label })), 'Todos'),
            selectField('Status', 'status', Object.entries(STATUS_LABEL).map(([value, label]) => ({ value, label })), 'Todos'),
            selectField('Categoria', 'categoryId', categories
                .filter((c) => c.type === 'EXPENSE' || c.type === 'INCOME')
                .map((c) => ({ value: c.id, label: c.name })), 'Todas'),
            selectField('Conta', 'accountId', accounts.map((a) => ({ value: a.id, label: a.name })), 'Todas'),
            dateField('Vence de', 'dueDateFrom'),
            dateField('até', 'dueDateTo'),
            sortSelect({ options: SORT_OPTIONS, sort, onSort: changeSort }),
            el('button', {
                class: 'btn-sm',
                text: 'Limpar',
                onClick: () => { presetBillFilters({}); load(); }
            })
        ]), active);
    }

    function changeSort(next) {
        sort = next;
        page = 0;
        load();
    }

    function head() {
        return pageHead('A pagar e receber', 'Compromissos com data marcada: pague ou receba e o lançamento entra no extrato', [
            el('button', {
                text: 'Recorrências',
                disabled: !categories.length,
                onClick: () => recurrencesDialog({ accounts, categories, onChanged: load })
            }),
            el('button', {
                text: 'Parcelar',
                disabled: !categories.length,
                onClick: () => installmentPlanDialog({ accounts, categories, onSaved: load })
            }),
            el('button', {
                class: 'btn-primary',
                text: 'Novo título',
                disabled: !categories.length,
                onClick: () => billDialog({ accounts, categories, onSaved: load })
            })
        ]);
    }

    function selectionBar() {
        if (!selected.size) return null;
        const bills = [...selected.values()];
        const net = bills.reduce((acc, bill) => acc + (bill.type === 'PAYABLE' ? -1 : 1) * toCents(bill.amount), 0);
        const tooMany = bills.length > BULK_LIMIT;

        return el('div', { class: 'selection-bar', role: 'region', 'aria-label': 'Seleção' }, [
            el('span', { class: 'selection-count', text: `${bills.length} ${bills.length === 1 ? 'selecionado' : 'selecionados'}` }),
            el('span', { class: 'selection-sum', text: `${net < 0 ? '−' : '+'} ${fmtMoney(centsToDecimal(Math.abs(net)))}` }),
            tooMany ? el('span', { class: 'selection-warn', text: `Máximo de ${BULK_LIMIT} por vez` }) : null,
            el('button', { class: 'btn-ghost btn-sm', text: 'Limpar seleção', onClick: () => { selected.clear(); render(); } }),
            el('button', {
                class: 'btn-primary btn-sm',
                text: 'Baixar selecionados',
                disabled: tooMany || !accounts.length,
                title: !accounts.length ? 'Cadastre uma conta para baixar títulos' : '',
                onClick: () => bulkSettleDialog({ bills, accounts, onSaved: load })
            })
        ]);
    }

    function askCancel(bill) {
        confirmDialog({
            title: 'Cancelar título',
            message: `"${bill.description || bill.categoryName}" de ${fmtMoney(bill.amount)} deixa de contar na previsão. Ele continua no histórico como cancelado.`,
            confirmLabel: 'Cancelar título',
            onConfirm: async () => {
                await api.bills.cancel(bill.id);
                selected.delete(bill.id);
                toast('Título cancelado.', 'success');
                load();
            }
        });
    }

    function askUnsettle(bill) {
        confirmDialog({
            title: 'Desfazer baixa',
            message: `O lançamento de ${fmtMoney(bill.settledAmount)} gerado pela baixa será apagado do extrato e o título volta a ficar em aberto.`,
            confirmLabel: 'Desfazer baixa',
            onConfirm: async () => {
                await api.bills.unsettle(bill.id);
                toast('Baixa desfeita.', 'success');
                load();
            }
        });
    }

    function askDelete(bill) {
        if (bill.installmentGroupId) {
            // Só esta parcela: é o caminho de quem cadastra uma compra antiga e precisa tirar
            // as parcelas que já pagou antes de usar o app. As demais mantêm a numeração (5/10…).
            choiceDialog({
                title: `Excluir parcela ${bill.installmentNumber}/${bill.installmentTotal}`,
                message: `"${bill.description || bill.categoryName}" de ${fmtMoney(bill.amount)}, vence em ${fmtDate(bill.dueDate)}. `
                    + 'Excluir só esta parcela mantém as outras como estão. Excluir o parcelamento remove todas as parcelas restantes '
                    + '(se alguma já foi baixada, nada é excluído).',
                choices: [
                    {
                        label: 'Excluir parcelamento',
                        class: 'btn-danger',
                        run: async () => {
                            await api.bills.removeInstallments(bill.installmentGroupId);
                            [...selected.values()]
                                .filter((item) => item.installmentGroupId === bill.installmentGroupId)
                                .forEach((item) => selected.delete(item.id));
                            toast('Parcelamento excluído.', 'success');
                            load();
                        }
                    },
                    {
                        label: 'Excluir só esta parcela',
                        class: 'btn-primary',
                        run: async () => {
                            await api.bills.remove(bill.id);
                            selected.delete(bill.id);
                            toast(`Parcela ${bill.installmentNumber}/${bill.installmentTotal} excluída.`, 'success');
                            load();
                        }
                    }
                ]
            });
            return;
        }
        confirmDialog({
            title: 'Excluir título',
            message: `"${bill.description || bill.categoryName}" de ${fmtMoney(bill.amount)} será removido. Essa ação não pode ser desfeita.`,
            confirmLabel: 'Excluir',
            onConfirm: async () => {
                await api.bills.remove(bill.id);
                selected.delete(bill.id);
                toast('Título excluído.', 'success');
                load();
            }
        });
    }

    function actions(bill) {
        const buttons = [];
        if (bill.status === 'OPEN') {
            buttons.push(el('button', {
                class: 'btn-sm',
                text: bill.type === 'PAYABLE' ? 'Pagar' : 'Receber',
                disabled: !accounts.length,
                onClick: () => settleDialog({ bill, accounts, onSaved: load })
            }));
            buttons.push(el('button', { class: 'btn-ghost btn-sm', text: 'Editar', onClick: () => billDialog({ bill, accounts, categories, onSaved: load }) }));
            buttons.push(el('button', { class: 'btn-ghost btn-sm', text: 'Cancelar', onClick: () => askCancel(bill) }));
        }
        if (bill.status === 'SETTLED') {
            buttons.push(el('button', { class: 'btn-ghost btn-sm', text: 'Desfazer baixa', onClick: () => askUnsettle(bill) }));
        }
        // O backend só exclui título em aberto: cancelado é estado final.
        // Título de recorrência excluído seria gerado de novo na próxima sessão; para pular um mês, Cancelar.
        if (bill.status === 'OPEN' && !bill.recurrenceId) {
            buttons.push(el('button', { class: 'btn-ghost btn-sm btn-danger', text: 'Excluir', onClick: () => askDelete(bill) }));
        }
        return buttons;
    }

    function descriptionCell(bill) {
        const label = bill.description || bill.categoryName || '—';
        const tags = [];
        if (bill.installmentNumber) tags.push(`parcela ${bill.installmentNumber}/${bill.installmentTotal}`);
        if (bill.recurrenceId) tags.push('recorrente');
        return el('td', { class: 'desc' }, [
            el('span', { class: 'desc-main', text: label }),
            tags.length ? el('span', { class: 'cell-tag', text: tags.join(' · ') }) : null
        ]);
    }

    function amountCell(bill) {
        const shown = bill.status === 'SETTLED' ? bill.settledAmount : bill.amount;
        const sign = bill.type === 'PAYABLE' ? '−' : '+';
        return el('td', {
            class: `num amount-${bill.type === 'PAYABLE' ? 'out' : 'in'} ${bill.status === 'CANCELED' ? 'is-canceled' : ''}`,
            text: `${sign} ${fmtMoney(shown)}`
        });
    }

    function table() {
        const openRows = rows.filter((bill) => bill.status === 'OPEN');
        const allChecked = openRows.length > 0 && openRows.every((bill) => selected.has(bill.id));

        const headerCheck = el('input', {
            type: 'checkbox',
            'aria-label': 'Selecionar todos os títulos em aberto desta página',
            disabled: !openRows.length,
            onChange: () => {
                if (headerCheck.checked) openRows.forEach((bill) => selected.set(bill.id, bill));
                else openRows.forEach((bill) => selected.delete(bill.id));
                render();
            }
        });
        headerCheck.checked = allChecked;
        headerCheck.indeterminate = !allChecked && openRows.some((bill) => selected.has(bill.id));

        return stackable(el('div', { class: 'table-wrap' }, [
            el('table', { class: 'bills-table compact-rows' }, [
                el('thead', {}, el('tr', {}, [
                    el('th', { class: 'check' }, headerCheck),
                    sortHeader({ label: 'Vencimento', key: 'dueDate', sort, onSort: changeSort }),
                    sortHeader({ label: 'Descrição', key: 'description', sort, onSort: changeSort }),
                    el('th', { text: 'Categoria' }),
                    el('th', { text: 'Conta' }),
                    el('th', { text: 'Status' }),
                    sortHeader({ label: 'Valor', key: 'amount', sort, onSort: changeSort, numeric: true }),
                    el('th', { text: '' })
                ])),
                el('tbody', {}, rows.map((bill) => {
                    const checkbox = el('input', {
                        type: 'checkbox',
                        'aria-label': `Selecionar ${bill.description || bill.categoryName}`,
                        disabled: bill.status !== 'OPEN',
                        onChange: () => {
                            if (checkbox.checked) selected.set(bill.id, bill);
                            else selected.delete(bill.id);
                            render();
                        }
                    });
                    checkbox.checked = selected.has(bill.id);

                    return el('tr', { class: [isOverdue(bill) ? 'is-overdue' : '', selected.has(bill.id) ? 'is-selected' : ''].join(' ').trim() || null }, [
                        el('td', { class: 'check' }, checkbox),
                        el('td', { class: 'due', text: fmtDate(bill.dueDate) }),
                        descriptionCell(bill),
                        el('td', { class: 'muted cat', text: bill.categoryName || '—' }),
                        el('td', { class: `muted acct ${bill.accountName ? '' : 'is-empty'}`, text: bill.accountName || '—' }),
                        el('td', { class: 'status' }, billStatusBadge(bill)),
                        amountCell(bill),
                        el('td', { class: 'actions' }, actions(bill))
                    ]);
                }))
            ])
        ]));
    }

    let result = null;

    function render() {
        const content = rows.length
            ? el('div', { class: 'card' }, [
                table(),
                pager({
                    page: result.number,
                    totalPages: result.totalPages,
                    totalElements: result.totalElements,
                    onChange: (next) => { page = next; load(); }
                })
            ])
            : el('div', { class: 'card' }, [emptyState(filters.status === 'OPEN' && !filters.type
                ? 'Nada em aberto. Cadastre um título ou um parcelamento para acompanhar o que vence.'
                : 'Nenhum título encontrado com esses filtros.')]);

        view.replaceChildren(head(), filterBar(), selectionBar() || '', content);
    }

    async function load() {
        view.replaceChildren(head(), filterBar(), el('div', { class: 'empty', text: 'Carregando…' }));

        try {
            result = normalizePage(await api.bills.list({ ...filters, page, size: 20, sort: [`${sort.key},${sort.dir}`, `id,${sort.dir}`] }));
            rows = result.content;
            // Atualiza a cópia guardada de quem continua selecionado; quem saiu de OPEN sai da seleção.
            rows.forEach((bill) => {
                if (!selected.has(bill.id)) return;
                if (bill.status === 'OPEN') selected.set(bill.id, bill);
                else selected.delete(bill.id);
            });
            render();
        } catch (error) {
            reportError(error);
            view.replaceChildren(head(), filterBar(), emptyState(error?.message || 'Não foi possível carregar os títulos.'));
        }
    }

    await load();
    return view;
}
