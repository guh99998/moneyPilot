/** Mapa tela → tutorial da central de ajuda (spec 021). Tela sem tutorial cai no índice. */
export const HELP_PAGES = {
    dashboard: 'visao-geral.html',
    transactions: 'lancamentos.html',
    bills: 'a-pagar-e-receber.html',
    forecast: 'previsao.html',
    accounts: 'contas.html',
    categories: 'categorias.html',
    budgets: 'orcamentos.html',
    profile: 'perfil-e-seguranca.html',
    suporte: 'suporte.html',
    'usando-com-ia': 'usando-com-ia.html'
};

export const HELP_LABEL = 'Ajuda sobre esta tela (abre em nova aba)';

/** Rota do hash (`#/bills?x=1` → `bills`). */
export function routeOf(hash) {
    return String(hash || '').replace(/^#\/?/, '').split('?')[0];
}

export function helpUrl(routePath) {
    const page = Object.hasOwn(HELP_PAGES, routePath) ? HELP_PAGES[routePath] : null;
    return page ? `/ajuda/${page}` : '/ajuda/';
}

/** O "?" ao lado do título: um link, porque navega. O símbolo é decorativo; o nome vem do aria-label. */
export function helpLink(routePath, createElement) {
    return createElement('a', {
        class: 'help-button',
        href: helpUrl(routePath),
        target: '_blank',
        rel: 'noopener',
        title: HELP_LABEL,
        'aria-label': HELP_LABEL
    }, [createElement('span', { 'aria-hidden': 'true', text: '?' })]);
}
