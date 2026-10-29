import { session } from './api.js';

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

function start() {
    const target = legacyAppUrl(window.location.hash);
    if (target) {
        window.location.replace(target);
        return;
    }
    const nav = document.getElementById('site-session');
    if (nav) renderSession(nav, session.active);
}

if (typeof document !== 'undefined' && document.body?.dataset?.page === 'site') {
    start();
}
