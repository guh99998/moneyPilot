# Plano — 028 Ajuste dos itens da fatura

## Contexto

A spec 028 (`specs/028-itens-da-fatura/spec.md`, branch `feature/028-itens-da-fatura`) foi aprovada pelo Gustavo em 2026-11-12. Problema: parcela de compra no cartão não se edita (`409`), a tela Faturas esconde as atrasadas no fim da lista e o pagamento adiantado de um valor qualquer já funciona, mas a tela não o explica. Resultado esperado: editar parcela (só esta ou esta e as seguintes), apagar com aviso claro, lista de faturas da mais antiga para a mais nova abrindo nas não pagas, e o adiantamento visível.

**Estorno retirado do escopo (2026-11-12):** a primeira versão do plano incluía estornar compras do cartão (categoria global "Estorno", `V22`, rota `POST /transactions/{id}/refund`, escopos, desfazer). Foi considerado complexo demais para esta spec e fica para uma spec futura. O que já estava implementado foi guardado na branch `feature/refund-parked` (não faz parte desta entrega).

Decisões:
- A lista de faturas **abre filtrada em "Não pagas"**, isto é, todas menos `PAID`, e a aberta paga adiantada continua aparecendo.
- O selo se chama "Paga adiantada".
- Na fatura atual, com pagamento parcial, o valor principal é o que **falta pagar**; o total e o já pago ficam nos detalhes.

## Schema

Nenhuma migration. Nenhum dado existente muda.

## Backend (pacote `transaction`)

**Editar** — `PUT /transactions/{id}?scope=ONLY_THIS|THIS_AND_FOLLOWING` (padrão `ONLY_THIS`; enum `EditScope`)
- Em `updateTransaction` sai o `TransactionIsInstallmentException` (removido junto do handler). Em parcela: `accountId` e `type` têm que ser os atuais (senão `400` no campo), e a categoria, do tipo certo.
- `THIS_AND_FOLLOWING`: aplica descrição, categoria e valor às parcelas de número ≥, e a data só à editada. `scope` fora de parcela com `THIS_AND_FOLLOWING` dá `400`.
- Transferência e baixa de título continuam `409`. Fora de parcela, o comportamento de hoje não muda.
- `TransactionRepository.findAllByInstallmentGroupIdAndInstallmentNumberGreaterThanEqual`.

**Contrato**
- `TransactionController`: `@Operation` do `PUT` e do apagar atualizados; `OpenApiContractIntegrationTest` verde.
- `MoneyScaleIntegrationTest` e `CrossUserIsolationIntegrationTest` cobrem o `PUT` com `scope`.

## Frontend (JS puro)

- `api.js`: `transactions.update(id, body, query)` (query opcional, para o `scope`).
- `invoices-list.js`: `EMPTY_FILTERS.status = 'UNPAID'`; `filterInvoices` trata `UNPAID` como `status !== 'PAID'`; `isPaidInAdvance`; `paymentPreview(invoices, amountCents)`, que reparte o valor da mais antiga para a mais nova (não pagas e não futuras, em centavos).
- `invoices.js`: `DEFAULT_SORT` crescente; opção "Não pagas"; selo "Paga adiantada"; "pago R$ X, falta R$ Y" na linha; fatura atual com o que falta pagar como valor principal; diálogo "Pagar fatura" com a prévia.
- **Novo módulo `views/invoices-items.js`**: ações em cada linha de compra de "Ver compras" (pagamentos e baixas ficam sem ações). **Editar** (descrição, categoria, data e valor; em parcela, "Aplicar a") e **Apagar** (`confirmDialog` que avisa que a parcela apaga o grupo). Depois de cada ação, recarrega as faturas.
- `ajuda/faturas.html`: parágrafos sobre editar, apagar e adiantar. Sem aviso novo em Novidades.

## Tarefas (viram `tasks.md`)

1. Passo 0 (spec, plano, quadro).
2. Editar parcela (só esta e esta e as seguintes), bloqueios (CA1 a CA4).
3. Contrato OpenAPI, `MoneyScale` e isolamento `404` (CA8, CA9).
4. Front: filtro "Não pagas", ordem crescente, selos, prévia do pagamento e fatura atual (CA5, CA6, CA7, CA10).
5. Front: ações dos itens em `invoices-items.js`.
6. Retirar o estorno do que já estava feito (depois do corte de escopo).
7. README, ajuda, skills do projeto e checagem a 375px.

Testes primeiro em cada tarefa (TDD), commits separados de código e teste, mensagens em inglês com escopo.

## Testes

- `transaction/TransactionEditInstallmentIntegrationTest` (CA1 a CA4, CA8, `accountId` ou `type` diferente `400`, `scope` fora de parcela `400`).
- `InstallmentPurchaseIntegrationTest`: o teste que esperava `409` na edição de parcela passa a esperar `200`; é mudança de regra aprovada (regra 9 da 016), não ajuste para passar.
- `CrossUserIsolationIntegrationTest`: `PUT` com `scope` de outro usuário → `404`.
- JS: `invoices-list.test.mjs` (UNPAID, ordem, `paymentPreview`), `invoices-view.test.mjs` (selos, prévia, fatura atual) e `invoices-items.test.mjs` (payloads, escopos, ações por tipo de linha).

## Verificação

1. `docker compose up -d`, `./mvnw clean verify` e `node --test "src/test/js/*.test.mjs"`, as duas suítes verdes.
2. App local: compra 10x com parcela atual 4; editar a 5/10 "esta e as seguintes"; pagar R$ 50 de uma fatura e ver "pago/falta", a prévia e o valor principal; pagar a aberta inteira e ver "Paga adiantada"; lista abrindo em "Não pagas", da mais antiga primeiro.
3. Checagem a 375px e 1280px.
4. Skills `web-design-guidelines`, `accessibility-compliance`, `responsive-design` e `api-design-principles`; anotar a revisão em `tasks.md`.
5. Merge `--no-ff` em `develop` só no fim; `main` só com o ok do Gustavo.

## Arquivos principais

- `transaction/{TransactionService,TransactionController,TransactionRepository}.java`, `EditScope`
- `static/js/api.js`, `views/invoices.js`, `views/invoices-list.js`, `views/invoices-items.js` (novo), `ajuda/faturas.html`
- `README.md`, `specs/028-itens-da-fatura/{spec,plan,tasks}.md`
