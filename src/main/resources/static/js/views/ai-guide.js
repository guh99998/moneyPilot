import { session } from '../api.js';
import { el, pageHead, toast } from '../ui.js';

/** Texto pronto para colar no assistente; as URLs usam a origem em que o app está rodando. */
export function assistantPrompt(origin) {
    return [
        `Você vai operar a minha conta do moneyPilot pela API REST em ${origin}.`,
        '',
        `Contrato completo (OpenAPI): ${origin}/v3/api-docs`,
        '',
        'Sessão:',
        `1. POST ${origin}/api/v1/auth/login com {"email": "...", "password": "...", "refreshTokenInBody": true}.`,
        '   A resposta traz token (access token), refreshToken e expiresIn (segundos até o token vencer).',
        '2. Mande o token em todas as chamadas: Authorization: Bearer <token>.',
        `3. Quando receber 401 ou faltar pouco para expiresIn, renove com POST ${origin}/api/v1/auth/refresh e`,
        '   {"refreshToken": "..."}. A resposta traz um novo par e o refreshToken antigo deixa de valer:',
        '   guarde sempre o último. Nunca reuse um refreshToken já trocado.',
        `4. Ao terminar, saia com POST ${origin}/api/v1/auth/logout e {"refreshToken": "..."}.`,
        '',
        'Regras:',
        '- Dinheiro é número com 2 casas e ponto decimal (50.00), nunca com vírgula. Datas são aaaa-mm-dd.',
        '- Antes de lançar, liste as contas (GET /api/v1/accounts) e as categorias (GET /api/v1/categories)',
        '  e use os ids certos. A categoria deve ser do mesmo tipo do lançamento (EXPENSE ou INCOME).',
        '- Para lançar uma despesa: POST /api/v1/transactions com accountId, categoryId, amount, type, description e date.',
        '- Erros vêm como JSON com message e, quando é de validação, fieldErrors por campo. Leia e corrija antes de tentar de novo.',
        '- Guarde a senha e o refreshToken só para esta tarefa e não os escreva em mensagens ou arquivos.',
        '- Confirme comigo antes de apagar qualquer coisa.'
    ].join('\n');
}

function code(text) {
    // tabindex: o bloco rola na horizontal e precisa ser alcançável pelo teclado.
    return el('pre', { class: 'code-block', tabindex: '0', role: 'region', 'aria-label': 'Exemplo de chamada', translate: 'no' }, [el('code', { text })]);
}

/**
 * "Usando com IA": página pública com o passo a passo para operar a API fora do navegador e o texto pronto
 * para o assistente. Sem sessão ela é uma tela solta; com sessão fica dentro do layout do app.
 */
export function renderAiGuide() {
    const origin = window.location.origin;
    const prompt = assistantPrompt(origin);
    const promptId = `ai-prompt-${Math.random().toString(36).slice(2, 7)}`;

    const promptBox = el('textarea', {
        id: promptId,
        name: 'assistant-prompt',
        class: 'ai-prompt',
        autocomplete: 'off',
        spellcheck: 'false',
        translate: 'no',
        readonly: true,
        rows: '14',
        'aria-label': 'Texto para colar no assistente'
    });
    promptBox.value = prompt;

    const copyStatus = el('p', { class: 'meter-foot', role: 'status' });
    const copyBtn = el('button', { type: 'button', class: 'btn-primary', text: 'Copiar texto' });
    copyBtn.addEventListener('click', async () => {
        try {
            await navigator.clipboard.writeText(prompt);
            copyStatus.textContent = 'Texto copiado.';
            toast('Texto copiado.', 'success');
        } catch {
            // Sem permissão para a área de transferência: deixa o texto selecionado para o Ctrl+C.
            promptBox.focus();
            promptBox.select();
            copyStatus.textContent = 'Não consegui copiar sozinho. O texto está selecionado: use Ctrl+C.';
        }
    });

    const body = el('div', { class: 'ai-guide' }, [
        el('section', {}, [
            el('h3', { text: '1. Entrar' }),
            el('p', { text: 'Peça o refresh token no corpo com refreshTokenInBody. No navegador isso não é necessário: lá o token fica num cookie protegido.' }),
            code(`POST ${origin}/api/v1/auth/login\n{ "email": "voce@exemplo.com", "password": "sua senha", "refreshTokenInBody": true }\n\n→ { "token": "...", "refreshToken": "...", "expiresIn": 900 }`),
            el('p', { text: 'Mande o token em cada chamada: Authorization: Bearer <token>.' })
        ]),
        el('section', {}, [
            el('h3', { text: '2. Renovar' }),
            el('p', { text: 'O access token dura poucos minutos (expiresIn, em segundos). Ao receber 401, ou perto do fim, troque o refresh token por um novo par. O antigo deixa de valer, então guarde sempre o último.' }),
            code(`POST ${origin}/api/v1/auth/refresh\n{ "refreshToken": "..." }\n\n→ { "token": "...", "refreshToken": "...", "expiresIn": 900 }`),
            el('p', { class: 'ai-warning', text: 'Reusar um refresh token já trocado encerra todas as suas sessões.' })
        ]),
        el('section', {}, [
            el('h3', { text: '3. Listar contas e categorias' }),
            el('p', { text: 'Pegue os ids para o lançamento.' }),
            code(`GET ${origin}/api/v1/accounts\nGET ${origin}/api/v1/categories`)
        ]),
        el('section', {}, [
            el('h3', { text: '4. Lançar uma despesa' }),
            el('p', { text: 'Dinheiro é número com 2 casas e ponto decimal (50.00); com vírgula ou mais de 2 casas a API responde 400 com o campo errado. Datas são aaaa-mm-dd.' }),
            code(`POST ${origin}/api/v1/transactions\n{ "accountId": 1, "categoryId": 3, "amount": 50.00, "type": "EXPENSE", "description": "Mercado", "date": "2026-10-28" }`)
        ]),
        el('section', {}, [
            el('h3', { text: '5. Sair' }),
            code(`POST ${origin}/api/v1/auth/logout\n{ "refreshToken": "..." }`)
        ]),
        el('section', {}, [
            el('h3', { text: 'Cuidados' }),
            el('ul', {}, [
                el('li', { text: 'Guarde o refresh token como se fosse a sua senha: ele vale 30 dias, até você sair (o logout o revoga).' }),
                el('li', { text: 'Conta criada só com o Google não tem senha. Para usar a API, crie uma em Perfil → Criar senha.' }),
                el('li', { text: 'Trocar a senha no app encerra as sessões da API: entre de novo depois.' })
            ])
        ]),
        el('section', {}, [
            el('h3', { text: 'Contrato completo' }),
            el('p', {}, [
                el('a', { href: '/swagger-ui.html', text: 'Swagger (interativo)' }),
                el('span', { text: ' · ' }),
                el('a', { href: '/v3/api-docs', text: 'OpenAPI (JSON)' })
            ])
        ]),
        el('section', {}, [
            el('h3', { text: 'Texto para o seu assistente' }),
            el('p', { text: 'Cole no assistente de IA. Depois diga o que quer, por exemplo: “lance R$ 50 no mercado hoje”.' }),
            promptBox,
            el('div', { class: 'ai-actions' }, [copyBtn]),
            copyStatus
        ])
    ]);

    const title = 'Usando com IA';
    const subtitle = 'Deixe um assistente lançar e consultar por você, usando a API do moneyPilot.';

    if (session.active) {
        return el('div', {}, [pageHead(title, subtitle), el('div', { class: 'card ai-card' }, [body])]);
    }

    return el('div', { class: 'auth-wrap' }, [
        el('div', { class: 'card auth-card ai-card' }, [
            el('img', { class: 'auth-logo', src: '/image/money-pilot-logo.png', alt: 'moneyPilot' }),
            el('h2', { text: title, style: 'text-align:center; margin:0 0 .5rem;' }),
            el('p', { class: 'auth-tagline', text: subtitle }),
            body,
            el('p', { class: 'auth-switch' }, [
                el('button', { type: 'button', text: 'Voltar para o login', onClick: () => { window.location.hash = ''; } })
            ])
        ])
    ]);
}
