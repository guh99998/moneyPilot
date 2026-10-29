# 018 — Tarefas

**Plano:** [plan.md](plan.md)

Cada tarefa vira um commit (ou poucos) e aponta para os critérios que atende.

- [x] **T1** — Migration `V16__add_user_index_to_support_tickets.sql` (CA1)
- [x] **T2** — Repositório: `findAllByUserId`, `findByReferenceAndUserId` e `findAllByUserIdAndStatusChangedAtGreaterThanEqual`; construtor por referência em `SupportTicketNotFoundException` (CA1, CA3, CA4, CA5)
- [x] **T3** — `MySupportTicketSummaryResponse`, `MySupportTicketDetailResponse` e `MySupportTicketService` (CA1, CA2, CA3, CA4)
- [x] **T4** — `GET /support/tickets` e `GET /support/tickets/{reference}` no `SupportController`, com OpenAPI no padrão da 011 (CA1, CA3, CA4)
- [x] **T5** — `MySupportTicketIntegrationTest` (CA1–CA4)
- [x] **T6** — `AlertKind.SUPPORT_TICKET_UPDATED`, campos novos em `AlertResponse` e alertas de chamado em `AlertService.compute` (CA5, CA6, CA7)
- [x] **T7** — Testes em `AlertIntegrationTest` e conferência do `OpenApiContractIntegrationTest` (CA5–CA7)
- [x] **T8** — Cliente: `api.support.mine` e `api.support.get` em `js/api.js` (CA1, CA3)
- [x] **T9** — Tela `js/views/support.js`: card "Meus chamados", estado vazio, detalhe e recarga após envio (CA1, CA2, CA3)
- [x] **T10** — `js/alerts.js`: frase, destino, `presetOpenTicket` e `dismissIfActive`; detalhe dispensa o alerta ao abrir (CA6, CA8)
- [x] **T11** — Testes JS: `support-view.test.mjs` e `alert-targets.test.mjs` (CA1, CA3, CA8)
- [x] **T12** — README: rotas novas e alerta novo na tabela da 007 (CA1–CA8)
- [ ] **T13** — Rodar `./mvnw verify` e `node --test "src/test/js/*.test.mjs"`, e a checagem manual do plano (suítes rodadas; falta a checagem manual no navegador)
- [x] **T14** — Validação com as skills do projeto (`api-design-principles`, `web-design-guidelines`, `accessibility-compliance`, `responsive-design`) e anotar a revisão aqui

## Verificação

- `./mvnw verify`: 445 testes, nenhuma falha. `node --test "src/test/js/*.test.mjs"`: 153 testes, nenhuma falha.
- **Pendente:** a checagem manual do plano (abrir chamado logado, mudar o status como administrador, ver o alerta no sino, clicar e chegar ao detalhe, conferir no celular e no desktop) ainda não foi feita no navegador.

## Notas de implementação

- **Aviso do detalhe ao sino por evento:** o plano previa o detalhe chamar `dismissIfActive` direto. Importar `alerts.js` em `support.js` quebraria os testes (o DOM falso não tem `document.addEventListener`), então o detalhe dispara o evento `support:ticket-opened` na janela e o `alerts.js` o escuta e chama `dismissIfActive`. O comportamento é o mesmo.
- **`OpenApiContractIntegrationTest`:** a lista `PUBLIC_PATHS` era por caminho, e `/support/tickets` agora tem `POST` público e `GET` protegido. Entrou `PUBLIC_OPERATIONS` (método + caminho) só para esse caso.
- **`support-view.test.mjs`:** os testes com sessão agora enfileiram a resposta da lista "Meus chamados", que carrega ao montar a tela; o teste do envio passou a conferir a segunda chamada.
- A coluna da lista é "Assunto" (a categoria do chamado), como no painel do administrador.

**Revisão com as skills do projeto**

- `api-design-principles`: recursos em plural, `GET` paginado com `page`, `401` sem login e `404` para inexistente ou de outra conta (não revela o que existe), respostas sem `id` e `emailSent`, campos novos do alerta opcionais (sem quebra), contrato coberto pelo `OpenApiContractIntegrationTest`.
- `web-design-guidelines` e `accessibility-compliance`: `h1`/`h2` em ordem, tabela semântica, botão "Abrir" com `aria-label` com a referência, selo de status com texto (não só cor), mensagem como texto, estados vazio, de carga e de erro. Corrigido: ao voltar do detalhe para a lista, o foco vai para o título do card.
- `responsive-design`: lista no padrão `table-stack` (vira cartões no celular), mensagem com `overflow-wrap: anywhere` e `pre-wrap`, sem largura fixa. Falta conferir visualmente em navegador.
