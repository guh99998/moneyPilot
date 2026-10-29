import { api, session } from '../api.js';
import { el, pageHead, emptyState, reportError, svgIcon } from '../ui.js';

const COFFEE_ICON = svgIcon('<path d="M6 2v2M10 2v2M14 2v2"/><path d="M16 8h1a4 4 0 0 1 0 8h-1"/>'
    + '<path d="M3 8h13v9a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4z"/>', 28);

/**
 * Sem login (quem chega pelo site), a tela sai do layout do app: logo, o conteúdo e um link para voltar.
 * O caminho do logo é absoluto porque o app vive em `/app/`.
 */
function publicPage(children) {
    return el('div', { class: 'auth-wrap' }, [
        el('div', { class: 'card auth-card contribution-card' }, [
            el('img', { class: 'auth-logo', src: '/image/money-pilot-logo.png', alt: 'moneyPilot' }),
            ...children,
            el('p', { class: 'auth-switch' }, [el('a', { href: '/', text: 'Voltar para o moneyPilot' })])
        ])
    ]);
}

/**
 * "Apoie o projeto": a chave Pix e o QR code de quem quiser ajudar a manter o moneyPilot. O pagamento acontece
 * fora do app, que não registra nada. A primeira coisa na tela é que o projeto é gratuito. A tela vale com ou
 * sem login.
 */
export async function renderContribution() {
    const standalone = !session.active;
    const head = pageHead('Apoie o projeto', 'Pagar um cafezinho?');

    let pix;
    try {
        pix = await api.contribution.pix();
    } catch (error) {
        reportError(error);
        const message = error?.status === 404
            ? 'O apoio não está disponível agora.'
            : (error?.message || 'Não foi possível carregar a chave agora.');
        return standalone
            ? publicPage([el('h1', { text: 'Apoie o projeto' }), emptyState(message)])
            : el('div', {}, [head, emptyState(message)]);
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

    const content = [
        el('h2', { class: 'contribution-title' }, [
            el('span', { class: 'contribution-icon', 'aria-hidden': 'true', html: COFFEE_ICON }),
            'O moneyPilot é um projeto gratuito, e sempre vai ser.'
        ]),
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
    ];

    if (standalone) return publicPage([el('h1', { text: 'Apoie o projeto' }), ...content]);
    return el('div', {}, [head, el('div', { class: 'card contribution-card' }, content)]);
}
