import { api, fetchAll } from '../api.js';
import { el, toast } from '../ui.js';
import { TYPE_LABEL } from './accounts.js';

/**
 * Cartão "Saldo disponível" (spec 015): uma caixinha por conta decide se ela conta no saldo disponível.
 * A caixinha só fica no novo estado depois que o servidor confirma; em erro ela volta ao estado real.
 */
export async function availabilityCard() {
    let accounts;
    try {
        accounts = await fetchAll(api.accounts.list);
    } catch {
        return card(el('p', { class: 'form-error', role: 'alert', text: 'Não foi possível carregar as contas. Atualize a página.' }));
    }
    if (!accounts.length) {
        return card(el('p', { class: 'muted', text: 'Cadastre uma conta para escolher o que conta no saldo disponível.' }));
    }

    const errorBox = el('p', { class: 'form-error', role: 'alert', hidden: true });
    const rows = accounts.map((account) => row(account, errorBox));
    return card(el('div', {}, [errorBox, el('ul', { class: 'availability-list' }, rows)]));
}

function card(body) {
    return el('div', { class: 'card profile-card' }, [
        el('div', { class: 'card-head' }, [
            el('h2', { text: 'Saldo disponível' }),
            el('span', {
                class: 'sub',
                text: 'Marque as contas que contam no saldo disponível. As desmarcadas aparecem como guardado.'
            })
        ]),
        body
    ]);
}

function row(account, errorBox) {
    const id = `availability-${account.id}`;
    const input = el('input', { type: 'checkbox', id });
    input.checked = account.countsAsAvailable;

    input.addEventListener('change', async () => {
        const wanted = input.checked;
        input.disabled = true;
        errorBox.hidden = true;
        try {
            const saved = await api.accounts.update(account.id, {
                name: account.name,
                accountType: account.type,
                initialBalance: account.initialBalance,
                countsAsAvailable: wanted
            });
            account.countsAsAvailable = saved.countsAsAvailable;
            input.checked = saved.countsAsAvailable;
            toast(wanted ? `${account.name} conta no saldo disponível.` : `${account.name} saiu do saldo disponível.`, 'success');
        } catch (error) {
            input.checked = account.countsAsAvailable;
            errorBox.textContent = error?.message || 'Não foi possível salvar.';
            errorBox.hidden = false;
            toast(error?.message || 'Não foi possível salvar.', 'error');
        } finally {
            input.disabled = false;
        }
    });

    return el('li', {}, el('label', { for: id }, [
        input,
        el('span', { class: 'availability-name', text: account.name }),
        el('span', { class: 'sub', text: TYPE_LABEL[account.type] || account.type })
    ]));
}
