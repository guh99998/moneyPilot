import { api, fetchAll, normalizePage } from '../api.js';
import {
    el, clear, fmtMoney, fmtDate, badge, toast, reportError,
    pageHead, emptyState, pager, formDialog, confirmDialog, todayIso, stackable
} from '../ui.js';

const filters = { accountId: '', categoryId: '', type: '', from: '', to: '' };
let page = 0;

const TYPE_LABEL = { INCOME: 'Receita', EXPENSE: 'Despesa', TRANSFER: 'Transferência' };

function typeBadge(transaction) {
    if (transaction.transferGroupId) return badge('transfer', 'Transferência');
    return transaction.type === 'INCOME'
        ? badge('income', 'Receita')
        : badge('expense', 'Despesa');
}

function signedAmount(transaction) {
    const amount = fmtMoney(transaction.amount);
    return transaction.type === 'INCOME' ? `+ ${amount}` : `− ${amount}`;
}

/**
 * Diálogo de lançamento: o tipo filtra as categorias em tempo real, porque a API
 * rejeita categoria de tipo diferente do lançamento.
 */
function transactionDialog({ title, values = {}, accounts, categories, onSubmit }) {
    const dialog = el('dialog');
    const errorBox = el('p', { class: 'dialog-error', hidden: true });

    const typeSelect = el('select', {
        required: true,
        onChange: () => fillCategories()
    }, [
        el('option', { value: 'EXPENSE', text: 'Despesa', selected: (values.type || 'EXPENSE') === 'EXPENSE' }),
        el('option', { value: 'INCOME', text: 'Receita', selected: values.type === 'INCOME' })
    ]);

    const categorySelect = el('select', { required: true });
    const accountSelect = el('select', { required: true }, accounts.map((account) => el('option', {
        value: account.id,
        text: account.name,
        selected: String(account.id) === String(values.accountId ?? '')
    })));

    const amountInput = el('input', { type: 'number', step: '0.01', min: '0.01', required: true, value: values.amount ?? '' });
    const dateInput = el('input', { type: 'date', required: true, value: values.date ?? todayIso() });
    const descriptionInput = el('input', { type: 'text', maxlength: '255', value: values.description ?? '' });

    function fillCategories() {
        const wanted = typeSelect.value;
        const options = categories.filter((category) => category.type === wanted);
        clear(categorySelect);
        if (!options.length) {
            categorySelect.append(el('option', { value: '', text: 'Nenhuma categoria deste tipo' }));
            return;
        }
        options.forEach((category) => categorySelect.append(el('option', {
            value: category.id,
            text: category.name,
            selected: String(category.id) === String(values.categoryId ?? '')
        })));
    }

    fillCategories();

    const submitBtn = el('button', { class: 'btn-primary', type: 'submit', text: 'Salvar' });

    const form = el('form', {
        class: 'dialog-body',
        onSubmit: async (event) => {
            event.preventDefault();
            submitBtn.disabled = true;
            errorBox.hidden = true;
            try {
                await onSubmit({
                    accountId: Number(accountSelect.value),
                    categoryId: Number(categorySelect.value),
                    amount: amountInput.value,
                    type: typeSelect.value,
                    description: descriptionInput.value.trim() || null,
                    date: dateInput.value
                });
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
        el('div', { class: 'form-row' }, [
            el('div', { class: 'field' }, [el('label', { text: 'Tipo' }), typeSelect]),
            el('div', { class: 'field' }, [el('label', { text: 'Valor' }), amountInput])
        ]),
        el('div', { class: 'field' }, [el('label', { text: 'Conta' }), accountSelect]),
        el('div', { class: 'field' }, [el('label', { text: 'Categoria' }), categorySelect]),
        el('div', { class: 'field' }, [el('label', { text: 'Data' }), dateInput]),
        el('div', { class: 'field' }, [el('label', { text: 'Descrição (opcional)' }), descriptionInput]),
        el('div', { class: 'dialog-actions' }, [
            el('button', { type: 'button', text: 'Cancelar', onClick: () => dialog.close() }),
            submitBtn
        ])
    ]);

    dialog.append(form);
    dialog.addEventListener('close', () => dialog.remove());
    document.body.append(dialog);
    dialog.showModal();
    amountInput.focus();
}

export async function renderTransactions() {
    const view = el('div');
    const [accounts, categories] = await Promise.all([
        fetchAll(api.accounts.list),
        fetchAll(api.categories.list)
    ]);

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
        return el('div', { class: 'filters' }, [
            selectField('Conta', 'accountId', accounts.map((a) => ({ value: a.id, label: a.name })), 'Todas'),
            selectField('Categoria', 'categoryId', categories.map((c) => ({ value: c.id, label: c.name })), 'Todas'),
            selectField('Tipo', 'type', Object.entries(TYPE_LABEL).map(([value, label]) => ({ value, label })), 'Todos'),
            dateField('De', 'from'),
            dateField('Até', 'to'),
            el('button', {
                class: 'btn-sm',
                text: 'Limpar',
                onClick: () => {
                    Object.keys(filters).forEach((key) => { filters[key] = ''; });
                    page = 0;
                    load();
                }
            })
        ]);
    }

    function head() {
        return pageHead('Lançamentos', 'Receitas, despesas e transferências entre suas contas', [
            el('button', {
                text: 'Transferência',
                disabled: accounts.length < 2,
                title: accounts.length < 2 ? 'Cadastre ao menos duas contas' : '',
                onClick: openTransferDialog
            }),
            el('button', {
                class: 'btn-primary',
                text: 'Novo lançamento',
                disabled: !accounts.length || !categories.length,
                onClick: () => openTransactionDialog()
            })
        ]);
    }

    function openTransactionDialog(transaction) {
        transactionDialog({
            title: transaction ? 'Editar lançamento' : 'Novo lançamento',
            values: transaction || {},
            accounts,
            categories,
            onSubmit: async (payload) => {
                if (transaction) await api.transactions.update(transaction.id, payload);
                else await api.transactions.create(payload);
                toast(transaction ? 'Lançamento atualizado.' : 'Lançamento criado.', 'success');
                load();
            }
        });
    }

    function openTransferDialog() {
        const accountOptions = accounts.map((account) => ({ value: account.id, label: account.name }));
        formDialog({
            title: 'Nova transferência',
            fields: [
                { name: 'fromAccountId', label: 'De', type: 'select', options: accountOptions, required: true },
                { name: 'toAccountId', label: 'Para', type: 'select', options: accountOptions, required: true },
                { name: 'amount', label: 'Valor', type: 'number', step: '0.01', min: '0.01', required: true },
                { name: 'date', label: 'Data', type: 'date', required: true },
                { name: 'description', label: 'Descrição (opcional)', type: 'text' }
            ],
            values: { date: todayIso(), toAccountId: accounts[1]?.id },
            onSubmit: async (values) => {
                await api.transactions.transfer({
                    fromAccountId: Number(values.fromAccountId),
                    toAccountId: Number(values.toAccountId),
                    amount: values.amount,
                    date: values.date,
                    description: values.description
                });
                toast('Transferência registrada.', 'success');
                load();
            }
        });
    }

    function askDelete(transaction) {
        const isTransfer = Boolean(transaction.transferGroupId);
        confirmDialog({
            title: isTransfer ? 'Excluir transferência' : 'Excluir lançamento',
            message: isTransfer
                ? `A transferência de ${fmtMoney(transaction.amount)} será desfeita por inteiro: os dois lançamentos (saída e entrada) somem juntos. Essa ação não pode ser desfeita.`
                : `"${transaction.description || transaction.categoryName}" de ${fmtMoney(transaction.amount)} será removido. Essa ação não pode ser desfeita.`,
            confirmLabel: 'Excluir',
            onConfirm: async () => {
                await api.transactions.remove(transaction.id);
                toast('Lançamento excluído.', 'success');
                load();
            }
        });
    }

    function table(rows) {
        return stackable(el('div', { class: 'table-wrap' }, [
            el('table', {}, [
                el('thead', {}, el('tr', {}, [
                    el('th', { text: 'Data' }),
                    el('th', { text: 'Descrição' }),
                    el('th', { text: 'Categoria' }),
                    el('th', { text: 'Conta' }),
                    el('th', { text: 'Tipo' }),
                    el('th', { class: 'num', text: 'Valor' }),
                    el('th', { text: '' })
                ])),
                el('tbody', {}, rows.map((transaction) => {
                    const isTransfer = Boolean(transaction.transferGroupId);
                    return el('tr', {}, [
                        el('td', { text: fmtDate(transaction.date) }),
                        el('td', { text: transaction.description || '—' }),
                        el('td', { class: 'muted', text: transaction.categoryName || '—' }),
                        el('td', { class: 'muted', text: transaction.accountName || '—' }),
                        el('td', {}, typeBadge(transaction)),
                        el('td', {
                            class: 'num',
                            style: `color: var(--${transaction.type === 'INCOME' ? 'income' : 'expense'})`,
                            text: signedAmount(transaction)
                        }),
                        el('td', { class: 'actions' }, [
                            isTransfer
                                ? el('span', { class: 'meter-foot', text: 'não editável' })
                                : el('button', { class: 'btn-ghost btn-sm', text: 'Editar', onClick: () => openTransactionDialog(transaction) }),
                            el('button', { class: 'btn-ghost btn-sm btn-danger', text: 'Excluir', onClick: () => askDelete(transaction) })
                        ])
                    ]);
                }))
            ])
        ]));
    }

    async function load() {
        view.replaceChildren(head(), filterBar(), el('div', { class: 'empty', text: 'Carregando…' }));

        try {
            const result = normalizePage(await api.transactions.list({ ...filters, page, size: 20 }));
            const content = result.content.length
                ? el('div', { class: 'card' }, [
                    table(result.content),
                    pager({
                        page: result.number,
                        totalPages: result.totalPages,
                        totalElements: result.totalElements,
                        onChange: (next) => { page = next; load(); }
                    })
                ])
                : el('div', { class: 'card' }, [emptyState('Nenhum lançamento encontrado com esses filtros.')]);

            view.replaceChildren(head(), filterBar(), content);
        } catch (error) {
            reportError(error);
            view.replaceChildren(head(), filterBar(), emptyState(error?.message || 'Não foi possível carregar os lançamentos.'));
        }
    }

    await load();
    return view;
}
