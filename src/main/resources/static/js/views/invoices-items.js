import { api, fetchAll } from '../api.js';
import { el, fmtMoney, toast, reportError, formDialog, confirmDialog } from '../ui.js';

/**
 * Ações sobre os itens de uma fatura (spec 028): editar e apagar uma compra no cartão. Pagamentos
 * (transferência) e lançamentos de baixa de título não têm ações aqui.
 */

const SCOPE_LABEL = {
    ONLY_THIS: 'Só esta parcela',
    THIS_AND_FOLLOWING: 'Esta e as seguintes'
};

export const isInstallment = (transaction) => Boolean(transaction.installmentGroupId);
const isFixed = (transaction) => Boolean(transaction.transferGroupId) || Boolean(transaction.billId);

/** Quais ações a linha oferece: pagamento e baixa de título não têm ações. */
export function itemActions(transaction) {
    return isFixed(transaction) ? [] : ['edit', 'delete'];
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

export function deleteMessage(transaction) {
    const name = `"${transaction.description || transaction.categoryName || 'Compra'}"`;
    if (isInstallment(transaction)) {
        return `${name} é a parcela ${transaction.installmentNumber} de ${transaction.installmentTotal}. Todas as ${transaction.installmentTotal} parcelas serão apagadas juntas e o histórico some. Essa ação não pode ser desfeita.`;
    }
    return `${name} de ${fmtMoney(transaction.amount)} será apagada e o histórico some. Essa ação não pode ser desfeita.`;
}

/** Botões de ação da linha; `onChanged` recarrega as faturas depois de cada ação. */
export function itemActionButtons(transaction, { onChanged } = {}) {
    const label = transaction.description || transaction.categoryName || 'compra';
    const button = (text, onClick, extra = '') => el('button', {
        type: 'button', class: `btn-ghost btn-sm ${extra}`.trim(), text, 'aria-label': `${text}: ${label}`, onClick
    });

    const handlers = {
        edit: () => button('Editar', () => openEdit(transaction, onChanged)),
        delete: () => button('Apagar', () => askDelete(transaction, onChanged), 'btn-danger')
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
        fields.push({
            name: 'scope', label: 'Aplicar a', type: 'select', required: true,
            options: Object.entries(SCOPE_LABEL).map(([value, label]) => ({ value, label })),
            hint: `Parcela ${transaction.installmentNumber}/${transaction.installmentTotal}. Em "Esta e as seguintes", descrição, categoria e valor valem para as seguintes; a data muda só desta parcela.`
        });
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
