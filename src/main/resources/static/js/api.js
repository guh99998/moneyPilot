const BASE = '/api/v1';
const TOKEN_KEY = 'moneypilot.token';
const EMAIL_KEY = 'moneypilot.email';

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
    start(token, email) {
        localStorage.setItem(TOKEN_KEY, token);
        if (email) localStorage.setItem(EMAIL_KEY, email);
    },
    clear() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(EMAIL_KEY);
    },
    get active() {
        return Boolean(localStorage.getItem(TOKEN_KEY));
    }
};

function buildUrl(path, query) {
    const url = new URL(BASE + path, window.location.origin);
    Object.entries(query || {}).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, value);
    });
    return url;
}

async function request(path, { method = 'GET', body, query, anonymous = false } = {}) {
    const headers = {};
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (!anonymous && session.token) headers.Authorization = `Bearer ${session.token}`;

    let response;
    try {
        response = await fetch(buildUrl(path, query), {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body)
        });
    } catch {
        throw new ApiError(0, 'Não foi possível falar com o servidor. Verifique sua conexão.');
    }

    if (response.status === 401 && !anonymous) {
        session.clear();
        window.dispatchEvent(new CustomEvent('session:expired'));
        throw new ApiError(401, 'Sua sessão expirou. Entre novamente.');
    }

    if (response.status === 204) return null;

    const payload = await response.json().catch(() => null);

    if (!response.ok) {
        throw new ApiError(
            response.status,
            payload?.message || `Erro ${response.status}`,
            payload?.fieldErrors
        );
    }

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

    reports: {
        monthlySummary: (month, year) => request('/reports/monthly-summary', { query: { month, year } }),
        spendingByCategory: (month, year) => request('/reports/spending-by-category', { query: { month, year } }),
        budgetVsActual: (month, year) => request('/reports/budget-vs-actual', { query: { month, year } })
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
