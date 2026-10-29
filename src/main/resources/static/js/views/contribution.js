import { api } from '../api.js';
import { el, pageHead, emptyState, reportError } from '../ui.js';

/**
 * "Apoie o projeto": a chave Pix e o QR code de quem quiser ajudar a manter o moneyPilot. O pagamento acontece
 * fora do app, que não registra nada. A primeira coisa na tela é que o projeto é gratuito.
 */
export async function renderContribution() {
    const head = pageHead('Apoie o projeto', 'Pagar um cafezinho?');

    let pix;
    try {
        pix = await api.contribution.pix();
    } catch (error) {
        reportError(error);
        return el('div', {}, [head, emptyState(error?.message || 'Não foi possível carregar a chave agora.')]);
    }

    const keyId = 'f-pix-key';
    const keyInput = el('input', {
        id: keyId,
        name: 'pixKey',
        type: 'text',
        readonly: true,
        autocomplete: 'off',
        spellcheck: 'false',
        value: pix.key
    });
    // Região de status já no DOM: o texto que muda dentro dela é anunciado por leitores de tela.
    const status = el('p', { class: 'meter-foot', role: 'status', 'aria-live': 'polite' });

    async function copy() {
        try {
            await navigator.clipboard.writeText(pix.key);
            status.textContent = 'Chave copiada.';
        } catch {
            keyInput.select?.();
            status.textContent = 'Não foi possível copiar. Selecione a chave e copie.';
        }
    }

    return el('div', {}, [
        head,
        el('div', { class: 'card contribution-card' }, [
            el('h2', { text: 'O moneyPilot é um projeto gratuito, e sempre vai ser.' }),
            el('p', {
                text: 'Apoiar é só um agradecimento, totalmente opcional. Se ele te ajuda e você quiser pagar um cafezinho para '
                    + 'ajudar a manter o projeto, qualquer valor é bem-vindo. Nada no app muda para quem apoia.'
            }),
            el('div', { class: 'field' }, [
                el('label', { for: keyId, text: 'Chave Pix (aleatória)' }),
                el('div', { class: 'contribution-key' }, [
                    keyInput,
                    el('button', { class: 'btn-primary', type: 'button', text: 'Copiar chave', onClick: copy })
                ]),
                status
            ]),
            pix.qrImage
                ? el('img', {
                    class: 'contribution-qr',
                    src: pix.qrImage,
                    alt: 'QR code da chave Pix',
                    width: '220',
                    height: '220'
                })
                : null
        ])
    ]);
}
