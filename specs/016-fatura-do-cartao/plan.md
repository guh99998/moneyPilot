# 016 — Plano técnico

**Spec:** [spec.md](spec.md)
**Status:** aprovado (2026-11-06), inclusive a seção "Mudança: fatura em A pagar e receber", inclusive "Mudança: início das faturas e lista única"

## Abordagem

A fatura é calculada em leitura, sem gravar nada, como o saldo. Um cálculo puro (`InvoiceCycle`) diz em que ciclo cai cada data e quando ele vence. O `InvoiceService` agrupa por dia os lançamentos do cartão desde `invoicesFrom`, soma por ciclo e abate os pagamentos (transferências para o cartão) da fatura mais antiga para a mais nova. As duas previsões reaproveitam esse serviço para cada cartão desmarcado com dias.

## Banco de dados

`V20__add_credit_card_invoices.sql` (a `develop` termina na V17; V18 está na 027 e V19 na 015, então **o merge segue 027, 015, 016**):

```sql
ALTER TABLE accounts ADD COLUMN closing_day SMALLINT;
ALTER TABLE accounts ADD COLUMN due_day SMALLINT;
ALTER TABLE accounts ADD COLUMN invoices_from DATE;
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_invoice_days CHECK (
    (closing_day IS NULL AND due_day IS NULL AND invoices_from IS NULL)
    OR (closing_day BETWEEN 1 AND 31 AND due_day BETWEEN 1 AND 31 AND invoices_from IS NOT NULL));

ALTER TABLE transactions ADD COLUMN installment_group_id UUID;
ALTER TABLE transactions ADD COLUMN installment_number SMALLINT;
ALTER TABLE transactions ADD COLUMN installment_total SMALLINT;
CREATE INDEX idx_transactions_installment_group ON transactions (installment_group_id) WHERE installment_group_id IS NOT NULL;
```

**Impacto em dados existentes: nenhum.** Só colunas nulas; nenhum número muda no deploy. Reversível com `DROP COLUMN`. A regra "só em cartão" fica na aplicação (o `type` é string).

## Cálculo de ciclo (`account/InvoiceCycle`, puro, sem banco)

- `closingDate(YearMonth)` = `min(closingDay, tamanho do mês)`.
- Uma data `d` pertence ao ciclo que começa no último fechamento `<= d`. A compra no dia do fechamento já cai no ciclo seguinte (regra 4).
- A fatura de um ciclo fecha no fechamento seguinte; `dueDate` é a primeira data com `dueDay` **estritamente depois** do fechamento (se `dueDay > closingDay`, no mesmo mês; senão, no mês seguinte; sempre limitado ao último dia do mês).
- Métodos: `cycleStart(LocalDate)`, `cycleOf(LocalDate)` (início, fim, fechamento, vencimento).

## API

| Método | Rota | Mudança |
|---|---|---|
| POST/PUT | `/accounts`, `/accounts/{id}` | `closingDay`, `dueDay` opcionais (`@Min(1) @Max(31)`) |
| GET | `/accounts`, `/accounts/{id}` | resposta expõe `closingDay`, `dueDay`, `invoicesFrom` |
| GET | `/accounts/{id}/invoices` | **novo**: lista de faturas |
| POST | `/transactions/installments` | **novo**: compra parcelada (devolve as N parcelas) |
| DELETE | `/transactions/installments/{groupId}` | **novo**: apaga o grupo |
| PUT/DELETE | `/transactions/{id}` | PUT em parcela dá `409`; DELETE em parcela apaga o grupo, como na transferência |
| GET | `/reports/cash-flow-forecast`, `/reports/monthly-forecast` | faturas somam em `payable` (regra 10) |

Regras de `AccountRequest`:
- Só um dos dois dias, ou dias em conta que não é cartão: `400`.
- PUT sem os dois dias **mantém** os dias atuais (protege o PUT de `profile-availability.js`). Não há como apagar os dias sem trocar o tipo: está fora do escopo.
- Primeira vez que os dias aparecem: `countsAsAvailable` vira `false` (salvo escolha explícita no pedido) e `invoicesFrom` = início do ciclo de hoje (`Clock`). Mudar os dias depois não mexe em `invoicesFrom`.
- Trocar o tipo para outro que não cartão limpa os três campos.

`GET /accounts/{id}/invoices` → lista (não paginada) de `InvoiceResponse`: `periodStart`, `periodEnd`, `closingDate`, `dueDate`, `total`, `paid`, `remaining`, `status`. Vai do ciclo de `invoicesFrom` até o último ciclo com lançamento ou o ciclo atual. **Conta sem os dias responde `409`** (estado da conta não permite; `400` fica para dado mal formado). Conta de outro usuário: `404`.

`POST /transactions/installments`: `accountId`, `categoryId`, `description`, `totalAmount` (`@Digits(17,2)`), `installments` (2 a 60, como no plano de títulos), `date`. Categoria de despesa. A parcela `i` cai em `date.plusMonths(i-1)` (o `plusMonths` já limita ao último dia do mês); o centavo que sobra fica na primeira.

## Fatura: regras de cálculo (`account/InvoiceService`)

1. Consulta única, agrupada por dia (nada de carregar lançamento por lançamento): despesas, receitas e pagamentos do cartão com `date >= invoicesFrom`. Pagamento = perna `INCOME` de transferência (`transfer_group_id` não nulo).
2. `total` do ciclo = despesas − receitas (sem transferências). Total negativo (estorno maior que a compra) vira crédito que sobra para as seguintes.
3. Pagamentos somados num único montante, abatido do ciclo mais antigo ao mais novo: `paid = min(montante restante, max(total, 0))`.
4. Situação: `FUTURE` se o ciclo começa depois de hoje; `OPEN` se contém hoje; `PAID` se encerrado e `remaining = 0`; `OVERDUE` se encerrado, `dueDate < hoje` e `remaining > 0`; senão `CLOSED`. Ciclo atual que já está quitado continua `OPEN` (ainda recebe compras).
5. Saldo inicial e lançamentos anteriores a `invoicesFrom` nunca entram.

## Previsão (`ReportService`)

`ReportService` chama `InvoiceService.projectedPayables(userId, to)` para os cartões com dias e `countsAsAvailable = false`. Cada fatura com `remaining > 0` e `dueDate <= to` vira uma linha igual às de `BillDueTotals`: vencida cai no primeiro período e conta em `OverdueSummary`; as demais caem pelo vencimento. Cartão marcado ou sem dias não projeta. `currentBalance` não muda (a 015 já o define). A fatura soma só em `payable`; não cria `Bill` nenhum, então "A pagar" e alertas da Visão geral ficam como estão (decisão da spec).

## Cliente web

- `views/accounts.js`: campos `closingDay`/`dueDay` quando o tipo é cartão. **Verificar na implementação** se o `formDialog` aceita campo condicional; se não aceitar, o formulário troca os campos ao mudar o tipo, sem mexer no `formDialog` além do mínimo.
- Novo `views/invoices.js` e item **Faturas** no menu: seletor de cartão (só os com dias; senão, orientação para configurar), fatura atual em destaque (total, vencimento, situação), lista das demais, compras do ciclo (`api.transactions.list` com `from`/`to` e `accountId`), ações **Pagar fatura** (abre transferência já preenchida com o valor restante e a data de hoje) e **Compra parcelada**.
- `views/dashboard.js`: `savedCents` ignora cartão; `loadBalances` passa a levar o `type`.
- `views/profile-availability.js`: cartão com dias diz "Entra pela fatura" em vez de "Guardado".
- Aviso de Novidades no padrão de `news-available.js` e página em `static/ajuda/`.
- Sem dependência nova, sem build.

## Testes

| Critério | Prova |
|---|---|
| CA1 | `AccountIntegrationTest`: `400` para dias sem par e em conta de outro tipo; troca de tipo limpa |
| CA2 | `AccountIntegrationTest`: primeira vez desmarca e define `invoicesFrom`; cartão sem dias igual; PUT sem dias mantém |
| CA3 | `InvoiceCycleTest` (unitário): fechamento 31 em fevereiro, dia do fechamento, vencimento antes/depois, virada de ano |
| CA4 | `InvoiceIntegrationTest`: estorno, transferência ignorada, compra anterior a `invoicesFrom` |
| CA5 | `InvoiceIntegrationTest`: parcial, a mais, atraso |
| CA6 | `InstallmentIntegrationTest`: N parcelas, centavo na primeira, `409` na edição, apagar grupo |
| CA7 | `CashFlowForecastIntegrationTest` e `MonthlyForecastIntegrationTest`: desmarcado projeta, marcado não, pagar não conta duas vezes |
| CA8 | `ReportIntegrationTest`, `AccountBalanceIntegrationTest`, teste JS do dashboard (Guardado sem cartão) |
| CA9 | `CrossUserIsolationIntegrationTest` |
| CA10 | `OpenApiContractIntegrationTest`, `MoneyScaleIntegrationTest` (inclui `/transactions/installments`) |
| CA11 | testes JS de `invoices.js`/`accounts.js`; verificação manual no navegador, mobile incluído |

Suítes completas: `./mvnw verify` e `node --test "src/test/js/*.test.mjs"`. Skills do projeto antes de dar por pronto: `web-design-guidelines`, `accessibility-compliance`, `responsive-design` e `api-design-principles`, com a revisão anotada em `tasks.md`.

## Riscos e alternativas descartadas

- **Ordem das migrations.** V20 só pode ir depois de V18 (027) e V19 (015); fora disso o Flyway recusa.
- **Parcelar só em cartão?** Proponho `400` para conta que não é cartão, já que a spec só trata de cartão. A confirmar abaixo.
- **Saída do cartão por transferência** (adiantamento de dinheiro) não entra na fatura: só pagamentos de entrada abatem. Raro; fica registrado como fora de escopo.
- **Fatura com muitos ciclos** (anos de histórico): a consulta é agrupada por dia, então o custo é pequeno mesmo na VPS de 1 vCPU.
- Alternativa descartada: gravar a fatura numa tabela. Duplica o dado e exige reconciliar a cada edição de compra; o saldo já é calculado em leitura.
- Alternativa descartada: título (`Bill`) para a fatura. Apareceria no "A pagar" e nos alertas, contra a decisão da spec.

## Pontos para você confirmar

1. `POST /transactions/installments` aceita só conta do tipo cartão (`400` nas outras)?
2. Conta sem os dias em `GET /accounts/{id}/invoices` responde `409`?
3. Não haverá como apagar os dias sem trocar o tipo (fora de escopo)?

## Deploy

Backup do banco antes, a cargo do Gustavo. A V20 só adiciona colunas nulas; depois do deploy nenhum número muda até alguém informar os dias de um cartão. O aviso em Novidades sai junto.

## Mudança: fatura em A pagar e receber (2026-11-06)

Regras 17 a 23 da spec. A fatura continua **calculada**, nunca um `Bill`: a baixa de um título cria uma despesa na conta que paga, o que contaria a compra e o pagamento como despesa. O pagamento correto é a transferência para o cartão, que já existe. Sem migration nova.

### Backend

- `InvoiceService.payableInvoices(userId)` devolve as faturas **abertas, fechadas e vencidas com `remaining > 0`** de todos os cartões com dias (marcados ou não no disponível; a futura fica de fora). Cada item leva o cartão (`accountId`, `accountName`) e os campos da fatura. É a base das três integrações abaixo e reusa `invoicesOf`.
- **Rota nova:** `GET /api/v1/invoices/payable` → lista de `PayableInvoiceResponse` (`accountId`, `accountName`, `periodStart`, `periodEnd`, `closingDate`, `dueDate`, `total`, `paid`, `remaining`, `status`), da que vence primeiro à que vence depois. Novo `InvoiceController` (tag "Faturas"), OpenAPI completo, `404` não se aplica (só devolve o que é da pessoa logada). Fica fora de `/bills` de propósito: a lista de títulos é paginada e filtrada no servidor, e uma fatura calculada não cabe nessa consulta.
- **Quadro "A pagar"** (`ReportService.getBillsSummary`): soma `count` e `remaining` das faturas em `payable`, e as vencidas também em `payable.overdue`. `BillTypeSummaryResponse` não muda de formato.
- **Alertas** (`AlertService.compute`): as faturas **vencidas** entram nos totais de `PAYABLE_OVERDUE`; as **fechadas** com vencimento em até 7 dias, nos de `PAYABLE_DUE_SOON` (com `lastDueDate` o maior vencimento). A aberta não entra. O fingerprint já é `quantidade|total`, então pagar ou comprar mais faz o alerta voltar ou sumir.
- `ReportRepository`, `BillRepository` e a previsão não mudam.

### Cliente web

- `views/invoices.js`: exporta `selectInvoiceCard(id)` (grava o cartão escolhido antes de navegar) e `openPayInvoiceDialog({ card, amount, accounts, onDone })`, extraído do diálogo de pagamento de hoje, para a tela A pagar e receber reusar.
- `views/bills.js`: na primeira página, busca `api.invoices.payable()` junto com a lista e mostra as linhas no topo, só se os filtros combinarem (regra 20, função pura e testada). Linha com selo de situação, "Aberta" com o texto de em andamento, valor total e "falta R$ X" se parcial, e os botões "Ver itens da fatura" e "Pagar fatura". Sem checkbox. Falha ao buscar as faturas não derruba a lista: mostra um aviso discreto.
- `js/api.js`: `invoices.payable()`.
- `views/dashboard.js`: "Próximos vencimentos" busca `api.invoices.payable()` junto com os títulos em aberto, mistura os dois por vencimento (função pura `mergeUpcoming`, mais próximo primeiro, 6 itens) e mostra a fatura com "Fatura do <cartão>", o selo da situação e o link para Faturas. Falha ao buscar as faturas não derruba o cartão.
- `alert-targets.js` e o clique no quadro "A pagar" já abrem a tela com filtros que mostram as faturas; só confirmar nos testes.
- Ajuda: `a-pagar-e-receber.html` e `faturas.html` explicam a linha; o aviso em Novidades da fatura ganha uma frase.

### Testes

| Critério | Prova |
|---|---|
| CA12 | `InvoicePayableIntegrationTest`: `GET /invoices/payable` com aberta, fechada, vencida e futura; só vêm as três primeiras, com `remaining > 0` |
| CA13 | teste JS da regra 20 (tipo, status, conta, vencimento, categoria) e da linha sem checkbox |
| CA14 | teste JS: "Ver itens da fatura" grava o cartão e aponta para `#/invoices`; "Pagar fatura" chama a transferência |
| CA15 | `BillsSummaryIntegrationTest` (ou o teste do relatório existente): faturas somam em `payable` e `payable.overdue`; título comum não muda |
| CA16 | `AlertIntegrationTest`: vencida e fechada em até 7 dias geram alerta, aberta não; pagar a fatura apaga o alerta |
| CA18 | teste JS de `mergeUpcoming` (ordem por vencimento, limite de 6, fatura com cartão e selo) e do cartão da Visão geral |
| CA17 | `InvoiceForecastIntegrationTest` e `AvailableBalanceIntegrationTest` seguem verdes sem mudança |

Mais: `OpenApiContractIntegrationTest` para a rota nova, `./mvnw verify`, `node --test`, verificação manual no navegador (desktop e mobile) e as skills do projeto.

### Riscos

- **Número que sobe:** a fatura aberta cresce a cada compra e o quadro "A pagar" sobe junto. É o comportamento decidido; o selo "Aberta" e o texto de em andamento deixam isso claro.
- **Linhas fora da paginação e da ordenação:** ficam sempre no topo da primeira página. Em geral são de 0 a 3, então não pesa.
- **Alerta com a fatura aberta:** ignorado de propósito, para não avisar de um valor que ainda cresce.
- **Alternativa descartada:** gravar a fatura como `Bill`. Valor desatualizado quando uma compra muda, e a baixa criaria despesa em dobro.

## Mudança: início das faturas e lista única (2026-11-06)

Regras 25 a 30 da spec. Sem migration: `accounts.invoices_from` já existe.

### Backend: `invoicesFrom` editável

- `AccountRequest`: `LocalDate invoicesFrom` (opcional, com `@Schema`). O construtor de 6 argumentos continua existindo para os testes atuais.
- `AccountService`: em `applyInvoiceDays` (e quando só `invoicesFrom` vem numa edição de cartão que já tem dias), o início gravado é `InvoiceCycle.cycleOf(data).start()` com os dias vigentes (os novos, se vieram no mesmo pedido). Omitido: primeira vez usa o ciclo de hoje; depois mantém.
- Validação (`InvalidInvoiceDaysException`, `400` com `fieldErrors.invoicesFrom`): data depois de hoje (`Clock`); `invoicesFrom` em conta que não é cartão ou em cartão sem dias (nem nos dias do pedido).
- `InvoiceService` e o resto não mudam: já leem `invoicesFrom`. Fatura antiga não quitada já aparece como vencida em A pagar e receber, Visão geral e alertas, porque isso sai de `payableOf`.
- OpenAPI: o campo novo no pedido, descrição do `400`.

### Cliente web

- `views/accounts.js`: campo "Contar faturas desde" (data, só para cartão), com a dica "Só conta compras a partir do ciclo dessa data. Para trazer uma fatura antiga, escolha uma data anterior." `accountPayload` envia `invoicesFrom` só junto com os dois dias. O formulário de edição já vem preenchido com o valor atual.
- `views/invoices.js` **reescrito** como lista única:
  - Carrega as contas e, em paralelo, `api.accounts.invoices(id)` de cada cartão com dias. Cada linha é `{ card, invoice, payAmount }`, onde `payAmount` soma o que falta até aquela fatura **no próprio cartão, sobre a lista completa** (antes de filtrar).
  - Funções puras e testadas: `filterInvoices(rows, filters)`, `sortInvoices(rows, sort)`, `summarize(rows)` (a pagar, vencido, quantidade de abertas) e `payableThrough`.
  - Estado do módulo: `filters { cardId, status, dueFrom, dueTo }` e `sort` (padrão vencimento, da mais nova para a mais antiga). `selectInvoiceCard(id)` passa a **zerar os filtros e fixar o cartão** (regra 30).
  - Interface: resumo; um destaque por cartão (a fatura atual, com "Nova compra", "Compra parcelada", "Pagar fatura" e o texto "Faturas contam desde dd/mm/aaaa"); filtros recolhíveis (`collapsibleFilters`); tabela com cabeçalho ordenável (`sortHeader`, `sortSelect` no celular), empilhando em cartões no mobile (`stackable`); "Ver compras" abre uma linha com as compras do ciclo, carregadas sob demanda.
  - Sem rota nova de API.
- Ajuda (`faturas.html`) e README descrevem os filtros e o campo novo.

### Testes

| Critério | Prova |
|---|---|
| CA19 | `AccountInvoiceConfigIntegrationTest`: normaliza para o início do ciclo; futura, sem dias e conta que não é cartão dão `400`; omitido na edição mantém; informado na edição muda |
| CA20 | `InvoiceIntegrationTest` (ou `InvoicePayableIntegrationTest`): com início anterior, compras antigas entram e a fatura vencida aparece em `/invoices/payable`; mover para a frente esconde de novo |
| CA21 | testes JS de `filterInvoices`, `sortInvoices` e da tabela (todas as faturas de dois cartões misturadas; cabeçalhos ordenáveis) |
| CA22 | testes JS de `summarize`, do destaque por cartão, do texto "desde" e de `selectInvoiceCard` fixando o filtro |

Mais: `OpenApiContractIntegrationTest`, `./mvnw verify`, `node --test`, verificação manual (dois cartões, fatura antiga, filtros, ordenação, mobile) e as skills do projeto.

### Riscos

- **Faturas antigas viram dívida:** ao recuar o início, o que não foi pago no app aparece como vencido e alerta. A dica do campo explica; o jeito de limpar é registrar o pagamento antigo (transferência com a data da época).
- **Muitos ciclos:** cada cartão pode ter dezenas de ciclos. Filtrar no navegador aguenta, e as compras só carregam quando a pessoa abre a linha.
- **Número de requisições:** uma por cartão com dias; poucos cartões, então aceitável. Se pesar, vira uma rota única.
- **Tela atual substituída:** o destaque e a lista por cartão de hoje desaparecem em favor da lista única; os testes de `invoices-view` serão reescritos junto.

## Mudança: compra recorrente no cartão (2026-11-09)

Contexto: a recorrência existe só em "A pagar e receber" (`BillRecurrence`). Para uma assinatura no cartão, o título teria de ser baixado à mão todo mês, a previsão o poria na data dele (não no vencimento da fatura) e a fatura não mostraria a cobrança que vem. Regras 31 a 38.

### Backend

- **Migration `V21__add_recurring_card_purchases.sql`** (só adiciona): tabela `recurring_purchases` (`id`, `user_id`, `account_id` NOT NULL com `ON DELETE CASCADE`, `category_id`, `description`, `amount NUMERIC(19,2)`, `day_of_month`, `start_date`, `end_date`, `active`, `generated_until`, timestamps) e coluna nula `transactions.recurring_purchase_id` (FK `ON DELETE SET NULL`).
- **Pacote `account/`** (junto de `InvoiceService`): `RecurringPurchase`, repositório, `RecurringPurchaseRequest/Response`, `RecurringPurchaseService`, `RecurringPurchaseController`.
- **Rotas**: `GET/POST /recurring-purchases`, `GET/PUT/DELETE /recurring-purchases/{id}`, `POST /recurring-purchases/{id}/deactivate`, `POST /recurring-purchases/materialize`. Tudo escopado ao usuário (`404`).
- **Geração**: o clamp de dia vira utilitário comum (`MonthDays`), usado por `BillService.dueDateFor` e pela geração. `generatedUntil` avança com `UPDATE ... WHERE generated_until = :anterior`, seguro em chamadas simultâneas. "Hoje" vem do `Clock`.
- **Fatura**: `InvoiceService.invoicesOf(account, today, horizon)` soma as ocorrências projetadas (depois de `generatedUntil`, até o horizonte) aos totais do ciclo e devolve `projectedTotal`. A previsão (`payableInvoices(userId, to)`) passa o `to`; a tela Faturas usa o fim do ciclo seguinte; A pagar e receber, Visão geral e alertas usam horizonte nulo (sem projeção).
- **Bloqueio (regra 38)**: `BillService.createBill/updateBill/createInstallmentPlan` e `BillRecurrenceService` create/update com conta = cartão com dias levam a `400` no campo `accountId`. `settle` e `bulkSettle` não mudam.
- **Exclusão de conta (spec 012)**: `AccountDeletionService` apaga as recorrências de compra antes das contas.

### Cliente web

- `views/invoices.js`: botão "Compra recorrente" no destaque do cartão, diálogo com prévia das datas, lista das recorrências do cartão (editar, desativar, apagar) e selo "Prevista" nas cobranças projetadas.
- `ensureRecurringBills` também chama o `materialize` das compras recorrentes (mesmo cache por sessão).
- `bills.js` `accountOptions`: tira cartões com dias dos formulários de título e recorrência, com a dica de usar Compra recorrente em Faturas.
- `views/transactions.js`: cobrança gerada mostra "Recorrente".
- Ajuda `ajuda/faturas.html` e Novidade `news-invoices.js`.

### Testes

`RecurringPurchaseIntegrationTest`, `RecurringPurchaseInvoiceIntegrationTest`, casos do bloqueio nos testes de título, rota em `MoneyScaleIntegrationTest`, `OpenApiContractIntegrationTest`, testes JS para payload e prévia.

### Riscos

- **Dupla contagem**: a projeção começa depois de `generatedUntil`; a geração avança a marca na mesma transação que grava as despesas.
- **Concorrência**: o `UPDATE` condicional garante que dois pedidos simultâneos não gerem a mesma cobrança duas vezes.
- **Produção**: a migration só adiciona; o bloqueio da regra 38 não toca títulos existentes.
