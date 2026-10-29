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
import { renderSupport } from './views/support.js';
import { renderAdminTickets } from './views/admin-tickets.js';
import { renderContribution } from './views/contribution.js';
import { renderAiGuide } from './views/ai-guide.js';
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
    ai: svgIcon('<rect x="5" y="8" width="14" height="11" rx="2"/><path d="M12 4v4M9 13h.01M15 13h.01M9.5 16.5h5"/>'),
    tickets: svgIcon('<path d="M3 8a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v2a2 2 0 0 0 0 4v2a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2a2 2 0 0 0 0-4z"/><path d="M13 6v12"/>'),
    support: svgIcon('<path d="M21 12a9 9 0 0 1-9 9H4l2.2-3.3A9 9 0 1 1 21 12z"/><path d="M9 10h6M9 14h4"/>'),
    budgets: svgIcon('<path d="M21 12a9 9 0 1 1-9-9v9z"/><path d="M15 3.5a9 9 0 0 1 5.5 5.5H15z"/>')
};

// Tela fora do menu: o acesso é pelo nome do usuário no rodapé.
const PROFILE_ROUTE = { path: 'profile', label: 'Perfil', render: renderProfile };
const SUPPORT_ROUTE = { path: 'suporte', label: 'Falar com o suporte', render: renderSupport };
// Só para administrador: sem o papel, a rota cai na Visão geral e o servidor responde 404 de qualquer jeito.
const ADMIN_TICKETS_ROUTE = { path: 'admin/chamados', label: 'Chamados', render: renderAdminTickets };
// Só com a chave Pix configurada no servidor: sem ela, a rota cai na Visão geral e o servidor responde 404.
const CONTRIBUTION_ROUTE = { path: 'apoie', label: 'Apoie o projeto', render: renderContribution };
const AI_ROUTE = { path: 'usando-com-ia', label: 'Usando com IA', render: renderAiGuide };

// O app tem só o tema claro; limpa a preferência que versões antigas gravavam.
localStorage.removeItem('moneypilot.theme');

function currentRoute() {
    const path = window.location.hash.replace(/^#\/?/, '');
    if (path === PROFILE_ROUTE.path) return PROFILE_ROUTE;
    if (path === SUPPORT_ROUTE.path) return SUPPORT_ROUTE;
    if (path === AI_ROUTE.path) return AI_ROUTE;
    if (path === ADMIN_TICKETS_ROUTE.path && session.admin) return ADMIN_TICKETS_ROUTE;
    if (path === CONTRIBUTION_ROUTE.path && session.contribution) return CONTRIBUTION_ROUTE;
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
                    class: 'user-chip' + (active.path === AI_ROUTE.path ? ' active' : ''),
                    href: '#/' + AI_ROUTE.path,
                    // Tocar na página atual não dispara hashchange, então fecha à mão.
                    onClick: () => sidebar.classList.remove('open')
                }, [
                    el('span', { class: 'user-chip-avatar', 'aria-hidden': 'true', html: NAV_ICONS.ai }),
                    el('span', { class: 'user-chip-name', text: AI_ROUTE.label })
                ]),
                session.admin ? el('a', {
                    class: 'user-chip' + (active.path === ADMIN_TICKETS_ROUTE.path ? ' active' : ''),
                    href: '#/' + ADMIN_TICKETS_ROUTE.path,
                    // Tocar na página atual não dispara hashchange, então fecha à mão.
                    onClick: () => sidebar.classList.remove('open')
                }, [
                    el('span', { class: 'user-chip-avatar', 'aria-hidden': 'true', html: NAV_ICONS.tickets }),
                    el('span', { class: 'user-chip-name', text: ADMIN_TICKETS_ROUTE.label })
                ]) : null,
                el('a', {
                    class: 'user-chip' + (active.path === SUPPORT_ROUTE.path ? ' active' : ''),
                    href: '#/' + SUPPORT_ROUTE.path,
                    // Tocar na página atual não dispara hashchange, então fecha à mão.
                    onClick: () => sidebar.classList.remove('open')
                }, [
                    el('span', { class: 'user-chip-avatar', 'aria-hidden': 'true', html: NAV_ICONS.support }),
                    el('span', { class: 'user-chip-name', text: SUPPORT_ROUTE.label })
                ]),
                el('a', {
                    class: 'user-chip' + (active.path === PROFILE_ROUTE.path ? ' active' : ''),
                    href: '#/' + PROFILE_ROUTE.path,
                    title: session.email || 'Meu perfil',
                    'aria-label': 'Meu perfil',
                    // Tocar na página atual não dispara hashchange, então fecha à mão.
                    onClick: () => sidebar.classList.remove('open')
                }, [
                    el('span', { class: 'user-chip-avatar profile-chip-avatar' }, userAvatar()),
                    el('span', { class: 'user-chip-name profile-chip-name', text: session.name || session.email || '' })
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
                }),
                // Link de texto, sem ícone: o apoio é um convite discreto, não um item de navegação.
                session.contribution ? el('a', {
                    class: 'foot-link',
                    href: '#/' + CONTRIBUTION_ROUTE.path,
                    'aria-current': active.path === CONTRIBUTION_ROUTE.path ? 'page' : null,
                    // Tocar na página atual não dispara hashchange, então fecha à mão.
                    onClick: () => sidebar.classList.remove('open'),
                    text: 'Pagar um cafezinho?'
                }) : null
            ])
        ])
    ]);

    return sidebar;
}

async function render() {
    // Sessões abertas antes do perfil existir (ou do login com Google) não têm o nome e a foto guardados:
    // busca uma vez. A espera vem antes de limpar a tela: duas renderizações concorrentes (login + hashchange)
    // montariam o menu duas vezes.
    if (session.active && (!session.name || session.avatar === null || session.admin === null || session.contribution === null)) {
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

    // Rota pública: a documentação da API para IA vale com ou sem sessão (com sessão fica no layout do app).
    if (!session.active && window.location.hash.startsWith('#/usando-com-ia')) {
        root.append(renderAiGuide());
        return;
    }

    // Rota pública: o apoio ao projeto abre pelo site, sem login (spec 019).
    if (!session.active && window.location.hash.startsWith('#/apoie')) {
        root.append(await renderContribution());
        return;
    }

    // Rota pública: quem não consegue entrar também precisa falar com o suporte.
    if (!session.active && window.location.hash.startsWith('#/suporte')) {
        root.append(renderSupport());
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
    const chipName = document.querySelector('.profile-chip-name');
    if (chipName) chipName.textContent = session.name || session.email || '';
    document.querySelector('.profile-chip-avatar')?.replaceChildren(userAvatar());
});
window.addEventListener('session:expired', () => {
    toast('Sua sessão expirou. Entre novamente.', 'error');
    resetAlerts();
    window.location.hash = '';
    render();
});

render();
