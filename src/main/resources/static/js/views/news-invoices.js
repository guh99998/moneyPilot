import { el } from '../ui.js';

const DISMISSED_KEY = 'moneypilot.news.card-invoices';

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
 * Aviso de Novidades da spec 016: o cartão de crédito ganhou fatura.
 * Aparece para quem já tem cartão e ainda não dispensou. A dispensa fica neste aparelho,
 * como no aviso do saldo disponível; a spec 027 generaliza e passa a guardá-la no servidor.
 */
export function invoiceNewsCard(cardCount, onDismiss) {
    if (!cardCount || readDismissed()) return null;

    const dismiss = el('button', {
        type: 'button',
        class: 'btn',
        text: 'Entendi',
        onClick: () => {
            rememberDismissed();
            onDismiss?.();
        }
    });

    return el('section', { class: 'card news-card', 'aria-labelledby': 'news-invoices-title' }, [
        el('div', { class: 'card-head' }, [
            el('h2', { id: 'news-invoices-title', text: 'Novidade: fatura do cartão' })
        ]),
        el('p', {
            text: 'Agora o moneyPilot calcula a fatura do cartão de crédito, mostra quando ela vence e leva o vencimento '
                + 'para a previsão. O seu cartão só muda quando você informar o fechamento e o vencimento.'
        }),
        el('ol', { class: 'news-steps' }, [
            el('li', {}, ['Abra ', el('a', { href: '#/accounts', text: 'Contas' }), ', edite o cartão e informe o dia do fechamento e o do vencimento.']),
            el('li', {}, [
                'Veja a fatura em ', el('a', { href: '#/invoices', text: 'Faturas' }),
                '. O cartão sai do saldo disponível, e a fatura aparece também em A pagar e receber, na Visão geral e na previsão. Compras de antes de você informar os dias não entram.'
            ]),
            el('li', { text: 'Pague pela própria tela de Faturas: o pagamento é uma transferência para o cartão, não uma nova despesa.' })
        ]),
        el('p', {
            text: 'Streaming ou academia no cartão? Use Assinatura mensal em Faturas: a cobrança vira despesa sozinha '
                + 'na data, e a próxima já aparece como prevista na fatura e na previsão.'
        }),
        dismiss
    ]);
}
