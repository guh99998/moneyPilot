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
- [ ] **T13** — README, `./mvnw verify` e `node --test`, atualizar o quadro

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
