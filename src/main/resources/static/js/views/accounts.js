import { api, fetchAll } from '../api.js';
import {
    el, fmtMoney, toast, reportError, pageHead, emptyState, formDialog, confirmDialog, stackable, sortHeader, sortSelect
} from '../ui.js';

const TYPE_LABEL = {
    CHECKING: 'Conta corrente',
    SAVING: 'Poupança',
    CREDIT_CARD: 'Cartão de crédito',
    CASH: 'Dinheiro',
    INVESTMENT: 'Investimento'
};

let sort = { key: 'name', dir: 'asc' };

const SORT_OPTIONS = [
    { key: 'name', label: 'Conta', asc: 'A → Z', desc: 'Z → A' },
    { key: 'type', label: 'Tipo', asc: 'A → Z', desc: 'Z → A' },
    { key: 'initialBalance', label: 'Saldo inicial', asc: 'menor primeiro', desc: 'maior primeiro' },
    { key: 'currentBalance', label: 'Saldo atual', asc: 'menor primeiro', desc: 'maior primeiro' },
    { key: 'transactionCount', label: 'Lançamentos', asc: 'menos primeiro', desc: 'mais primeiro' }
];
const NUMERIC_KEYS = new Set(['initialBalance', 'currentBalance', 'transactionCount']);

/** Ordena no navegador: a tela já carrega todas as contas. Vazios vão sempre para o fim. */
function sortRows(rows) {
    const factor = sort.dir === 'asc' ? 1 : -1;
    const value = (row) => (sort.key === 'type' ? TYPE_LABEL[row.type] || row.type : row[sort.key]);
    return [...rows].sort((a, b) => {
        const left = value(a);
        const right = value(b);
        if (left === null || left === undefined) return 1;
        if (right === null || right === undefined) return -1;
        if (NUMERIC_KEYS.has(sort.key)) return (Number(left) - Number(right)) * factor || a.id - b.id;
        return String(left).localeCompare(String(right), 'pt-BR', { sensitivity: 'base' }) * factor || a.id - b.id;
    });
}

const TYPE_OPTIONS = Object.entries(TYPE_LABEL).map(([value, label]) => ({ value, label }));

function accountFields() {
    return [
        { name: 'name', label: 'Nome', type: 'text', required: true, placeholder: 'Ex.: Nubank' },
        { name: 'accountType', label: 'Tipo', type: 'select', options: TYPE_OPTIONS, required: true },
        {
            name: 'initialBalance', label: 'Saldo inicial', type: 'number', step: '0.01', required: true,
            hint: 'Quanto havia nessa conta antes do primeiro lançamento.'
        }
    ];
}

export async function renderAccounts() {
    const view = el('div');

    function head() {
        return pageHead('Contas', 'Onde seu dinheiro entra e sai', [
            el('button', { class: 'btn-primary', text: 'Nova conta', onClick: () => openDialog() })
        ]);
    }

    function openDialog(account) {
        formDialog({
            title: account ? 'Editar conta' : 'Nova conta',
            fields: accountFields(),
            values: account
                ? { name: account.name, accountType: account.type, initialBalance: account.initialBalance }
                : { initialBalance: '0' },
            onSubmit: async (values) => {
                const payload = {
                    name: values.name,
                    accountType: values.accountType,
                    initialBalance: values.initialBalance
                };
                if (account) await api.accounts.update(account.id, payload);
                else await api.accounts.create(payload);
                toast(account ? 'Conta atualizada.' : 'Conta criada.', 'success');
                load();
            }
        });
    }

    function askDelete(account) {
        confirmDialog({
            title: 'Excluir conta',
            message: `"${account.name}" será removida. Se houver lançamentos ligados a ela, a API vai recusar a exclusão.`,
            confirmLabel: 'Excluir',
            onConfirm: async () => {
                await api.accounts.remove(account.id);
                toast('Conta excluída.', 'success');
                load();
            }
        });
    }

    function table(rows) {
        return stackable(el('div', { class: 'table-wrap' }, [
            el('table', {}, [
                el('thead', {}, el('tr', {}, [
                    sortHeader({ label: 'Conta', key: 'name', sort, onSort: changeSort }),
                    sortHeader({ label: 'Tipo', key: 'type', sort, onSort: changeSort }),
                    sortHeader({ label: 'Saldo inicial', key: 'initialBalance', sort, onSort: changeSort, numeric: true }),
                    sortHeader({ label: 'Saldo atual', key: 'currentBalance', sort, onSort: changeSort, numeric: true }),
                    sortHeader({ label: 'Lançamentos', key: 'transactionCount', sort, onSort: changeSort, numeric: true }),
                    el('th', { text: '' })
                ])),
                el('tbody', {}, sortRows(rows).map((row) => el('tr', {}, [
                    el('td', { class: 'desc', text: row.name }),
                    el('td', { class: 'muted', text: TYPE_LABEL[row.type] || row.type }),
                    el('td', { class: 'num muted', text: fmtMoney(row.initialBalance) }),
                    el('td', { class: 'num', text: row.currentBalance === null ? '—' : fmtMoney(row.currentBalance) }),
                    el('td', { class: 'num muted', text: row.transactionCount ?? '—' }),
                    el('td', { class: 'actions' }, [
                        el('button', { class: 'btn-ghost btn-sm', text: 'Editar', onClick: () => openDialog(row) }),
                        el('button', { class: 'btn-ghost btn-sm btn-danger', text: 'Excluir', onClick: () => askDelete(row) })
                    ])
                ])))
            ])
        ]));
    }

    let loaded = [];

    function changeSort(next) {
        sort = next;
        render();
    }

    function render() {
        view.replaceChildren(
            head(),
            loaded.length > 1 ? el('div', { class: 'sort-only' }, sortSelect({ options: SORT_OPTIONS, sort, onSort: changeSort })) : '',
            el('div', { class: 'card' }, [
                loaded.length ? table(loaded) : emptyState('Nenhuma conta cadastrada ainda.')
            ])
        );
    }

    async function load() {
        view.replaceChildren(head(), el('div', { class: 'empty', text: 'Carregando…' }));

        try {
            const accounts = await fetchAll(api.accounts.list);
            const rows = await Promise.all(accounts.map(async (account) => {
                try {
                    const balance = await api.accounts.balance(account.id);
                    return { ...account, currentBalance: balance.currentBalance, transactionCount: balance.transactionCount };
                } catch {
                    return { ...account, currentBalance: null, transactionCount: null };
                }
            }));

            loaded = rows;
            render();
        } catch (error) {
            reportError(error);
            view.replaceChildren(head(), emptyState(error?.message || 'Não foi possível carregar as contas.'));
        }
    }

    await load();
    return view;
}
