# Tarefas — 028 Ajuste dos itens da fatura

- [x] 1. Passo 0: spec aprovada, plano, tarefas, quadro.
- [x] 2. Editar parcela, só esta e esta e as seguintes; bloqueios (CA1 a CA4).
- [x] 3. Contrato OpenAPI, `MoneyScale` e isolamento `404` (CA8, CA9).
- [x] 4. Front: filtro "Não pagas", ordem crescente, selos, prévia do pagamento e fatura atual (CA5, CA6, CA7, CA10).
- [x] 5. Front: ações dos itens (editar e apagar) em `invoices-items.js`.
- [x] 6. Retirar o estorno do que já estava feito (corte de escopo em 2026-11-12; código guardado em `feature/refund-parked`; ideia na spec 029).
- [x] 7. README, ajuda, skills do projeto e checagem a 375px, refeitos sem o estorno.

## Revisão com as skills do projeto

Revisão feita em 2026-11-12 com as skills do projeto, sobre `invoices.js`, `invoices-list.js`, `invoices-items.js` e a rota `PUT /transactions/{id}` com `scope`. O estorno saiu depois da primeira revisão; o que sobrou é um subconjunto do que foi revisado.

- **web-design-guidelines:** a ação destrutiva passa por `confirmDialog`; os botões das linhas levam o nome da compra no `aria-label` (começa pelo texto visível); as prévias usam `aria-live="polite"`; o selo e o estado nunca dependem só de cor. Nada a corrigir.
- **accessibility-compliance:** botões nativos, rótulos nos campos do diálogo, alvo de toque de 44px nas ações da linha, texto de ajuda no campo "Aplicar a". Nada a corrigir.
- **responsive-design:** checado a 375px no app local (cartão com compra parcelada e pagamento parcial). Corrigido: a linha "pago R$ X, falta R$ Y" estourava a borda do cartão e a lista de compras espremia o nome do cartão na tabela empilhada (CSS em `app.css`); sem rolagem horizontal depois.
- **api-design-principles:** `PUT` com `scope` em query, erros `409`/`400`/`404` no formato `ErrorResponse` e contrato OpenAPI coberto pelo `OpenApiContractIntegrationTest`. Nada a corrigir.

Verificação: `./mvnw clean verify` com 661 testes e `node --test` com 371, sem falhas.
