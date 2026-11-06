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
        ]),
        el('ul', { class: 'availability-help' }, [
            el('li', {}, [el('b', { text: 'Marcada: ' }), 'o saldo da conta entra no Saldo disponível da Visão geral e é o ponto de partida da previsão.']),
            el('li', {}, [el('b', { text: 'Desmarcada: ' }), 'o saldo vira Guardado e fica fora do disponível e da previsão.'])
        ]),
        el('p', { class: 'sub', text: 'Lançamentos, extrato, relatórios e orçamento não mudam.' }),
        body
    ]);
}

const STATUS = {
    true: 'Conta no saldo disponível e na previsão',
    false: 'Guardado: fora do saldo disponível e da previsão'
};

function row(account, errorBox) {
    const id = `availability-${account.id}`;
    const status = el('span', { class: 'availability-status', id: `${id}-status`, text: STATUS[account.countsAsAvailable] });
    const input = el('input', { type: 'checkbox', id, name: `countsAsAvailable-${account.id}`, autocomplete: 'off', 'aria-describedby': `${id}-status` });
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
            status.textContent = STATUS[saved.countsAsAvailable];
            toast(wanted ? `${account.name} conta no saldo disponível.` : `${account.name} saiu do saldo disponível.`, 'success');
        } catch (error) {
            input.checked = account.countsAsAvailable;
            status.textContent = STATUS[account.countsAsAvailable];
            errorBox.textContent = error?.message || 'Não foi possível salvar.';
            errorBox.hidden = false;
            toast(error?.message || 'Não foi possível salvar.', 'error');
        } finally {
            input.disabled = false;
        }
    });

    return el('li', {}, el('label', { for: id }, [
        input,
        el('span', { class: 'availability-text' }, [
            el('span', {}, [
                el('span', { class: 'availability-name', text: account.name }),
                ' ',
                el('span', { class: 'sub', text: TYPE_LABEL[account.type] || account.type })
            ]),
            status
        ])
    ]));
}
