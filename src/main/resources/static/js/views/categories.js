import { api, fetchAll } from '../api.js';
import {
    el, badge, toast, reportError, pageHead, emptyState, formDialog, confirmDialog
} from '../ui.js';

const TYPE_OPTIONS = [
    { value: 'EXPENSE', label: 'Despesa' },
    { value: 'INCOME', label: 'Receita' }
];

export async function renderCategories() {
    const view = el('div');

    function head() {
        return pageHead('Categorias', 'As categorias padrão vêm com o sistema; as suas você controla', [
            el('button', { class: 'btn-primary', text: 'Nova categoria', onClick: () => openDialog() })
        ]);
    }

    function openDialog(category) {
        formDialog({
            title: category ? 'Editar categoria' : 'Nova categoria',
            fields: [
                { name: 'name', label: 'Nome', type: 'text', required: true, placeholder: 'Ex.: Academia' },
                { name: 'type', label: 'Tipo', type: 'select', options: TYPE_OPTIONS, required: true }
            ],
            values: category ? { name: category.name, type: category.type } : {},
            onSubmit: async (values) => {
                if (category) await api.categories.update(category.id, values);
                else await api.categories.create(values);
                toast(category ? 'Categoria atualizada.' : 'Categoria criada.', 'success');
                load();
            }
        });
    }

    function askDelete(category) {
        confirmDialog({
            title: 'Excluir categoria',
            message: `"${category.name}" será removida. Se houver lançamentos ou orçamentos usando ela, a API vai recusar a exclusão.`,
            confirmLabel: 'Excluir',
            onConfirm: async () => {
                await api.categories.remove(category.id);
                toast('Categoria excluída.', 'success');
                load();
            }
        });
    }

    function table(rows) {
        return el('div', { class: 'table-wrap' }, [
            el('table', {}, [
                el('thead', {}, el('tr', {}, [
                    el('th', { text: 'Categoria' }),
                    el('th', { text: 'Tipo' }),
                    el('th', { text: 'Origem' }),
                    el('th', { text: '' })
                ])),
                el('tbody', {}, rows.map((row) => {
                    const isDefault = row.userId === null || row.userId === undefined;
                    return el('tr', {}, [
                        el('td', { text: row.name }),
                        el('td', {}, row.type === 'INCOME' ? badge('income', 'Receita') : badge('expense', 'Despesa')),
                        el('td', { class: 'muted', text: isDefault ? 'Padrão do sistema' : 'Sua' }),
                        el('td', { class: 'actions' }, isDefault
                            ? [el('span', { class: 'meter-foot', text: 'não editável' })]
                            : [
                                el('button', { class: 'btn-ghost btn-sm', text: 'Editar', onClick: () => openDialog(row) }),
                                el('button', { class: 'btn-ghost btn-sm btn-danger', text: 'Excluir', onClick: () => askDelete(row) })
                            ])
                    ]);
                }))
            ])
        ]);
    }

    async function load() {
        view.replaceChildren(head(), el('div', { class: 'empty', text: 'Carregando…' }));

        try {
            const categories = await fetchAll(api.categories.list);
            const sorted = [...categories].sort((a, b) => {
                if (a.type !== b.type) return a.type === 'INCOME' ? -1 : 1;
                return a.name.localeCompare(b.name, 'pt-BR');
            });

            view.replaceChildren(head(), el('div', { class: 'card' }, [
                sorted.length ? table(sorted) : emptyState('Nenhuma categoria disponível.')
            ]));
        } catch (error) {
            reportError(error);
            view.replaceChildren(head(), emptyState(error?.message || 'Não foi possível carregar as categorias.'));
        }
    }

    await load();
    return view;
}
