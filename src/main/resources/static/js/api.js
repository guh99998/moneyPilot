const BASE = '/api/v1';
const TOKEN_KEY = 'moneypilot.token';
const EMAIL_KEY = 'moneypilot.email';
const NAME_KEY = 'moneypilot.name';

export class ApiError extends Error {
    constructor(status, message, fieldErrors) {
        super(message);
        this.status = status;
        this.fieldErrors = fieldErrors || null;
    }
}

export const session = {
    get token() {
        return localStorage.getItem(TOKEN_KEY);
    },
    get email() {
        return localStorage.getItem(EMAIL_KEY);
    },
    get name() {
        return localStorage.getItem(NAME_KEY);
    },
    setName(name) {
        if (name) localStorage.setItem(NAME_KEY, name);
    },
    start(token, email) {
        localStorage.setItem(TOKEN_KEY, token);
        if (email) localStorage.setItem(EMAIL_KEY, email);
    },
    clear() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(EMAIL_KEY);
        localStorage.removeItem(NAME_KEY);
    },
    get active() {
        return Boolean(localStorage.getItem(TOKEN_KEY));
    }
};

function buildUrl(path, query) {
    const url = new URL(BASE + path, window.location.origin);
    Object.entries(query || {}).forEach(([key, value]) => {
        if (Array.isArray(value)) value.forEach((item) => url.searchParams.append(key, item));
        else if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, value);
    });
    return url;
}

const NETWORK_ERROR_MESSAGE = 'Não foi possível falar com o servidor. Verifique sua conexão.';

let refreshing = null;

/**
 * Troca o refresh token (cookie httpOnly, enviado pelo navegador) por um access token novo.
 * A renovação em andamento é uma só e todas as chamadas que recebem 401 ao mesmo tempo esperam
 * pela mesma promise: cada renovação rotaciona o refresh token, e rotacionar várias vezes em
 * paralelo é indistinguível de reuso — o servidor derrubaria a própria sessão.
 */
function refreshAccessToken() {
    if (!refreshing) {
        refreshing = fetch(buildUrl('/auth/refresh'), { method: 'POST' })
            .catch(() => {
                throw new ApiError(0, NETWORK_ERROR_MESSAGE);
            })
            .then(async (response) => {
                if (!response.ok) throw new ApiError(response.status, 'Sua sessão expirou. Entre novamente.');
                const payload = await response.json();
                localStorage.setItem(TOKEN_KEY, payload.token);
                return payload.token;
            })
            .finally(() => {
                refreshing = null;
            });
    }
    return refreshing;
}

async function send(path, { method, body, query, token }) {
    const headers = {};
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (token) headers.Authorization = `Bearer ${token}`;

    try {
        return await fetch(buildUrl(path, query), {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body)
        });
    } catch {
        throw new ApiError(0, NETWORK_ERROR_MESSAGE);
    }
}

function expireSession() {
    session.clear();
    window.dispatchEvent(new CustomEvent('session:expired'));
    return new ApiError(401, 'Sua sessão expirou. Entre novamente.');
}

function tooManyAttemptsMessage(retryAfter) {
    const seconds = Number(retryAfter);
    if (!Number.isFinite(seconds) || seconds <= 0) return 'Muitas tentativas. Tente de novo em instantes.';
    const minutes = Math.max(1, Math.ceil(seconds / 60));
    return `Muitas tentativas. Tente de novo em ${minutes} ${minutes === 1 ? 'minuto' : 'minutos'}.`;
}

/** Depois de qualquer alteração do usuário, quem mostra situação calculada (o sino) recarrega. */
function announceChange(method, anonymous) {
    if (method !== 'GET' && !anonymous) window.dispatchEvent(new CustomEvent('api:changed'));
}

async function request(path, { method = 'GET', body, query, anonymous = false } = {}) {
    const options = { method, body, query };
    const sentWith = anonymous ? null : session.token;
    let response = await send(path, { ...options, token: sentWith });

    if (response.status === 401 && !anonymous) {
        let token;
        try {
            // Se outra chamada já renovou enquanto esta estava em voo, reaproveita o token novo.
            token = session.token && session.token !== sentWith ? session.token : await refreshAccessToken();
        } catch (error) {
            // Sem rede não dá para saber se a sessão acabou: mantém o login e deixa o erro chegar à tela.
            if (error.status === 0) throw error;
            throw expireSession();
        }

        response = await send(path, { ...options, token });
        if (response.status === 401) throw expireSession();
    }

    if (response.status === 204) {
        announceChange(method, anonymous);
        return null;
    }

    const payload = await response.json().catch(() => null);

    if (response.status === 429) {
        throw new ApiError(429, tooManyAttemptsMessage(response.headers.get('Retry-After')));
    }

    if (!response.ok) {
        throw new ApiError(
            response.status,
            payload?.message || `Erro ${response.status}`,
            payload?.fieldErrors
        );
    }

    announceChange(method, anonymous);
    return payload;
}

/** Spring serializa Page de dois jeitos (legado e via-dto) — normaliza os dois. */
export function normalizePage(payload) {
    if (!payload) return { content: [], number: 0, totalPages: 0, totalElements: 0 };
    const meta = payload.page || payload;
    return {
        content: payload.content || [],
        number: meta.number ?? 0,
        size: meta.size ?? 20,
        totalPages: meta.totalPages ?? 0,
        totalElements: meta.totalElements ?? (payload.content || []).length
    };
}

export const api = {
    register: (body) => request('/auth/register', { method: 'POST', body, anonymous: true }),
    login: (body) => request('/auth/login', { method: 'POST', body, anonymous: true }),
    // Anônimas: quem ainda não ativou a conta não tem sessão, e um 400 (link inválido) não pode derrubar outra.
    resendVerification: (email) =>
        request('/auth/verification/resend', { method: 'POST', body: { email }, anonymous: true }),
    confirmVerification: (token) =>
        request('/auth/verification/confirm', { method: 'POST', body: { token }, anonymous: true }),
    logout: () => request('/auth/logout', { method: 'POST', anonymous: true }),

    alerts: {
        list: () => request('/alerts'),
        dismiss: (key) => request('/alerts/dismiss', { method: 'POST', body: { key } })
    },

    profile: {
        get: () => request('/profile'),
        update: (name) => request('/profile', { method: 'PUT', body: { name } })
    },
    changePassword: (currentPassword, newPassword) =>
        request('/auth/password', { method: 'POST', body: { currentPassword, newPassword } }),
    requestEmailChange: (newEmail, currentPassword) =>
        request('/profile/email', { method: 'POST', body: { newEmail, currentPassword } }),
    // Anônima: o link do e-mail funciona sem login, e um 400 (link inválido) não pode derrubar a sessão.
    confirmEmailChange: (token) =>
        request('/auth/email/confirm', { method: 'POST', body: { token }, anonymous: true }),

    accounts: {
        list: (query) => request('/accounts', { query }),
        create: (body) => request('/accounts', { method: 'POST', body }),
        update: (id, body) => request(`/accounts/${id}`, { method: 'PUT', body }),
        remove: (id) => request(`/accounts/${id}`, { method: 'DELETE' }),
        balance: (id) => request(`/accounts/${id}/balance`)
    },

    categories: {
        list: (query) => request('/categories', { query }),
        create: (body) => request('/categories', { method: 'POST', body }),
        update: (id, body) => request(`/categories/${id}`, { method: 'PUT', body }),
        remove: (id) => request(`/categories/${id}`, { method: 'DELETE' })
    },

    transactions: {
        list: (query) => request('/transactions', { query }),
        create: (body) => request('/transactions', { method: 'POST', body }),
        update: (id, body) => request(`/transactions/${id}`, { method: 'PUT', body }),
        remove: (id) => request(`/transactions/${id}`, { method: 'DELETE' }),
        transfer: (body) => request('/transactions/transfers', { method: 'POST', body })
    },

    budgets: {
        list: (query) => request('/budgets', { query }),
        create: (body) => request('/budgets', { method: 'POST', body }),
        update: (id, body) => request(`/budgets/${id}`, { method: 'PUT', body }),
        remove: (id) => request(`/budgets/${id}`, { method: 'DELETE' })
    },

    bills: {
        list: (query) => request('/bills', { query }),
        create: (body) => request('/bills', { method: 'POST', body }),
        update: (id, body) => request(`/bills/${id}`, { method: 'PUT', body }),
        remove: (id) => request(`/bills/${id}`, { method: 'DELETE' }),
        cancel: (id) => request(`/bills/${id}/cancel`, { method: 'POST' }),
        settle: (id, body) => request(`/bills/${id}/settle`, { method: 'POST', body }),
        unsettle: (id) => request(`/bills/${id}/unsettle`, { method: 'POST' }),
        bulkSettle: (body) => request('/bills/settle', { method: 'POST', body }),
        createInstallments: (body) => request('/bills/installments', { method: 'POST', body }),
        removeInstallments: (groupId) => request(`/bills/installments/${groupId}`, { method: 'DELETE' }),
        materialize: (month) => request('/bills/materialize', { method: 'POST', query: { month } })
    },

    billRecurrences: {
        list: (query) => request('/bill-recurrences', { query }),
        create: (body) => request('/bill-recurrences', { method: 'POST', body }),
        update: (id, body) => request(`/bill-recurrences/${id}`, { method: 'PUT', body }),
        remove: (id) => request(`/bill-recurrences/${id}`, { method: 'DELETE' }),
        deactivate: (id) => request(`/bill-recurrences/${id}/deactivate`, { method: 'POST' })
    },

    reports: {
        monthlySummary: (month, year) => request('/reports/monthly-summary', { query: { month, year } }),
        spendingByCategory: (month, year) => request('/reports/spending-by-category', { query: { month, year } }),
        budgetVsActual: (month, year) => request('/reports/budget-vs-actual', { query: { month, year } }),
        cashFlowForecast: (days) => request('/reports/cash-flow-forecast', { query: { days } }),
        monthlyForecast: (months) => request('/reports/monthly-forecast', { query: { months } }),
        billsSummary: () => request('/reports/bills-summary')
    }
};

/** Carrega todas as páginas de um endpoint paginado (listas curtas: contas, categorias). */
export async function fetchAll(listFn) {
    const first = normalizePage(await listFn({ page: 0, size: 100 }));
    const items = [...first.content];
    for (let page = 1; page < first.totalPages; page++) {
        const next = normalizePage(await listFn({ page, size: 100 }));
        items.push(...next.content);
    }
    return items;
}
