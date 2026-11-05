import { api } from '../api.js';
import { el } from '../ui.js';

// Pop-up de primeiro contato (spec 027): os três passos da 013, num <dialog> nativo.
// O showModal prende o foco, trata o Esc e devolve o foco a quem estava com ele ao fechar.
const STEP_TEXT = {
    ACCOUNT: {
        title: 'Crie sua primeira conta bancária',
        text: 'A conta é onde o seu dinheiro fica. Cadastre a carteira ou o banco que você mais usa e informe o saldo de hoje.',
        link: 'Ir para Contas',
        href: '#/accounts'
    },
    TRANSACTION: {
        title: 'Lance sua primeira despesa ou receita',
        text: 'Cada compra ou entrada de dinheiro vira um lançamento. Comece pelo último gasto de que você se lembra.',
        link: 'Ir para Lançamentos',
        href: '#/transactions'
    },
    BUDGET: {
        title: 'Crie um orçamento',
        text: 'Defina quanto você quer gastar por mês em uma categoria e acompanhe o quanto já usou.',
        link: 'Ir para Orçamentos',
        href: '#/budgets'
    }
};

const REOPEN_HASH = '#/dashboard?primeiros-passos';

/** O primeiro passo pendente; sem pendência (reabertura pela ajuda), o passo 1. */
function firstPending(steps) {
    const index = steps.findIndex((item) => !item.done);
    return index === -1 ? 0 : index;
}

/** Abre o pop-up com o estado devolvido por `GET /onboarding`. */
export function openOnboarding(state) {
    const steps = (state.steps || []).filter((item) => STEP_TEXT[item.key]);
    if (!steps.length) return;

    let index = firstPending(steps);
    const dialog = el('dialog', { 'aria-labelledby': 'onboarding-title' });
    const body = el('div', { class: 'dialog-body onboarding-dialog' });
    dialog.append(body);

    const errorBox = el('p', { class: 'dialog-error', role: 'alert', hidden: true });

    function checkboxRow() {
        // Sem efeito (conta antiga, cartão dispensado ou passos concluídos), a caixinha não aparece.
        if (!state.eligible) return null;
        const id = `onboarding-show-${Math.random().toString(36).slice(2, 7)}`;
        const box = el('input', { type: 'checkbox', id });
        box.checked = state.showOnStart !== false;
        box.addEventListener('change', async () => {
            const wanted = box.checked;
            box.disabled = true;
            errorBox.hidden = true;
            try {
                await api.onboarding.savePreferences(wanted);
                state.showOnStart = wanted;
            } catch (error) {
                box.checked = !wanted;
                errorBox.textContent = error?.message || 'Não foi possível salvar.';
                errorBox.hidden = false;
            } finally {
                box.disabled = false;
            }
        });
        return el('div', { class: 'onboarding-pref' }, [box, el('label', { for: id, text: 'Mostrar ao iniciar' })]);
    }

    const preference = checkboxRow();

    function show(next, moveFocus) {
        index = next;
        const item = steps[index];
        const text = STEP_TEXT[item.key];
        const heading = el('h2', {
            id: 'onboarding-title',
            tabindex: '-1',
            text: `Passo ${index + 1} de ${steps.length}`
        });

        body.replaceChildren(...[
            heading,
            el('p', { class: `onboarding-state ${item.done ? 'is-done' : ''}`, text: item.done ? 'Feito' : 'A fazer' }),
            el('h3', { class: 'onboarding-step-title', text: text.title }),
            el('p', { class: 'dialog-lead', text: text.text }),
            el('a', { class: 'onboarding-link', href: text.href, text: text.link, onClick: () => dialog.close() }),
            errorBox,
            preference,
            el('div', { class: 'dialog-actions dialog-actions-wrap onboarding-actions' }, [
                el('button', { type: 'button', class: 'btn-ghost', text: 'Dispensar', onClick: () => dialog.close() }),
                index > 0 ? el('button', { type: 'button', text: 'Voltar', onClick: () => show(index - 1, true) }) : null,
                index < steps.length - 1
                    ? el('button', { type: 'button', class: 'btn-primary', text: 'Avançar', onClick: () => show(index + 1, true) })
                    : null
            ].filter(Boolean))
        ].filter(Boolean));

        // O botão usado pode sumir (Voltar no passo 1, Avançar no 3): o foco vai para o título.
        if (moveFocus) heading.focus();
        return heading;
    }

    dialog.addEventListener('close', () => dialog.remove());
    document.body.append(dialog);
    show(index, false);
    dialog.showModal();
    // O título é o primeiro foco, para o leitor de tela anunciar o passo e não um botão.
    body.children[0].focus();
}

let loginPending = false;

/** Chamado pelo callback do login (senha ou Google): a próxima renderização pode abrir o pop-up. */
export function markLogin() {
    loginPending = true;
}

/**
 * Depois de desenhar a rota. Abre no login ou pelo link da ajuda; F5, aba nova e troca de rota não abrem.
 * A flag é consumida de forma síncrona: login e `hashchange` disparam duas renderizações seguidas.
 * Falha ao buscar não mostra pop-up nem erro: ele é um extra.
 */
export async function maybeOpenOnboarding() {
    const reopen = window.location.hash === REOPEN_HASH;
    const login = loginPending;
    loginPending = false;
    if (reopen) window.history.replaceState(null, '', '#/dashboard');
    if (!reopen && !login) return;

    let state;
    try {
        state = await api.onboarding.get();
    } catch {
        return;
    }
    if (reopen || state?.visible) openOnboarding(state);
}
