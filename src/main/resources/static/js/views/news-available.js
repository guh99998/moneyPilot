import { el } from '../ui.js';

const DISMISSED_KEY = 'moneypilot.news.available-balance';

function readDismissed() {
    try {
        return localStorage.getItem(DISMISSED_KEY) === '1';
    } catch {
        return false;
    }
}

function rememberDismissed() {
    try {
        localStorage.setItem(DISMISSED_KEY, '1');
    } catch {
        // Sem armazenamento, o aviso volta na próxima visita; nada quebra.
    }
}

/**
 * Aviso de Novidades da spec 015: o saldo em destaque passou a ser o saldo disponível.
 * Aparece para quem já tem conta e ainda não dispensou; conta sem nenhuma conta ainda não viu o saldo antigo.
 * A dispensa fica neste aparelho; a spec 027 generaliza o aviso e passa a guardá-la no servidor.
 */
export function availableNewsCard(accountCount, onDismiss) {
    if (!accountCount || readDismissed()) return null;

    const dismiss = el('button', {
        type: 'button',
        class: 'btn',
        text: 'Entendi',
        onClick: () => {
            rememberDismissed();
            onDismiss?.();
        }
    });

    return el('section', { class: 'card news-card', 'aria-labelledby': 'news-available-title' }, [
        el('div', { class: 'card-head' }, [
            el('h2', { id: 'news-available-title', text: 'Novidade: saldo disponível' })
        ]),
        el('p', {
            text: 'O saldo em destaque agora mostra só o dinheiro disponível. Contas de investimento ficam de fora, '
                + 'e o valor delas aparece como "Guardado". Por isso o seu saldo pode ter mudado.'
        }),
        el('ol', { class: 'news-steps' }, [
            el('li', {}, ['Abra o ', el('a', { href: '#/profile', text: 'Perfil' }), ' e vá para a aba "Preferências".']),
            el('li', { text: 'Marque as contas que contam no saldo disponível e desmarque as que guardam dinheiro.' }),
            el('li', { text: 'Volte à Visão geral: o saldo disponível e o valor guardado já estão atualizados.' })
        ]),
        dismiss
    ]);
}
