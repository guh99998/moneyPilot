import { el } from '../ui.js';

/**
 * Abas do Perfil (padrão WAI-ARIA de tabs com ativação automática): setas, Home e End movem a seleção.
 * Todos os painéis ficam no DOM e os inativos ficam `hidden`, assim os formulários e o foco seguem como antes.
 *
 * `items`: [{ id, label, content }]. `selected` é o id da aba aberta; `onSelect(id)` avisa a tela.
 */
export function tabs({ label, items, selected, onSelect }) {
    const current = items.some((item) => item.id === selected) ? selected : items[0].id;
    const tabButtons = new Map();
    const panels = new Map();

    function select(id, moveFocus) {
        items.forEach((item) => {
            const active = item.id === id;
            const tab = tabButtons.get(item.id);
            tab.setAttribute('aria-selected', String(active));
            tab.setAttribute('tabindex', active ? '0' : '-1');
            panels.get(item.id).hidden = !active;
        });
        if (moveFocus) tabButtons.get(id).focus();
        onSelect?.(id);
    }

    function onKeyDown(event) {
        const index = items.findIndex((item) => tabButtons.get(item.id) === event.target);
        if (index < 0) return;
        const last = items.length - 1;
        const target = {
            ArrowRight: index === last ? 0 : index + 1,
            ArrowLeft: index === 0 ? last : index - 1,
            Home: 0,
            End: last
        }[event.key];
        if (target === undefined) return;
        event.preventDefault();
        select(items[target].id, true);
    }

    items.forEach((item) => {
        const active = item.id === current;
        tabButtons.set(item.id, el('button', {
            type: 'button',
            role: 'tab',
            class: 'tab',
            id: `profile-tab-${item.id}`,
            'aria-selected': String(active),
            'aria-controls': `profile-panel-${item.id}`,
            tabindex: active ? '0' : '-1',
            text: item.label,
            onClick: () => select(item.id, false),
            onKeyDown
        }));
        panels.set(item.id, el('div', {
            role: 'tabpanel',
            class: 'tab-panel',
            id: `profile-panel-${item.id}`,
            'aria-labelledby': `profile-tab-${item.id}`,
            tabindex: '0',
            hidden: !active
        }, item.content));
    });

    return el('div', { class: 'tabs' }, [
        el('div', { role: 'tablist', class: 'tablist', 'aria-label': label }, [...tabButtons.values()]),
        ...panels.values()
    ]);
}
