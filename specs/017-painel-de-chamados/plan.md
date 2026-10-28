# 017 — Plano técnico

**Spec:** [spec.md](spec.md)
**Status:** proposto

## Abordagem

### A. Gravar o chamado (domínio `support/`)

- Entidade `SupportTicket` e `SupportTicketRepository`, no padrão de `notification/DismissedAlert`.
- Enum `SupportTicketStatus` (`OPEN`, `IN_PROGRESS`, `RESOLVED`) com `label()` em português, como `SupportCategory`.
- `SupportService.send` passa a:
  1. checar os limites (como hoje);
  2. gravar o chamado `OPEN`, com `emailSent = false`;
  3. enviar o e-mail. Se der certo, marca `emailSent = true`. Se lançar `EmailSendException`, registra no log e segue;
  4. registrar o limite e responder `{reference, replyTo}`.
- Agora o limite conta sempre que o chamado é gravado, porque ele foi recebido. A gravação fica fora da transação do envio, para a falha do provedor não desfazer o chamado.
- Referência única: `SupportReference.next()` continua gerando a referência, e o service tenta de novo se ela já existir (`existsByReference`), até 5 vezes.

### B. Administrador

- `config/AdminProperties` (`app.admin.emails`): lista normalizada com `EmailAddress.normalize`, como o e-mail do login. Vazia por padrão.
- Componente `support/AdminAccess`, que reusa `AuthenticatedUserProvider.getCurrentUserId()` e `UserRepository`:
  - `isAdmin(userId)` diz se o e-mail da conta está na lista;
  - `requireAdmin()` lança `AdminOnlyException`, que o `GlobalExceptionHandler` responde como `404` no `ErrorResponse`.
- O papel vem do e-mail atual da conta, então trocar o e-mail (008) tira o acesso até a variável ser atualizada.
- `ProfileResponse` ganha `boolean admin`, para o cliente decidir se mostra o menu. Quem protege as rotas é o servidor.

### C. Rotas do painel

`AdminSupportTicketController`, protegidas por Bearer. Todas passam por `requireAdmin()` e são documentadas no padrão da 011. O `OpenApiContractIntegrationTest` obriga isso.

## Banco de dados

Migration nova `V15__create_support_tickets_table.sql`:

- `id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`
- `reference VARCHAR(6) NOT NULL UNIQUE`
- `category VARCHAR(20) NOT NULL` com `CHECK` nos 4 valores
- `message TEXT NOT NULL`
- `reply_to VARCHAR(255) NOT NULL`
- `user_id BIGINT NULL REFERENCES users(id)` (sem cascade, como as outras tabelas; a 012 apaga explicitamente)
- `status VARCHAR(20) NOT NULL DEFAULT 'OPEN'` com `CHECK` (`OPEN`, `IN_PROGRESS`, `RESOLVED`)
- `email_sent BOOLEAN NOT NULL`
- `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `status_changed_at TIMESTAMPTZ NULL`
- índice `(status, created_at DESC)`

**Impacto em dados existentes:** nenhum. Só cria uma tabela; chamados antigos (só no e-mail) não são importados.

## API

| Método | Rota | Mudança |
|---|---|---|
| POST | `/support/tickets` | Grava o chamado. Falha no e-mail deixa de ser `502`: responde `200` com a referência. |
| GET | `/admin/support/tickets` | Novo. Lista paginada, mais recentes primeiro, filtro opcional `status`. Itens: `id, reference, category, status, replyTo, emailSent, createdAt`. `404` para quem não é admin. |
| GET | `/admin/support/tickets/{id}` | Novo. Chamado completo, com `message`, `statusChangedAt` e o nome da conta quando houver. `404` se não existe ou se não é admin. |
| PATCH | `/admin/support/tickets/{id}` | Novo. Corpo `{status}`. Responde `200` com o chamado. `400` status inválido; `404`. |
| GET | `/profile` | Novo campo `admin` (boolean). |

Mudanças incompatíveis com o cliente atual: o `502` do suporte deixa de existir. O cliente já trata `200`, então nada quebra.

## Cliente web

- `session` (`js/api.js`) guarda `admin`, preenchido pelo `api.profile.get()`. O `render()` de `app.js` busca o perfil também quando `admin` ainda é desconhecido, no mesmo bloco que hoje busca nome e foto. `session.clear()` limpa o valor.
- `api.admin.tickets`: `list(status, page)`, `get(id)` e `updateStatus(id, status)`.
- `js/views/admin-tickets.js` (novo), com `renderAdminTickets()`:
  - filtro de status que abre em "Aberto";
  - lista no padrão `table-stack`, que vira cartões no celular, com a marca "e-mail não enviado" quando for o caso;
  - ao abrir um chamado: mensagem como texto (`text`, nunca `html`), e-mail com link `mailto:` para responder, e o seletor de status com o botão "Salvar status";
  - paginação como a de Lançamentos.
- `app.js`:
  - rota `#/admin/chamados`, que só existe se `session.admin` (senão cai na Visão geral);
  - item "Chamados" no rodapé do menu, junto de "Falar com o suporte", só para administrador.

## Testes

- **`SupportIntegrationTest`**: o teste existente `sendFailure_isBadGateway_andDoesNotSpendTheQuota` **muda de propósito**, porque a regra mudou. Ele passa a provar o CA7: `200`, chamado gravado com `emailSent = false`, e o limite contado. O resto continua igual.
- **`SupportTicketIntegrationTest`** (novo):
  - CA1: com e sem login, o chamado é gravado `OPEN` com a mesma referência do e-mail;
  - CA2: o administrador lista os chamados mais recentes primeiro e filtra por status;
  - CA3: o `PATCH` grava o status e o `statusChangedAt`, e reabrir funciona;
  - CA4: conta comum recebe `404` nas três rotas, e sem login recebe `401`;
  - CA5: sem `ADMIN_EMAILS`, ninguém é administrador e o envio continua funcionando;
  - CA7: falha no e-mail grava o chamado com `emailSent = false`;
  - bordas: status inválido dá `400`, chamado inexistente dá `404`, e-mail na lista em maiúsculas casa, e `admin` aparece no `/profile`.
- **`OpenApiContractIntegrationTest`**: cobre as rotas novas sozinho.
- **JS:** `admin-tickets-view.test.mjs` cobre o CA6 (mensagem com `<script>` aparece como texto), o filtro, a troca de status e a marca de e-mail não enviado. Também entra um teste de que o menu só mostra "Chamados" para administrador.

## Riscos e alternativas descartadas

- Administrador definido por e-mail: quem conseguir uma conta com esse e-mail vira admin. Mitigação: o login exige conta verificada e o e-mail é único.
- Gravar com o e-mail falhando: o chamado pode chegar só ao painel. Mitigação: a marca "e-mail não enviado" na lista.
- Descartado: uma coluna `role` em `users`. Isso pediria migration de dados e uma tela para dar o papel, quando a variável resolve.
- Descartado: responder pelo painel. Está fora de escopo, e a resposta segue pelo e-mail.

## Deploy

- Tem migration (V15), mas ela só cria tabela. Mesmo assim, faça o `pg_dump` antes, como manda o README.
- Variável nova `ADMIN_EMAILS`:
  - em `application-prod.properties`, `app.admin.emails=${ADMIN_EMAILS:}` (opcional, sem quebrar o boot);
  - no `.env.production.example` e no `docker-compose.prod.yml`, sem `:?`, porque não é obrigatória.
- Rollback: imagem anterior. A tabela `support_tickets` fica no banco sem uso e não atrapalha a versão anterior.

## Verificação (ao implementar)

- Rodar `./mvnw verify` e `node --test "src/test/js/*.test.mjs"`.
- Checagem manual com o app local e `ADMIN_EMAILS` apontando para uma conta de teste:
  - abrir um chamado sem login e outro com login;
  - entrar como admin, ver a lista, abrir e mudar o status;
  - entrar com outra conta e confirmar que o menu não aparece e a rota cai na Visão geral.
- Validar com as skills: `api-design-principles` nas rotas novas; `web-design-guidelines`, `accessibility-compliance` e `responsive-design` na tela. Anotar em `tasks.md`.
- README: tabela da API, decisão de domínio (admin por e-mail, chamado gravado), variável `ADMIN_EMAILS` e item do cliente web.
