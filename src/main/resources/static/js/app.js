import { session } from './api.js';
import { el, clear, toast } from './ui.js';
import { renderAuth } from './views/auth.js';
import { renderDashboard } from './views/dashboard.js';
import { renderTransactions } from './views/transactions.js';
import { renderAccounts } from './views/accounts.js';
import { renderCategories } from './views/categories.js';
import { renderBudgets } from './views/budgets.js';

const ROUTES = [
    { path: 'dashboard', label: 'Visão geral', render: renderDashboard },
    { path: 'transactions', label: 'Lançamentos', render: renderTransactions },
    { path: 'accounts', label: 'Contas', render: renderAccounts },
    { path: 'categories', label: 'Categorias', render: renderCategories },
    { path: 'budgets', label: 'Orçamentos', render: renderBudgets }
];

const THEME_KEY = 'moneypilot.theme';

function applyStoredTheme() {
    const stored = localStorage.getItem(THEME_KEY);
    if (stored) document.documentElement.dataset.theme = stored;
}

function toggleTheme() {
    const current = document.documentElement.dataset.theme
        || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
    const next = current === 'dark' ? 'light' : 'dark';
    document.documentElement.dataset.theme = next;
    localStorage.setItem(THEME_KEY, next);
}

function currentRoute() {
    const path = window.location.hash.replace(/^#\/?/, '');
    return ROUTES.find((route) => route.path === path) || ROUTES[0];
}

function buildSidebar(active) {
    // No celular a sidebar vira uma barra no topo e o menu abre por este botão.
    const menuToggle = el('button', {
        class: 'btn-ghost menu-toggle',
        type: 'button',
        'aria-label': 'Abrir menu',
        'aria-expanded': 'false',
        'aria-controls': 'sidebar-menu',
        html: '<span></span><span></span><span></span>',
        onClick: () => {
            const open = sidebar.classList.toggle('open');
            menuToggle.setAttribute('aria-expanded', String(open));
            menuToggle.setAttribute('aria-label', open ? 'Fechar menu' : 'Abrir menu');
        }
    });

    const sidebar = el('aside', { class: 'sidebar' }, [
        el('div', { class: 'sidebar-top' }, [
            el('div', { class: 'brand' }, [
                el('span', { class: 'brand-mark' }, el('img', { src: 'image/money-pilot-logo.png', alt: '' })),
                el('span', { text: 'moneyPilot' })
            ]),
            menuToggle
        ]),
        el('div', { class: 'sidebar-body', id: 'sidebar-menu' }, [
            el('nav', { class: 'nav' }, ROUTES.map((route) => el('a', {
                href: `#/${route.path}`,
                class: route.path === active.path ? 'active' : '',
                text: route.label,
                // Tocar na página atual não dispara hashchange, então fecha à mão.
                onClick: () => sidebar.classList.remove('open')
            }))),
            el('div', { class: 'sidebar-foot' }, [
                el('span', { class: 'user-chip', text: session.email || '' }),
                el('button', { class: 'btn-ghost btn-sm', text: 'Tema', onClick: toggleTheme }),
                el('button', {
                    class: 'btn-ghost btn-sm',
                    text: 'Sair',
                    onClick: () => {
                        session.clear();
                        window.location.hash = '';
                        render();
                    }
                })
            ])
        ])
    ]);

    return sidebar;
}

async function render() {
    const root = document.getElementById('app');
    root.className = '';
    clear(root);

    if (!session.active) {
        root.append(renderAuth(() => {
            window.location.hash = '#/dashboard';
            render();
        }));
        return;
    }

    const route = currentRoute();
    const main = el('main', { class: 'main' });
    root.append(el('div', { class: 'shell' }, [buildSidebar(route), main]));
    main.append(el('div', { class: 'empty', text: 'Carregando…' }));

    try {
        const view = await route.render();
        clear(main).append(view);
    } catch (error) {
        clear(main).append(el('div', { class: 'empty', text: error?.message || 'Não foi possível carregar a página.' }));
    }
}

window.addEventListener('hashchange', render);
window.addEventListener('session:expired', () => {
    toast('Sua sessão expirou. Entre novamente.', 'error');
    window.location.hash = '';
    render();
});

applyStoredTheme();
render();
