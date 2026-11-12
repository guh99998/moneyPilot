# Plano — 028 Ajuste dos itens da fatura

## Contexto

A spec 028 (`specs/028-itens-da-fatura/spec.md`, branch `feature/028-itens-da-fatura`) foi aprovada pelo Gustavo em 2026-11-12. Problema: parcela de compra no cartão não se edita (`409`), compra cancelada só pode ser apagada (o histórico some e o que foi pago fica errado), a tela Faturas esconde as atrasadas no fim da lista e o pagamento adiantado de um valor qualquer já funciona, mas a tela não o explica. Resultado esperado: editar parcela (só esta ou esta e as seguintes), estornar ou apagar compra, lista de faturas da mais antiga para a mais nova abrindo nas não pagas, e o adiantamento visível.

Decisões tomadas nesta sessão, que entram na spec antes de codar (passo 0):
- Estorno usa a **categoria global "Estorno"** (receita), criada por migration; a pessoa não escolhe categoria.
- **Só estorno inteiro.** O parcial fica para depois: quem precisar adianta a fatura no valor.
- A lista de faturas **abre filtrada em "Não pagas"**, isto é, todas menos `PAID`, e a aberta paga adiantada continua aparecendo.
- O selo se chama "Paga adiantada".
- **Data do estorno por parcela = a maior entre a data informada e a data da parcela.** O que já foi cobrado é estornado na data informada, e o futuro na data de cada parcela.
- **Compra ou parcela estornada não se edita** (`409`). Em "esta e as seguintes", se alguma parcela do alcance estiver estornada, a edição toda é recusada.

## Passo 0 — Documentos (antes do código)

- `spec.md`: regra 8 (categoria global "Estorno", sem escolha), regra 9 (data = maior entre informada e da parcela; num alcance com parcelas já estornadas, estorna as que faltam, e se não sobrar nenhuma, `409`), nova regra (estornada não se edita, `409`), regra 13 (abre em "Não pagas"). Respostas registradas em "Perguntas em aberto" → "Decisões". Estorno parcial vai para "Fora de escopo". `Status: aprovada`.
- Copiar este plano para `specs/028-itens-da-fatura/plan.md` e criar `tasks.md` (tarefas abaixo, cada uma com o seu CA).
- Quadro: card 028 → **Plano** (`gh project item-edit`, item `PVTI_lAHOBuiChs4Bp_jfzhIPiAg`).
- Commit `docs(invoice): ...`.

## Schema — `V22__add_transaction_refunds.sql` (só adiciona, nenhum dado muda)

```sql
INSERT INTO categories(name, type, user_id) VALUES ('Estorno', 'INCOME', NULL);
ALTER TABLE transactions ADD COLUMN refund_of_transaction_id BIGINT NULL
    REFERENCES transactions(id) ON DELETE CASCADE;
CREATE UNIQUE INDEX uq_transactions_refund_of ON transactions (refund_of_transaction_id)
    WHERE refund_of_transaction_id IS NOT NULL;
```
O `CASCADE` mantém funcionando a exclusão de conta e de usuário (`TransactionRepository:54`, DELETE em massa). O índice único garante um estorno por compra, mesmo com pedidos simultâneos (a violação vira `409`).

## Backend (pacote `transaction`)

**Entidade e resposta**
- `Transaction`: campo `refundOfTransactionId`.
- `TransactionResponse`: `refundOfTransactionId` (no estorno) e `refundTransactionId` (na compra estornada, nulo se não houver), com `@Schema`. Preencher `refundTransactionId` em lote na listagem, seguindo o padrão do `billsByTransaction` em `TransactionService.listTransactions` (uma consulta `findAllByRefundOfTransactionIdIn` por página).
- `TransactionRepository`: `findAllByRefundOfTransactionIdIn`, `existsByRefundOfTransactionId`, `findAllByInstallmentGroupIdAndInstallmentNumberGreaterThanEqual`.
- `CategoryRepository`: buscar a global "Estorno" (`findByNameAndTypeAndUserIdIsNull`).

**Editar** — `PUT /transactions/{id}?scope=ONLY_THIS|THIS_AND_FOLLOWING` (padrão `ONLY_THIS`; enum `EditScope`)
- Em `updateTransaction` sai o `TransactionIsInstallmentException`. Em parcela: `accountId` e `type` têm que ser os atuais (senão `400` no campo), e a categoria, do tipo certo.
- `THIS_AND_FOLLOWING`: aplica descrição, categoria e valor às parcelas de número ≥, e a data só à editada. `scope` fora de parcela com `THIS_AND_FOLLOWING` dá `400`.
- Bloqueios `409`: transferência e baixa (como hoje); **estorno** (`refundOfTransactionId != null`); **compra estornada**, ou qualquer parcela do alcance estornada (nova `TransactionIsRefundedException`).
- Fora de parcela, o comportamento de hoje não muda.

**Estornar** — `POST /transactions/{id}/refund`, corpo `RefundRequest(@NotNull LocalDate date, RefundScope scope)` com `scope` padrão `ONLY_THIS` e valores `ONLY_THIS | THIS_AND_FOLLOWING | WHOLE_PURCHASE` (`enumAsRef = true`). Resposta `201` com a lista de estornos criados.
- Só despesa em conta `CREDIT_CARD` que não seja transferência, baixa ou estorno; senão `409` (`TransactionNotRefundableException`). Escopo de grupo fora de parcela: `400`.
- Cada estorno: `INCOME`, mesmo cartão, mesmo valor, categoria "Estorno", descrição `Estorno: <descrição da compra>`, data `max(date, data da parcela)`, `refundOfTransactionId` = compra.
- Parcelas do alcance já estornadas são puladas; se nenhuma restar, `409`.

**Desfazer e apagar**
- Desfazer o estorno = `DELETE /transactions/{idDoEstorno}`, que apaga só o estorno. A compra volta ao normal sem precisar de rota nova.
- `deleteTransaction` da compra: `409` se ela, ou qualquer parcela do grupo, tiver estorno. O mesmo vale para `deleteInstallmentGroup`.

**Erros e contrato**
- Exceções novas em `exception/` + handlers `409` no `GlobalExceptionHandler`, no formato `ErrorResponse` (copiar o handler de `TransactionIsInstallmentException`, que passa a não ser usado na edição e é removido junto do seu handler, se não sobrar outro uso).
- `TransactionController`: `@Operation`, `@ApiResponse` `200/201/400/404/409` e `@Schema` nos records novos; `OpenApiContractIntegrationTest` verde.
- `MoneyScaleIntegrationTest`: o estorno não recebe valor. Incluir o caso `PUT /transactions/{id}` com `amount` (CA13), se o teste aceitar método além do POST; se não, um teste próprio no arquivo de edição.

## Frontend (JS puro)

- `api.js`: `transactions.update(id, body, { scope })` (query opcional) e `transactions.refund(id, body)`.
- `invoices-list.js`: `EMPTY_FILTERS.status = 'UNPAID'`; `filterInvoices` trata `UNPAID` como `status !== 'PAID'`; opção "Não pagas" no seletor de situação (com "Todas" = `''`). Em `invoices.js`, `DEFAULT_SORT = { key: 'dueDate', dir: 'asc' }`, e o "limpar filtros" e o `selectInvoiceCard` voltam a esse padrão.
- Selo e linha: fatura `OPEN` com `total > 0` e `remaining == 0` → selo "Paga adiantada"; com `paid > 0` e `remaining > 0` → "pago R$ X, falta R$ Y" na linha.
- Diálogo "Pagar fatura" (`openPayInvoiceDialog`): `preview` com uma função pura nova, `paymentPreview(invoices, amountCents)` em `invoices-list.js`, que reparte o valor da mais antiga para a mais nova (não pagas, em centavos) e diz "Abate a fatura com vencimento em dd/mm: vai ficar em R$ 450,00". Precisa receber as faturas do cartão.
- **Novo módulo `views/invoices-items.js`**, para não inchar `invoices.js` (521 linhas): ações em cada linha de compra de "Ver compras" (`purchaseLine`). Pagamentos, cobranças previstas e lançamentos de baixa ficam sem ações.
  - **Editar**: `formDialog` com descrição, categoria (despesa), data e valor; em parcela, o select "Aplicar a" (Só esta parcela / Esta e as seguintes).
  - **Estornar**: data (padrão hoje); em parcela, o select do alcance; o `preview` diz quantas parcelas e as datas.
  - **Apagar**: `confirmDialog` que avisa que a parcela apaga o grupo e sugere estornar.
  - Compra estornada mostra o selo "Estornada" e o botão "Desfazer estorno". A linha do estorno mostra o selo "Estorno".
  - Depois de cada ação, recarrega a lista de faturas.
- `transactions.js`: o estorno aparece como "não editável" (como a transferência). A edição de parcela continua só na tela Faturas.
- `ajuda/faturas.html`: um parágrafo sobre editar, estornar e adiantar. Sem aviso novo em Novidades.

## Tarefas (viram `tasks.md`)

1. Passo 0 (spec, plano, quadro).
2. `V22` + entidade e resposta (base de todas).
3. Editar parcela, só esta e esta e as seguintes; bloqueios (CA1, CA2, CA3, CA9).
4. Estornar, desfazer e bloquear apagar e editar a compra estornada (CA4 a CA8).
5. Contrato OpenAPI, `MoneyScale` e isolamento `404` (CA13, CA14; incluir as rotas no `CrossUserIsolationIntegrationTest`).
6. Front: filtro "Não pagas", ordem crescente, selos e prévia do pagamento (CA10 a CA12).
7. Front: ações dos itens em `invoices-items.js` e `transactions.js`.
8. README (decisões de domínio + linhas da API), ajuda, skills do projeto e checagem a 375px.

Testes primeiro em cada tarefa (TDD), commits separados de código e teste, mensagens em inglês com escopo (`feat(transaction)`, `feat(web)`, `test(transaction)`).

## Testes

- Novo `transaction/TransactionEditInstallmentIntegrationTest` (CA1 a CA3, CA9, editar estornada `409`, `accountId` ou `type` diferente `400`).
- Novo `transaction/TransactionRefundIntegrationTest` (CA4 a CA8: datas por `max`, fatura soma zero via `GET /accounts/{id}/invoices`, `409` repetido, desfazer, apagar bloqueado, categoria "Estorno").
- `InstallmentPurchaseIntegrationTest`: o teste que espera `409` na edição de parcela passa a esperar `200`; isso é mudança de regra aprovada (regra 9 da 016), não ajuste para passar.
- `CrossUserIsolationIntegrationTest`: refund e PUT com `scope` de outro usuário → `404`.
- JS: `invoices-list.test.mjs` (UNPAID, ordem crescente, `paymentPreview`), `invoices-view.test.mjs` (selos, prévia) e novo `invoices-items.test.mjs` (payloads, escopos, ações por tipo de linha).

## Verificação

1. `docker compose up -d`, `./mvnw verify` e `node --test "src/test/js/*.test.mjs"`, as duas suítes verdes.
2. App local (`./mvnw spring-boot:run`, usuário de teste local): cartão com dias, compra 10x com parcela atual 4; editar a 5/10 "esta e as seguintes"; estornar a compra inteira e conferir as datas; desfazer; pagar R$ 50 de uma fatura de R$ 500 e ver "pago/falta" e a prévia; pagar a aberta inteira e ver "Paga adiantada"; lista abrindo em "Não pagas", da mais antiga primeiro.
3. Checagem a 375px e 1280px (Playwright).
4. Skills `web-design-guidelines`, `accessibility-compliance`, `responsive-design` e `api-design-principles`; corrigir o que for apontado e anotar a revisão em `tasks.md`.
5. Merge `--no-ff` em `develop` só no fim; `main` só com o ok do Gustavo.

## Arquivos principais

- `src/main/resources/db/migration/V22__add_transaction_refunds.sql` (novo)
- `transaction/{Transaction,TransactionResponse,TransactionRepository,TransactionService,TransactionController}.java`, `RefundRequest`, `RefundScope`, `EditScope` (novos)
- `category/CategoryRepository.java`; `exception/` + `GlobalExceptionHandler`
- `static/js/api.js`, `views/invoices.js`, `views/invoices-list.js`, `views/invoices-items.js` (novo), `views/transactions.js`, `ajuda/faturas.html`
- `README.md`, `specs/028-itens-da-fatura/{spec,plan,tasks}.md`
