# Tarefas — 028 Ajuste dos itens da fatura

- [x] 1. Passo 0: spec aprovada, plano, tarefas, quadro.
- [x] 2. `V22` + entidade e resposta (base de todas).
- [x] 3. Editar parcela, só esta e esta e as seguintes; bloqueios (CA1, CA2, CA3, CA9).
- [x] 4. Estornar, desfazer e bloquear apagar e editar a compra estornada (CA4 a CA8).
- [x] 5. Contrato OpenAPI, `MoneyScale` e isolamento `404` (CA13, CA14).
- [x] 6. Front: filtro "Não pagas", ordem crescente, selos e prévia do pagamento (CA10 a CA12).
- [x] 7. Front: ações dos itens em `invoices-items.js` e `transactions.js`.
- [x] 8. README, ajuda, skills do projeto e checagem a 375px.

## Revisão com as skills do projeto

Revisão feita em 2026-11-12 com as skills do projeto, sobre `invoices.js`, `invoices-list.js`, `invoices-items.js`, `transactions.js` e as rotas novas.

- **web-design-guidelines:** ações destrutivas passam por `confirmDialog`; os botões das linhas levam o nome da compra no `aria-label` (começa pelo texto visível); as prévias usam `aria-live="polite"`; o selo e o estado nunca dependem só de cor. Nada a corrigir.
- **accessibility-compliance:** botões nativos, rótulos nos campos do diálogo, alvo de toque de 44px nas ações da linha, texto de ajuda no campo "Aplicar a". Nada a corrigir.
- **responsive-design:** checado a 375px no app local (cartão com parcelada, estorno, compra estornada e pagamento parcial). Corrigido: a linha "pago R$ X, falta R$ Y" estourava a borda do cartão e a lista de compras espremia o nome do cartão na tabela empilhada (CSS em `app.css`); sem rolagem horizontal depois.
- **api-design-principles:** `POST /transactions/{id}/refund` (ação sobre o recurso, `201` com a lista criada), `PUT` com `scope` em query, desfazer por `DELETE` do estorno, erros `409`/`400`/`404` no formato `ErrorResponse` e contrato OpenAPI coberto pelo `OpenApiContractIntegrationTest`. Nada a corrigir.

Verificação: `./mvnw verify` com 671 testes e `node --test` com 376, sem falhas. A prévia do estorno em parcela diz quantas parcelas e a regra da data, sem listar cada data (a linha só conhece a parcela clicada).
