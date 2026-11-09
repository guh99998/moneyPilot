# 016 — Tarefas

**Plano:** [plan.md](plan.md)

Cada tarefa vira um commit (ou poucos) e aponta para os critérios que atende. Testes primeiro.

- [x] **T1** — `InvoiceCycle` (cálculo puro de ciclo e vencimento), com teste unitário (CA3)
- [x] **T2** — Migration V20 + campos em `Account`/`Transaction`, `AccountRequest`/`AccountResponse` e regras 1 e 2 em `AccountService` (CA1, CA2)
- [x] **T3** — `InvoiceService` e `GET /accounts/{id}/invoices` (total, estorno, `invoicesFrom`, pagamento parcial/a mais/atraso, `409` sem dias, `404`) (CA4, CA5, CA9)
- [x] **T4** — Compra parcelada: `POST /transactions/installments`, `409` na edição, apagar grupo (CA6, CA10)
- [x] **T5** — Faturas nas duas previsões (CA7, CA8)
- [x] **T6** — Contrato OpenAPI, `MoneyScaleIntegrationTest`, isolamento entre usuários (CA9, CA10)
- [x] **T7** — Conta: fechamento e vencimento no formulário de cartão (CA11)
- [x] **T8** — Tela Faturas (`views/invoices.js`), menu, pagar fatura e compra parcelada (CA11)
- [x] **T9** — Dashboard sem cartão em "Guardado" e linha de estado do Perfil (CA8)
- [x] **T10** — Aviso em Novidades e página na ajuda
- [x] **T11** — Verificação manual local (desktop e mobile)
- [x] **T12** — Skills: `web-design-guidelines`, `accessibility-compliance`, `responsive-design`, `api-design-principles`; anotar a revisão aqui
- [x] **T13** — README, `./mvnw verify` e `node --test`, atualizar o quadro

### Mudança: fatura em A pagar e receber (spec, regras 17 a 23)

- [x] **T14** - `InvoiceService.payableInvoices` e `GET /invoices/payable`, com OpenAPI (CA12)
- [x] **T15** - Quadro "A pagar" da Visão geral soma as faturas (CA15)
- [x] **T16** - Alertas de a pagar incluem fatura fechada ou vencida (CA16)
- [x] **T17** - `invoices.js`: `selectInvoiceCard` e `openPayInvoiceDialog` extraído (CA14)
- [x] **T18** - `bills.js`: linhas de fatura no topo da primeira página, filtros e botões "Ver itens da fatura" e "Pagar fatura" (CA13, CA14)
- [x] **T19** - Próximos vencimentos da Visão geral com as faturas, `mergeUpcoming` (CA18)
- [x] **T19b** - Ajuda, aviso em Novidades e README (CA11)
- [x] **T20** - Verificação manual (desktop e mobile), skills do projeto, `./mvnw verify`, `node --test`, quadro nº 4 (CA17)

### Mudança: início das faturas e lista única (spec, regras 25 a 30)

- [x] **T21** - `invoicesFrom` no pedido de conta: normalização, validações e OpenAPI (CA19, CA20)
- [x] **T22** - Contas: campo "Contar faturas desde" e `accountPayload` (CA19)
- [x] **T23** - Funções puras da lista de faturas: `filterInvoices`, `sortInvoices`, `summarize` (CA21, CA22)
- [x] **T24** - Tela Faturas como lista única: resumo, destaque por cartão, filtros, ordenação e "Ver compras" (CA21, CA22)
- [x] **T25** - `selectInvoiceCard` fixa o filtro de cartão; "Ver itens da fatura" abre a lista já filtrada (CA22)
- [x] **T26** - Ajuda e README
- [x] **T27** - Verificação manual (dois cartões, fatura antiga, filtros, ordenação, mobile), skills do projeto, `./mvnw verify`, `node --test`, quadro nº 4

### Mudança: compra recorrente no cartão (spec, regras 31 a 38)

- [ ] **T28** - Migration V21, entidade, repositório e CRUD de `/recurring-purchases` com OpenAPI e `MoneyScaleIntegrationTest` (CA23, CA28)
- [ ] **T29** - Geração idempotente com `generatedUntil` e `POST /recurring-purchases/materialize` (CA24, CA25)
- [ ] **T30** - Projeção na fatura (`projectedTotal`) e nas duas previsões (CA26)
- [ ] **T31** - Editar, desativar e apagar (CA27)
- [ ] **T32** - Bloqueio de título e recorrência de título em cartão com dias (CA28)
- [ ] **T33** - Cliente: Faturas (diálogo, lista, selo "Prevista"), `materialize` na sessão, formulários de título, "Recorrente" em Lançamentos (CA23 a CA28)
- [ ] **T34** - Ajuda, Novidade, README e quadro nº 4
- [ ] **T35** - Verificação manual, skills do projeto, `./mvnw verify` e `node --test`

## Notas de implementação

Descobertas durante a implementação que afetaram a spec ou o plano.

- **Colunas `INTEGER` na V20:** o plano dizia `SMALLINT`, mas o `ddl-auto: validate` do Hibernate recusa `int2` para `Integer`. Usei `INTEGER` (custo desprezível, V20 ainda não aplicada em lugar nenhum).
- **Parcelas (T4):** o limite de parcelas é 2 a 60 (o plano citava "como no plano de títulos", mas o de títulos aceita até 360; 60 basta para cartão). Reaproveitei `InstallmentGroupNotFoundException` (404), que já existe para os títulos.
- **Formulário (T7):** o `formDialog` não aceitava campo condicional; ganhou `showWhen` (campo oculto fica desabilitado e vai `null`), mais a regra `.field[hidden]` no CSS.
- **Isolamento (T6):** os `404` das rotas novas estão nos testes de cada rota (`InvoiceIntegrationTest`, `InstallmentPurchaseIntegrationTest`); `CrossUserIsolationIntegrationTest` não foi alterado.

## Revisão com as skills (2026-11-06)

- `web-design-guidelines`: corrigidos `aria-live="polite"` na lista de compras carregada sob demanda e o estado de hover do `summary`. Não aplicado: placeholders sem `…` e datas via `fmtDate`, que seguem a convenção do resto do cliente.
- `accessibility-compliance`: `details/summary`, `ul`, `select` com `label` e foco visível herdado; campos condicionais ocultos ficam desabilitados (saem da ordem de tabulação); situação da fatura em texto, não só cor; o nome acessível do botão "Pagar fatura" contém o rótulo visível e acrescenta o vencimento.
- `responsive-design`: conferido a 375px no navegador (sem rolagem horizontal, compras empilham data e valor); botões da tela com 44px de altura mínima.
- `api-design-principles`: `GET /accounts/{id}/invoices` (recurso sob a conta), `POST /transactions/installments` e `DELETE /transactions/installments/{groupId}`; `201`/`204`/`400`/`404`/`409` coerentes com o resto da API; `409` para conta sem dias; OpenAPI completo (`OpenApiContractIntegrationTest` verde); `PUT` da conta continua sem os dias mantendo os atuais.

### Revisão da mudança "fatura em A pagar e receber" (2026-11-06)

- `web-design-guidelines`: a ação "Ver itens da fatura" é um link (`<a href="#/invoices">`), "Pagar fatura" é botão; ambos com `aria-label` que inclui cartão e vencimento. Aviso de falha ao carregar as faturas em `role="status"`, sem derrubar a lista.
- `accessibility-compliance`: a linha de fatura não tem caixa de seleção; a situação é dita em texto ("Aberta", "A vencer", "Vencida"), não só por cor; o valor que cresce na fatura aberta vem explicado ("fecha em dd/mm, ainda recebe compras").
- `responsive-design`: conferido a 375px (a linha empilha no formato de cartão, sem rolagem horizontal, texto longo quebra em vez de cortar).
- `api-design-principles`: `GET /invoices/payable` (recurso novo, `200`/`401`), fora de `/bills` porque a lista de títulos é paginada e filtrada no servidor; `OpenApiContractIntegrationTest` verde.
- Verificação manual no navegador: linha de fatura em A pagar e receber, pagamento parcial a partir da linha (a linha passa a mostrar "falta R$ 50,00"), "Ver itens da fatura" abre a tela Faturas no cartão, quadro "A pagar" (R$ 220 = fatura 150 + título 70), "Próximos vencimentos" e previsão (R$ 2.780 em 30 dias, sem contar a fatura duas vezes).
- Corrigido na verificação: o `display:flex` na célula de ações desalinhava a linha da tabela; o nome da fatura em "Próximos vencimentos" quebrava junto do status.

### Revisão da mudança "início das faturas e lista única" (2026-11-06)

- `web-design-guidelines`: os filtros têm `label` associado ao campo por `for`/`id`; a ordenação por cabeçalho tem `aria-sort`; o estado vazio da lista é uma mensagem, não uma tabela quebrada.
- `accessibility-compliance`: "Ver compras" é `details/summary` com `aria-label` que traz o período; cada "Pagar fatura" nomeia o cartão e o vencimento; a situação sai em texto, não só cor.
- `responsive-design`: conferido a 375px (a tabela empilha em cartões com os rótulos das colunas, a ordenação vira o seletor "Ordenar por", sem rolagem horizontal).
- `api-design-principles`: `invoicesFrom` opcional em `POST/PUT /accounts` (omitido mantém), `400` com erro no campo para data futura ou sem os dias, sem rota nova; `OpenApiContractIntegrationTest` verde.
- Verificação manual no navegador (dois cartões, fatura antiga de R$ 1.200 trazida por "Contar faturas desde"): resumo (A pagar R$ 2.040, vencido R$ 1.200), uma fatura atual por cartão com "Faturas contam desde", filtro por cartão, ordenação por total e "Ver compras".
- Corrigido na verificação: os rótulos dos filtros não estavam ligados aos campos.
