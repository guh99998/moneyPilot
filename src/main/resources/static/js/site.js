import { session } from './api.js';
import { assistantPrompt } from './views/ai-guide.js';

/**
 * Links antigos (favoritos, e-mails já enviados) apontavam para `/#/rota`. O app agora vive em `/app/`,
 * então um hash de rota na raiz segue para `/app/` com o hash inteiro, inclusive a query.
 */
export function legacyAppUrl(hash) {
    return typeof hash === 'string' && hash.startsWith('#/') ? '/app/' + hash : null;
}

/** Topo do site: "Abrir o app" com sessão; "Entrar" e "Criar conta" sem. Os dois levam a `/app/`. */
export function sessionLinks(hasSession) {
    return hasSession
        ? [{ label: 'Abrir o app', primary: true }]
        : [{ label: 'Entrar', primary: false }, { label: 'Criar conta', primary: true }];
}

function renderSession(nav, hasSession) {
    nav.replaceChildren(...sessionLinks(hasSession).map(({ label, primary }) => {
        const link = document.createElement('a');
        link.className = primary ? 'btn btn-primary' : 'btn';
        link.href = '/app/';
        link.textContent = label;
        return link;
    }));
}

/** Na ajuda "Usando com IA": troca o endereço de exemplo pelo do site e liga o "Copiar texto". */
function setupAssistantPrompt() {
    const box = document.getElementById('assistant-prompt');
    if (!box) return;
    const prompt = assistantPrompt(window.location.origin);
    box.value = prompt;
    document.querySelectorAll('[data-origin]').forEach((node) => { node.textContent = window.location.origin; });

    const status = document.getElementById('copy-status');
    const button = document.getElementById('copy-prompt');
    if (!button) return;
    button.hidden = false;
    button.addEventListener('click', async () => {
        try {
            await navigator.clipboard.writeText(prompt);
            if (status) status.textContent = 'Texto copiado.';
        } catch {
            // Sem permissão para a área de transferência: deixa o texto selecionado para o Ctrl+C.
            box.focus();
            box.select();
            if (status) status.textContent = 'Não consegui copiar sozinho. O texto está selecionado: use Ctrl+C.';
        }
    });
}

function start() {
    const page = document.body.dataset.page;
    if (page === 'site') {
        const target = legacyAppUrl(window.location.hash);
        if (target) {
            window.location.replace(target);
            return;
        }
    }
    const nav = document.getElementById('site-session');
    if (nav) renderSession(nav, session.active);
    if (page === 'help') setupAssistantPrompt();
}

const page = typeof document !== 'undefined' ? document.body?.dataset?.page : null;
if (page === 'site' || page === 'help') {
    start();
}
