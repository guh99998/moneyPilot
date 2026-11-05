import { api } from '../api.js';
import { el, reportError } from '../ui.js';

// Cartão de primeiros passos do painel (spec 013): só aparece em conta nova.
const STEP_TEXT = {
    ACCOUNT: { label: 'Crie sua primeira conta bancária', href: '#/accounts' },
    TRANSACTION: { label: 'Lance sua primeira despesa ou receita', href: '#/transactions' },
    BUDGET: { label: 'Crie um orçamento', href: '#/budgets' }
};

/**
 * Busca o cartão. Devolve null quando não há o que mostrar. Falha ao buscar nunca quebra o painel:
 * o cartão é um extra, então um erro só significa "sem cartão".
 */
export async function loadOnboarding() {
    try {
        const onboarding = await api.onboarding.get();
        return onboarding && onboarding.visible ? onboarding : null;
    } catch {
        return null;
    }
}

/** O estado vai escrito ("Feito" / "A fazer"), nunca só em cor. */
function step(item) {
    const text = STEP_TEXT[item.key];
    if (!text) return null;

    return el('li', { class: `onboarding-step ${item.done ? 'is-done' : ''}` }, [
        el('span', { class: 'onboarding-state', text: item.done ? 'Feito' : 'A fazer' }),
        el('a', { href: text.href, text: text.label })
    ]);
}

/** `onDismissed` é chamado depois que a API confirmou a dispensa; o painel remove o cartão. */
export function onboardingCard(onboarding, onDismissed) {
    async function dismiss() {
        try {
            await api.onboarding.dismiss();
            onDismissed();
        } catch (error) {
            reportError(error);
        }
    }

    return el('section', { class: 'card onboarding', 'aria-labelledby': 'onboarding-title' }, [
        el('div', { class: 'card-head' }, [
            el('h2', { id: 'onboarding-title', text: 'Primeiros passos' }),
            el('button', {
                type: 'button',
                class: 'btn-ghost btn-sm',
                text: 'Dispensar',
                'aria-label': 'Dispensar os primeiros passos',
                onClick: dismiss
            })
        ]),
        el('p', { class: 'onboarding-intro', text: 'Três passos para o moneyPilot ficar útil logo de cara.' }),
        el('ol', { class: 'onboarding-steps' }, (onboarding.steps || []).map(step))
    ]);
}
