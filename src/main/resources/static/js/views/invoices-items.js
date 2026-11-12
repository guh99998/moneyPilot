import { api, fetchAll } from '../api.js';
import { el, fmtMoney, fmtDate, badge, toast, reportError, formDialog, confirmDialog, todayIso } from '../ui.js';

/**
 * Ações sobre os itens de uma fatura (spec 028): editar, estornar, apagar e desfazer o estorno de uma compra
 * no cartão. Pagamentos (transferência), lançamentos de baixa de título e o próprio estorno não têm ações aqui.
 */

const SCOPE_LABEL = {
    ONLY_THIS: 'Só esta parcela',
    THIS_AND_FOLLOWING: 'Esta e as seguintes',
    WHOLE_PURCHASE: 'A compra inteira'
};

export const isInstallment = (transaction) => Boolean(transaction.installmentGroupId);
export const isRefund = (transaction) => Boolean(transaction.refundOfTransactionId);
export const isRefunded = (transaction) => Boolean(transaction.refundTransactionId);
const isFixed = (transaction) => Boolean(transaction.transferGroupId) || Boolean(transaction.billId);

/** Quais ações a linha oferece: compra estornada só desfaz; estorno, pagamento e baixa não têm ações. */
export function itemActions(transaction) {
    if (isFixed(transaction) || isRefund(transaction)) return [];
    if (isRefunded(transaction)) return ['undoRefund'];
    return transaction.type === 'EXPENSE' ? ['edit', 'refund', 'delete'] : ['edit', 'delete'];
}

/** Corpo do PUT: conta e tipo ficam os do lançamento (a API recusa mudar em parcela). */
export function editPayload(transaction, values) {
    return {
        accountId: transaction.accountId,
        type: transaction.type,
        categoryId: Number(values.categoryId),
        amount: values.amount,
        description: values.description,
        date: values.date
    };
}

/** O `scope` só vale em parcela; fora dela a API recusa `THIS_AND_FOLLOWING`. */
export function editScope(transaction, values) {
    return isInstallment(transaction) ? (values.scope || 'ONLY_THIS') : undefined;
}

export function refundPayload(transaction, values) {
    const payload = { date: values.date };
    if (isInstallment(transaction)) payload.scope = values.scope || 'ONLY_THIS';
    return payload;
}

/** Prévia do diálogo de estorno: quantas parcelas e a regra da data (a maior entre a informada e a da parcela). */
export function refundPreview(transaction, values) {
    if (!values.date) return 'Informe a data do estorno.';
    if (!isInstallment(transaction)) {
        return `Cria uma receita de ${fmtMoney(transaction.amount)} no cartão em ${fmtDate(values.date)}.`;
    }

    const { installmentNumber: number, installmentTotal: total } = transaction;
    const scope = values.scope || 'ONLY_THIS';
    const count = scope === 'WHOLE_PURCHASE' ? total : scope === 'THIS_AND_FOLLOWING' ? total - number + 1 : 1;
    const first = scope === 'WHOLE_PURCHASE' ? 1 : number;
    const range = count === 1 ? `a parcela ${number}/${total}` : `${count} parcelas, de ${first}/${total} a ${total}/${total}`;
    return `Estorna ${range}, de ${fmtMoney(transaction.amount)} cada. Cada estorno cai na data informada (${fmtDate(values.date)}) ou na da parcela, a que for mais tarde. Parcelas já estornadas são puladas.`;
}

export function deleteMessage(transaction) {
    const name = `"${transaction.description || transaction.categoryName || 'Compra'}"`;
    const advice = 'Se a compra foi cancelada e já foi cobrada, prefira estornar. Essa ação não pode ser desfeita.';
    if (isInstallment(transaction)) {
        return `${name} é a parcela ${transaction.installmentNumber} de ${transaction.installmentTotal}. Todas as ${transaction.installmentTotal} parcelas serão apagadas juntas e o histórico some. ${advice}`;
    }
    return `${name} de ${fmtMoney(transaction.amount)} será apagada e o histórico some. ${advice}`;
}

const scopeField = (options, hint) => ({
    name: 'scope', label: 'Aplicar a', type: 'select', required: true,
    options: options.map((value) => ({ value, label: SCOPE_LABEL[value] })), hint
});

/** Selos da linha: "Estorno" na receita do estorno e "Estornada" na compra. */
export function itemBadges(transaction) {
    if (isRefund(transaction)) return [badge('income', 'Estorno')];
    if (isRefunded(transaction)) return [badge('canceled', 'Estornada')];
    return [];
}

/** Botões de ação da linha; `onChanged` recarrega as faturas depois de cada ação. */
export function itemActionButtons(transaction, { onChanged } = {}) {
    const label = transaction.description || transaction.categoryName || 'compra';
    const button = (text, onClick, extra = '') => el('button', {
        type: 'button', class: `btn-ghost btn-sm ${extra}`.trim(), text, 'aria-label': `${text}: ${label}`, onClick
    });

    const handlers = {
        edit: () => button('Editar', () => openEdit(transaction, onChanged)),
        refund: () => button('Estornar', () => openRefund(transaction, onChanged)),
        delete: () => button('Apagar', () => askDelete(transaction, onChanged), 'btn-danger'),
        undoRefund: () => button('Desfazer estorno', () => askUndoRefund(transaction, onChanged))
    };
    return itemActions(transaction).map((name) => handlers[name]());
}

async function openEdit(transaction, onChanged) {
    let categories;
    try {
        categories = (await fetchAll(api.categories.list)).filter((category) => category.type === transaction.type);
    } catch (error) {
        reportError(error);
        return;
    }
    const fields = [
        { name: 'description', label: 'Descrição', type: 'text' },
        {
            name: 'categoryId', label: 'Categoria', type: 'select', required: true,
            options: categories.map((category) => ({ value: category.id, label: category.name }))
        },
        { name: 'amount', label: 'Valor', type: 'number', step: '0.01', min: '0.01', required: true },
        { name: 'date', label: 'Data', type: 'date', required: true }
    ];
    if (isInstallment(transaction)) {
        fields.push(scopeField(['ONLY_THIS', 'THIS_AND_FOLLOWING'],
            `Parcela ${transaction.installmentNumber}/${transaction.installmentTotal}. Em "Esta e as seguintes", descrição, categoria e valor valem para as seguintes; a data muda só desta parcela.`));
    }
    formDialog({
        title: isInstallment(transaction) ? 'Editar parcela' : 'Editar compra',
        fields,
        values: {
            description: transaction.description, categoryId: transaction.categoryId,
            amount: transaction.amount, date: transaction.date, scope: 'ONLY_THIS'
        },
        onSubmit: async (values) => {
            const scope = editScope(transaction, values);
            await api.transactions.update(transaction.id, editPayload(transaction, values), scope ? { scope } : undefined);
            toast('Compra atualizada.', 'success');
            onChanged?.();
        }
    });
}

function openRefund(transaction, onChanged) {
    const fields = [{
        name: 'date', label: 'Data do estorno', type: 'date', required: true,
        hint: 'A categoria é "Estorno" e o valor é o da compra, inteiro.'
    }];
    if (isInstallment(transaction)) {
        fields.push(scopeField(['ONLY_THIS', 'THIS_AND_FOLLOWING', 'WHOLE_PURCHASE']));
    }
    formDialog({
        title: 'Estornar compra',
        fields,
        values: { date: todayIso(), scope: 'ONLY_THIS' },
        preview: (values) => refundPreview(transaction, values),
        submitLabel: 'Estornar',
        onSubmit: async (values) => {
            await api.transactions.refund(transaction.id, refundPayload(transaction, values));
            toast('Compra estornada.', 'success');
            onChanged?.();
        }
    });
}

function askDelete(transaction, onChanged) {
    confirmDialog({
        title: isInstallment(transaction) ? 'Apagar compra parcelada' : 'Apagar compra',
        message: deleteMessage(transaction),
        confirmLabel: 'Apagar',
        onConfirm: async () => {
            await (isInstallment(transaction)
                ? api.transactions.removeInstallments(transaction.installmentGroupId)
                : api.transactions.remove(transaction.id));
            toast('Compra apagada.', 'success');
            onChanged?.();
        }
    });
}

function askUndoRefund(transaction, onChanged) {
    confirmDialog({
        title: 'Desfazer estorno',
        message: `A receita do estorno some e a compra de ${fmtMoney(transaction.amount)} volta a contar na fatura.`,
        confirmLabel: 'Desfazer estorno',
        onConfirm: async () => {
            await api.transactions.remove(transaction.refundTransactionId);
            toast('Estorno desfeito.', 'success');
            onChanged?.();
        }
    });
}
