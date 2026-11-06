# 016 — Plano técnico

**Spec:** [spec.md](spec.md)
**Status:** aprovado (2026-11-06)

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
