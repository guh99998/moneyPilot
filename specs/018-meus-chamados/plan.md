# 018 — Plano técnico

**Spec:** [spec.md](spec.md)
**Status:** proposto

## Abordagem

### A. Chamados da conta (domínio `support/`)

- `SupportTicketRepository` ganha:
  - `Page<SupportTicket> findAllByUserId(Long userId, Pageable pageable)`;
  - `Optional<SupportTicket> findByReferenceAndUserId(String reference, Long userId)`;
  - `List<SupportTicket> findAllByUserIdAndStatusChangedAtGreaterThanEqual(Long userId, Instant since)`.
- `MySupportTicketService` novo, no padrão do `AdminSupportTicketService`. Usa `AuthenticatedUserProvider.getCurrentUserId()`, ordena por `createdAt desc, id desc` e tem tamanho de página fixo em 10.
- Records novos, sem `id` e sem `emailSent`, para o contrato não expor o que a pessoa não deve ver:
  - `MySupportTicketSummaryResponse`: `reference`, `category`, `status`, `createdAt`, `statusChangedAt`;
  - `MySupportTicketDetailResponse`: os mesmos campos, mais `message` e `replyTo`.
- `SupportController` ganha `GET /support/tickets` e `GET /support/tickets/{reference}`.
- `SupportTicketNotFoundException` ganha um construtor por referência. Chamado de outra conta e chamado inexistente respondem o mesmo `404`.

### B. Alerta no sino (domínio `notification/`)

- `AlertKind.SUPPORT_TICKET_UPDATED`.
- `AlertResponse` ganha `ticketReference` e `ticketStatus`, com `@Schema`. As chamadas existentes passam `null`.
- `AlertService.compute` acrescenta os alertas de chamado depois dos de orçamento:
  - base: chamados da conta com `statusChangedAt >= agora − 30 dias`, com "agora" do `Clock`;
  - chave `SUPPORT_TICKET:<reference>` (cabe nos 100 caracteres de `dismissed_alerts.alert_key`) e impressão digital igual ao `status`. A dispensa e o "volta se mudar" reusam o `DismissedAlert` que já existe, sem tabela nova;
  - quando o alerta some pelos 30 dias, a dispensa é esquecida pelo `listAlerts`, como hoje.
- `statusChangedAt` é nulo em chamado que nunca mudou, e a consulta não o seleciona: é isso que cumpre o CA7.
- `AlertService` passa a depender do `SupportTicketRepository`, como hoje depende do `BillRepository`.

## Banco de dados

Migration nova `V16__add_user_index_to_support_tickets.sql`: índice `(user_id, created_at DESC)` em `support_tickets`.

**Impacto em dados existentes:** nenhum. Só cria índice; não altera nem apaga dados. A V15 não é editada.

## API

| Método | Rota | Mudança |
|---|---|---|
| GET | `/support/tickets` | Novo. Lista paginada (`page`, 10 por página) dos chamados da conta, mais recentes primeiro. Itens: `reference, category, status, createdAt, statusChangedAt`. `401` sem login. |
| GET | `/support/tickets/{reference}` | Novo. Detalhe com `message` e `replyTo`. `404` se não existe ou é de outra conta; `401` sem login. |
| GET | `/alerts` | Novo tipo `SUPPORT_TICKET_UPDATED`, com os campos `ticketReference` e `ticketStatus`. Campos novos e opcionais; clientes antigos ignoram. |

As duas rotas novas ficam sob Bearer, sem `@SecurityRequirements` vazio. No `SecurityConfig` só o `POST /support/tickets` é `permitAll`, então os `GET` já exigem login; o teste confirma. Documentação OpenAPI no padrão da 011 (`@Tag`, `@Operation`, `@ApiResponse` 200/401/404 com `ErrorResponse`, `@Schema` com exemplo em cada campo, enums com `enumAsRef`).

Nenhuma mudança incompatível.

## Cliente web

- `js/api.js`: `api.support.mine(page)` e `api.support.get(reference)`. O envio do chamado não muda.
- `js/views/support.js`, só na tela logada:
  - card "Meus chamados" abaixo do formulário, em lista `table-stack` (vira cartões no celular), status como selo com texto e paginação como a de Lançamentos;
  - estado vazio: "Você ainda não abriu chamados por aqui. Chamados enviados sem entrar na conta não aparecem nesta lista.";
  - o detalhe abre no próprio card: mensagem como `text` (nunca `html`), "Respondemos no e-mail X", botão Voltar e, se resolvido, a sugestão de abrir um chamado novo citando a referência;
  - a lista recarrega depois de enviar um chamado.
- `js/alerts.js`:
  - frase do alerta novo e `destinationFor` apontando para `#/suporte`;
  - `presetOpenTicket(reference)`, no padrão do `presetBillFilters`: a tela abre o detalhe direto;
  - `dismissIfActive(key)` exportado, que só dispensa se o alerta estiver na lista já carregada, para não gerar `404`. O detalhe o chama ao abrir.

## Testes

- **`MySupportTicketIntegrationTest`** (novo, estende `AbstractIntegrationTest`):
  - CA1: ordem e página de 10;
  - CA2: chamado anônimo com o mesmo e-mail não aparece;
  - CA3: campos do detalhe e ausência de `emailSent`;
  - CA4: `404` para chamado de outra conta e `401` sem login.
- **`AlertIntegrationTest`**:
  - CA5: mudança de status gera o alerta com o status novo;
  - CA6: dispensar esconde, e mudar o status de novo traz de volta;
  - CA7: some após 30 dias (com o `Clock` de teste) e chamado `OPEN` que nunca mudou não gera alerta;
  - isolamento: o alerta de uma conta não aparece para outra.
- **`OpenApiContractIntegrationTest`**: cobre as rotas e campos novos sozinho.
- **JS:**
  - `support-view.test.mjs`: lista, estado vazio, detalhe com a mensagem como texto (`<script>` não vira HTML), sugestão no resolvido (CA1, CA3);
  - `alert-targets.test.mjs`: destino e preset do alerta de chamado (CA8).

## Riscos e alternativas descartadas

- Descartado: listar também os chamados sem login pelo e-mail. Qualquer pessoa pode abrir um chamado com o e-mail de outra, então isso vazaria a mensagem de terceiros.
- Descartado: guardar a notificação no banco. Exige tabela e regras de lida/não lida; fica para a 023. Reusar `DismissedAlert` mantém o modelo calculado da 007.
- Risco: o alerta é calculado, então 30 dias depois da mudança ele some mesmo sem a pessoa ter visto. Aceito na spec; a 023 resolve com histórico.
- Risco: `dismissIfActive` depende da lista de alertas já carregada. Se estiver desatualizada, o alerta simplesmente não é dispensado, sem erro.

## Deploy

- Tem migration (V16), mas ela só cria índice. Mesmo assim, faça o `pg_dump` antes, como manda o README.
- Sem variável de ambiente nova.
- Rollback: imagem anterior. O índice fica no banco sem uso e não atrapalha a versão anterior.

## Verificação (ao implementar)

- `./mvnw verify` e `node --test "src/test/js/*.test.mjs"` passando.
- Checagem manual local: abrir um chamado logado; mudar o status como administrador em `#/admin/chamados`; ver o alerta no sino; clicar nele e chegar ao detalhe; confirmar que o alerta some. Conferir no celular (estreito) e no desktop.
- Validar com as skills: `api-design-principles` nas rotas novas; `web-design-guidelines`, `accessibility-compliance` e `responsive-design` na tela e no sino. Anotar a revisão em `tasks.md`.
- README: tabela da API (rotas novas) e tabela de alertas (alerta novo). A spec 007 continua como registro histórico.
