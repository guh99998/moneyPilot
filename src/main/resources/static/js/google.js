import { api } from './api.js';
import { el } from './ui.js';

const SCRIPT_URL = 'https://accounts.google.com/gsi/client';
const BUTTON_MAX_WIDTH = 400;

let scriptLoading = null;
let clientId = '';

function loadScript() {
    if (globalThis.google?.accounts?.id) return Promise.resolve();
    if (!scriptLoading) {
        scriptLoading = new Promise((resolve, reject) => {
            const script = el('script', { src: SCRIPT_URL, async: true, defer: true });
            script.addEventListener('load', () => resolve());
            script.addEventListener('error', () => {
                scriptLoading = null;
                reject(new Error('Google script blocked'));
            });
            document.head ? document.head.append(script) : document.body.append(script);
        });
    }
    return scriptLoading;
}

/** O client ID do servidor; vazio = recurso desligado. Só um ID de verdade fica guardado. */
async function loadClientId() {
    if (!clientId) {
        const config = await api.googleConfig();
        clientId = config?.clientId || '';
    }
    return clientId;
}

/** Mensagem em português para um erro de uma chamada que usa o Google. */
export function googleErrorMessage(error) {
    if (error?.status === 429 || error?.status === 0) return error.message;
    if (error?.status === 409) {
        const message = String(error.message || '');
        if (message.includes('another Google')) {
            return 'Este e-mail pertence a uma conta vinculada a outra conta Google. Entre com essa conta Google ou com a senha.';
        }
        if (message.includes('another account')) return 'Essa conta Google já está vinculada a outra conta.';
        if (message.includes('already linked')) return 'Esta conta já está vinculada a um Google. Desvincule antes.';
        if (message.includes('password')) return 'Crie uma senha antes de desvincular o Google.';
        return 'Não foi possível concluir. Tente de novo.';
    }
    if (error?.status === 401) return 'Não foi possível entrar com o Google. Tente de novo.';
    return 'Não foi possível continuar com o Google. Tente de novo.';
}

/**
 * O botão oficial do Google. Devolve um contêiner que nasce escondido e só aparece quando há client ID e o
 * script do Google carregou; sem isso (recurso desligado, script bloqueado, sem rede) continua escondido e
 * o login por senha segue como está. `onCredential` recebe o ID token.
 *
 * `text`: 'signin_with' | 'signup_with' | 'continue_with'. `separator`: mostra o "ou" acima do botão.
 */
export function googleButton({ text = 'signin_with', separator = false, onCredential }) {
    const slot = el('div', { class: 'google-slot' });
    const block = el('div', { class: 'google-block', hidden: true }, [
        separator ? el('p', { class: 'auth-or', 'aria-hidden': 'true' }, [el('span', { text: 'ou' })]) : null,
        slot
    ]);

    (async () => {
        try {
            const id = await loadClientId();
            if (!id) return;
            await loadScript();
            globalThis.google.accounts.id.initialize({
                client_id: id,
                callback: (response) => onCredential(response.credential)
            });
            // Aparece antes de desenhar: o botão mede a largura do contêiner.
            block.hidden = false;
            globalThis.google.accounts.id.renderButton(slot, {
                type: 'standard',
                theme: 'outline',
                size: 'large',
                text,
                shape: 'rectangular',
                logo_alignment: 'left',
                locale: 'pt-BR',
                width: Math.min(slot.clientWidth || 320, BUTTON_MAX_WIDTH)
            });
        } catch {
            block.hidden = true;
        }
    })();

    return block;
}
