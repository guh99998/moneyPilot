import { api, session } from './api.js';
import { el, clear, toast, svgIcon, avatar } from './ui.js';
import { renderAuth } from './views/auth.js';
import { renderDashboard } from './views/dashboard.js';
import { renderTransactions } from './views/transactions.js';
import { renderBills, ensureRecurringBills } from './views/bills.js';
import { renderForecast } from './views/forecast.js';
import { renderAccounts } from './views/accounts.js';
import { renderCategories } from './views/categories.js';
import { renderBudgets } from './views/budgets.js';
import { renderProfile } from './views/profile.js';
import { renderConfirmEmail } from './views/confirm-email.js';
import { renderVerifyEmail } from './views/verify-email.js';
import { renderResetPassword } from './views/reset-password.js';
import { renderBell, refreshAlerts, resetAlerts } from './alerts.js';

const ROUTES = [
    { path: 'dashboard', label: 'Visão geral', render: renderDashboard },
    { path: 'transactions', label: 'Lançamentos', render: renderTransactions },
    { path: 'bills', label: 'A pagar e receber', render: renderBills },
    { path: 'forecast', label: 'Previsão mensal', render: renderForecast },
    { path: 'accounts', label: 'Contas', render: renderAccounts },
    { path: 'categories', label: 'Categorias', render: renderCategories },
    { path: 'budgets', label: 'Orçamentos', render: renderBudgets }
];

const NAV_ICONS = {
    dashboard: svgIcon('<rect x="3" y="3" width="7" height="9" rx="1"/><rect x="14" y="3" width="7" height="5" rx="1"/>'
        + '<rect x="14" y="12" width="7" height="9" rx="1"/><rect x="3" y="16" width="7" height="5" rx="1"/>'),
    transactions: svgIcon('<path d="M7 7h13M16 3l4 4-4 4"/><path d="M17 17H4M8 13l-4 4 4 4"/>'),
    bills: svgIcon('<path d="M6 3h12v18l-3-2-3 2-3-2-3 2z"/><path d="M9 8h6M9 12h6"/>'),
    forecast: svgIcon('<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/>'),
    accounts: svgIcon('<path d="M3 10l9-6 9 6"/><path d="M5 10v8M9.5 10v8M14.5 10v8M19 10v8M3 21h18"/>'),
    categories: svgIcon('<path d="M3 12V4h8l10 10-8 8z"/><circle cx="7.5" cy="8.5" r="1.2"/>'),
    budgets: svgIcon('<path d="M21 12a9 9 0 1 1-9-9v9z"/><path d="M15 3.5a9 9 0 0 1 5.5 5.5H15z"/>')
};

// Tela fora do menu: o acesso é pelo nome do usuário no rodapé.
const PROFILE_ROUTE = { path: 'profile', label: 'Perfil', render: renderProfile };

// O app tem só o tema claro; limpa a preferência que versões antigas gravavam.
localStorage.removeItem('moneypilot.theme');

function currentRoute() {
    const path = window.location.hash.replace(/^#\/?/, '');
    if (path === PROFILE_ROUTE.path) return PROFILE_ROUTE;
    return ROUTES.find((route) => route.path === path) || ROUTES[0];
}

/** Foto do Google ou, sem ela, as iniciais; o nome ao lado é quem identifica a conta para leitores de tela. */
function userAvatar() {
    return avatar({ url: session.avatar, name: session.name || session.email });
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
                el('span', {}, ['money', el('span', { class: 'pilot', text: 'Pilot' })])
            ]),
            el('div', { class: 'sidebar-actions' }, [renderBell(), menuToggle])
        ]),
        el('div', { class: 'sidebar-body', id: 'sidebar-menu' }, [
            el('nav', { class: 'nav' }, ROUTES.map((route) => el('a', {
                href: `#/${route.path}`,
                class: route.path === active.path ? 'active' : '',
                // Tocar na página atual não dispara hashchange, então fecha à mão.
                onClick: () => sidebar.classList.remove('open')
            }, [
                el('span', { class: 'nav-icon', 'aria-hidden': 'true', html: NAV_ICONS[route.path] }),
                el('span', { text: route.label })
            ]))),
            el('div', { class: 'sidebar-foot' }, [
                el('a', {
                    class: 'user-chip' + (active.path === PROFILE_ROUTE.path ? ' active' : ''),
                    href: '#/' + PROFILE_ROUTE.path,
                    title: session.email || 'Meu perfil',
                    'aria-label': 'Meu perfil',
                    // Tocar na página atual não dispara hashchange, então fecha à mão.
                    onClick: () => sidebar.classList.remove('open')
                }, [
                    el('span', { class: 'user-chip-avatar' }, userAvatar()),
                    el('span', { class: 'user-chip-name', text: session.name || session.email || '' })
                ]),
                el('button', {
                    class: 'btn-ghost btn-sm',
                    text: 'Sair',
                    onClick: async () => {
                        // Revoga o refresh token no servidor; se falhar, sai mesmo assim.
                        await api.logout().catch(() => {});
                        session.clear();
                        resetAlerts();
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
    // Sessões abertas antes do perfil existir (ou do login com Google) não têm o nome e a foto guardados:
    // busca uma vez. A espera vem antes de limpar a tela: duas renderizações concorrentes (login + hashchange)
    // montariam o menu duas vezes.
    if (session.active && (!session.name || session.avatar === null)) {
        await api.profile.get().then((profile) => {
            session.setName(profile.name);
            session.setAvatar(profile.avatarUrl);
        }).catch(() => {});
    }

    const root = document.getElementById('app');
    root.className = '';
    clear(root);

    // Rota pública: o link do e-mail confirma a troca mesmo sem sessão aberta.
    if (window.location.hash.startsWith('#/confirmar-email')) {
        root.append(renderConfirmEmail());
        return;
    }

    // Rota pública: quem ativa a conta ainda não tem sessão.
    if (window.location.hash.startsWith('#/verificar-email')) {
        root.append(renderVerifyEmail());
        return;
    }

    // Rota pública: quem esqueceu a senha não tem sessão.
    if (window.location.hash.startsWith('#/redefinir-senha')) {
        root.append(renderResetPassword());
        return;
    }

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
    refreshAlerts();
    main.append(el('div', { class: 'empty', text: 'Carregando…' }));

    // Antes da tela: dashboard e previsão já precisam ver os títulos das recorrências.
    // Se falhar, segue sem eles — não vale travar o app por isso.
    await ensureRecurringBills().catch(() => {});

    try {
        const view = await route.render();
        clear(main).append(view);
    } catch (error) {
        clear(main).append(el('div', { class: 'empty', text: error?.message || 'Não foi possível carregar a página.' }));
    }
}

window.addEventListener('hashchange', render);
window.addEventListener('profile:updated', () => {
    const chipName = document.querySelector('.user-chip-name');
    if (chipName) chipName.textContent = session.name || session.email || '';
    document.querySelector('.user-chip-avatar')?.replaceChildren(userAvatar());
});
window.addEventListener('session:expired', () => {
    toast('Sua sessão expirou. Entre novamente.', 'error');
    resetAlerts();
    window.location.hash = '';
    render();
});

render();
