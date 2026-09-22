import { ApiError } from './api.js';

/** Cria um elemento. props aceita atributos, `class`, `text`, `html` e on<Event>. */
export function el(tag, props = {}, children = []) {
    const node = document.createElement(tag);
    Object.entries(props).forEach(([key, value]) => {
        if (value === undefined || value === null || value === false) return;
        if (key === 'class') node.className = value;
        else if (key === 'text') node.textContent = value;
        else if (key === 'html') node.innerHTML = value;
        else if (key === 'dataset') Object.assign(node.dataset, value);
        else if (key.startsWith('on') && typeof value === 'function') node.addEventListener(key.slice(2).toLowerCase(), value);
        else if (key === 'value') node.value = value;
        else node.setAttribute(key, value === true ? '' : value);
    });
    (Array.isArray(children) ? children : [children])
        .filter(Boolean)
        .forEach((child) => node.append(child));
    return node;
}

export function clear(node) {
    node.replaceChildren();
    return node;
}

const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const MONTHS = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho',
    'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];

export function fmtMoney(value) {
    return money.format(Number(value ?? 0));
}

export function fmtDate(isoDate) {
    if (!isoDate) return '—';
    const [year, month, day] = String(isoDate).split('-');
    return `${day}/${month}/${year}`;
}

export function monthName(month) {
    return MONTHS[Number(month) - 1] || '';
}

export function todayIso() {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

export function toast(message, kind = 'info') {
    const node = el('div', { class: `toast ${kind}`, text: message });
    document.getElementById('toasts').append(node);
    setTimeout(() => node.remove(), 4000);
}

export function reportError(error) {
    if (error instanceof ApiError && error.status === 401) return;
    toast(error?.message || 'Algo deu errado.', 'error');
}

export function emptyState(message) {
    return el('div', { class: 'empty', text: message });
}

export function badge(kind, label) {
    return el('span', { class: `badge badge-${kind}` }, [
        el('span', { class: 'swatch' }),
        el('span', { text: label })
    ]);
}

/**
 * Formulário em modal. `fields` são descritores:
 * { name, label, type, options:[{value,label}], required, step, min, max, hint }
 * `onSubmit(values)` pode lançar ApiError — erros de campo voltam inline.
 */
export function formDialog({ title, fields, values = {}, submitLabel = 'Salvar', onSubmit }) {
    const dialog = el('dialog');
    const errorBox = el('p', { class: 'dialog-error', hidden: true });
    const inputs = new Map();
    const errors = new Map();

    const body = el('form', {
        class: 'dialog-body',
        method: 'dialog',
        onSubmit: async (event) => {
            event.preventDefault();
            submitBtn.disabled = true;
            errorBox.hidden = true;
            errors.forEach((node) => { node.textContent = ''; });

            const payload = {};
            fields.forEach((field) => {
                const raw = inputs.get(field.name).value;
                payload[field.name] = raw === '' ? null : raw;
            });

            try {
                await onSubmit(payload);
                dialog.close();
            } catch (error) {
                if (error instanceof ApiError && error.fieldErrors) {
                    Object.entries(error.fieldErrors).forEach(([name, message]) => {
                        if (errors.has(name)) errors.get(name).textContent = message;
                    });
                }
                errorBox.textContent = error?.message || 'Não foi possível salvar.';
                errorBox.hidden = false;
                submitBtn.disabled = false;
            }
        }
    });

    const submitBtn = el('button', { class: 'btn-primary', type: 'submit', text: submitLabel });

    body.append(el('h2', { text: title }), errorBox);

    fields.forEach((field) => {
        const id = `f-${field.name}-${Math.random().toString(36).slice(2, 7)}`;
        const errorNode = el('span', { class: 'error' });
        let input;

        if (field.type === 'select') {
            input = el('select', { id, required: field.required },
                (field.options || []).map((option) => el('option', {
                    value: option.value,
                    text: option.label,
                    selected: String(option.value) === String(values[field.name] ?? '')
                }))
            );
            if (values[field.name] === undefined || values[field.name] === null) input.selectedIndex = 0;
        } else {
            input = el('input', {
                id,
                type: field.type || 'text',
                required: field.required,
                step: field.step,
                min: field.min,
                max: field.max,
                placeholder: field.placeholder,
                value: values[field.name] ?? ''
            });
        }

        inputs.set(field.name, input);
        errors.set(field.name, errorNode);
        body.append(el('div', { class: 'field' }, [
            el('label', { for: id, text: field.label }),
            input,
            field.hint ? el('span', { class: 'meter-foot', text: field.hint }) : null,
            errorNode
        ]));
    });

    body.append(el('div', { class: 'dialog-actions' }, [
        el('button', { type: 'button', text: 'Cancelar', onClick: () => dialog.close() }),
        submitBtn
    ]));

    dialog.append(body);
    dialog.addEventListener('close', () => dialog.remove());
    document.body.append(dialog);
    dialog.showModal();
    inputs.values().next().value?.focus();
    return dialog;
}

export function confirmDialog({ title, message, confirmLabel = 'Confirmar', onConfirm }) {
    const dialog = el('dialog');
    const confirmBtn = el('button', {
        class: 'btn-danger',
        text: confirmLabel,
        onClick: async () => {
            confirmBtn.disabled = true;
            try {
                await onConfirm();
                dialog.close();
            } catch (error) {
                reportError(error);
                dialog.close();
            }
        }
    });

    dialog.append(el('div', { class: 'dialog-body' }, [
        el('h2', { text: title }),
        el('p', { text: message, style: 'color: var(--text-secondary); font-size: .9rem; margin: 0;' }),
        el('div', { class: 'dialog-actions' }, [
            el('button', { type: 'button', text: 'Cancelar', onClick: () => dialog.close() }),
            confirmBtn
        ])
    ]));

    dialog.addEventListener('close', () => dialog.remove());
    document.body.append(dialog);
    dialog.showModal();
}

export function pager({ page, totalPages, totalElements, onChange }) {
    if (totalPages <= 1) return null;
    return el('div', { class: 'pager' }, [
        el('span', { text: `${totalElements} registro(s)` }),
        el('button', {
            class: 'btn-sm', text: 'Anterior', disabled: page <= 0,
            onClick: () => onChange(page - 1)
        }),
        el('span', { text: `${page + 1} / ${totalPages}` }),
        el('button', {
            class: 'btn-sm', text: 'Próxima', disabled: page >= totalPages - 1,
            onClick: () => onChange(page + 1)
        })
    ]);
}

export function pageHead(title, subtitle, actions = []) {
    return el('div', { class: 'page-head' }, [
        el('div', {}, [
            el('h1', { text: title }),
            subtitle ? el('p', { text: subtitle }) : null
        ]),
        el('div', { style: 'display:flex; gap:.5rem; flex-wrap:wrap;' }, actions)
    ]);
}

/** Seletor de mês/ano usado pelo dashboard e pelos orçamentos. */
export function monthPicker({ month, year, onChange }) {
    const monthSelect = el('select', {
        onChange: () => onChange(Number(monthSelect.value), Number(yearInput.value))
    }, MONTHS.map((name, index) => el('option', {
        value: index + 1,
        text: name.charAt(0).toUpperCase() + name.slice(1),
        selected: index + 1 === month
    })));

    const yearInput = el('input', {
        type: 'number', min: '2000', max: '2100', value: year,
        onChange: () => onChange(Number(monthSelect.value), Number(yearInput.value))
    });

    return el('div', { class: 'filters' }, [
        el('div', { class: 'field' }, [el('label', { text: 'Mês' }), monthSelect]),
        el('div', { class: 'field', style: 'max-width:110px' }, [el('label', { text: 'Ano' }), yearInput])
    ]);
}

/**
 * Marca a tabela pra virar lista de cartões no celular: cada célula recebe o
 * título da coluna em data-label, que o CSS mostra no lugar do cabeçalho.
 */
export function stackable(wrap) {
    const labels = [...wrap.querySelectorAll('thead th')].map((th) => th.textContent);
    wrap.querySelectorAll('tbody tr').forEach((tr) => {
        [...tr.children].forEach((td, index) => {
            if (labels[index]) td.dataset.label = labels[index];
        });
    });
    wrap.classList.add('table-stack');
    return wrap;
}
