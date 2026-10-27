# 007 — Plano técnico

**Spec:** [spec.md](spec.md)
**Status:** proposto

## Abordagem

Os alertas **não são armazenados**: a cada requisição o servidor calcula a situação atual do usuário e devolve a lista. Só a dispensa é gravada. Novo pacote `notification` (entidade, repositório, records, service e controller juntos), que reaproveita queries já existentes.

- **Hoje** vem de `LocalDate.now(clock)` (`Clock` injetado, `America/Sao_Paulo`); a janela de "a vencer" é `[hoje, hoje + 7]`, fixa.
- **Títulos:** duas queries novas em `BillRepository`, escopadas por `userId` e `status = OPEN`, agrupadas por `type` e devolvendo quantidade, soma e maior vencimento: uma para `dueDate` entre `hoje` e `hoje + 7` (a vencer) e outra para `dueDate < hoje` (vencidas). Título que vence hoje cai só na primeira. Baixado e cancelado nunca entram.
- **Orçamento:** reaproveita `ReportRepository.findBudgetVsActual(userId, mês, ano, ...)` do mês corrente, a mesma conta da tela Orçamentos (despesas, sem transferências). Comparação por multiplicação, sem dividir: `gasto > orçado` é estourado; `gasto >= 0,8 × orçado` (e não estourado) é "em 80%". Orçado igual a zero não gera alerta. O percentual mostrado é arredondado para baixo.
- **Chave de cada alerta:** `PAYABLE_DUE_SOON`, `RECEIVABLE_DUE_SOON`, `PAYABLE_OVERDUE`, `RECEIVABLE_OVERDUE`, `BUDGET_WARNING:<categoria>:<aaaa-mm>` e `BUDGET_EXCEEDED:<categoria>:<aaaa-mm>`. A faixa e o mês dentro da chave fazem o alerta de orçamento voltar ao mudar de faixa ou na virada do mês, sem regra extra.
- **Impressão digital (`fingerprint`):** títulos usam `quantidade|total`; orçamento usa texto vazio (a mudança de faixa já muda a chave).
- **Dispensar:** grava `(usuário, chave, fingerprint)`. Um alerta fica oculto se existir dispensa com a mesma chave **e** a mesma fingerprint. Dispensar uma chave sem alerta ativo responde `404`.
- **Esquecer dispensas obsoletas:** ao listar, apaga as dispensas do usuário cujo alerta não existe mais, para o alerta voltar se o mesmo problema reaparecer depois com os mesmos números.
- **Ordem da lista:** vencidas (pagar, receber), a vencer (pagar, receber), orçamento estourado, orçamento em 80% (por nome da categoria).

## Banco de dados

Migration nova `V10__create_dismissed_alerts_table.sql`:

```sql
CREATE TABLE dismissed_alerts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    alert_key VARCHAR(100) NOT NULL,
    fingerprint VARCHAR(100) NOT NULL,
    dismissed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE(user_id, alert_key),

    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

**Impacto em dados existentes:** nenhum. É uma tabela nova e vazia; não altera nem apaga dados de outras tabelas.

## API

| Método | Rota | Corpo | Resposta |
|---|---|---|---|
| `GET` | `/api/v1/alerts` | — | `200 {count, alerts[]}` com os alertas **ativos** (dispensados ficam de fora) |
| `POST` | `/api/v1/alerts/dismiss` | `{key}` | `204`; `404` se não há alerta ativo com essa chave |

Cada item de `alerts`: `key`, `kind` (`BILLS_DUE_SOON`, `BILLS_OVERDUE`, `BUDGET_WARNING`, `BUDGET_EXCEEDED`), `billType` (`PAYABLE`/`RECEIVABLE`, só nos de títulos), `count`, `total`, `dueUntil` (só em "a vencer"), `categoryId`, `categoryName`, `spent`, `budgeted`, `percentUsed` (só nos de orçamento), `month` e `year` (só nos de orçamento). Valores monetários como `BigDecimal` escala 2.

Erro novo: `AlertNotFoundException` em `exception/`, tratada pelo `GlobalExceptionHandler` como `404`.

Mudanças incompatíveis com o cliente atual: nenhuma.

## Cliente web

- `js/api.js`: `api.alerts.list()` e `api.alerts.dismiss(key)`; depois de qualquer requisição que não seja `GET` bem-sucedida, dispara o evento `api:changed` (é o que faz o alerta sumir sozinho depois de baixar um título).
- `js/alerts.js` (novo, componente e não tela): botão de sino com contador (sem número quando zero; "99+" acima de 99), painel de alertas, estado vazio "Tudo em dia", botão "Dispensar" por alerta. Fecha com `Esc` ou clique fora. Recarrega os alertas a cada troca de tela, ao voltar para a aba (`visibilitychange`) e em `api:changed`.
- `js/app.js`: o sino entra em `sidebar-top`, ao lado do logo (na barra do celular, antes do botão do menu).
- Clique num alerta de títulos: `presetBillFilters` (já exportado por `views/bills.js`) com `type` e `status`/janela de datas do alerta, depois vai para `#/bills`. Como o router não reage a hash igual, na própria tela de títulos dispara `hashchange` à mão. "A vencer" usa `status: OPEN`, `dueDateFrom: hoje`, `dueDateTo: hoje + 7`; "vencidas" usa `status: OVERDUE`.
- Clique num alerta de orçamento: nova função exportada em `views/budgets.js` posiciona o mês/ano do alerta e vai para `#/budgets`.
- `css`: estilos do sino, do contador e do painel (`position: fixed`, para não ser cortado pela sidebar), mobile-first e no tema existente.

## Testes

`AlertIntegrationTest` (`AbstractIntegrationTest`, MockMvc, `Clock` mockado):

- CA1: 3 títulos a pagar nos próximos 7 dias → um alerta com `count = 3`, o total e `dueUntil` igual ao maior vencimento; `count` da resposta igual a 1.
- CA2: baixar um título vencido → o alerta de vencidos some (ou é recalculado sem ele).
- CA3: título que vence hoje aparece em "a vencer" e não em "vencidas"; virada do dia (relógio avançado) o move para "vencidas".
- CA4: 85% do orçamento gera o de 80%; passar de 100% troca pelo de estourado; limites exatos (80,00% entra, 100,00% ainda é 80%, 100,01% estoura).
- CA5: dispensar esconde; sem mudança continua escondido; novo título na janela faz voltar; orçamento dispensado só volta ao mudar de faixa, não com mais gasto na mesma faixa; alerta que some e reaparece com os mesmos números volta.
- CA6: coberto pelo cliente (filtros do clique); teste JS do mapeamento alerta → filtros.
- CA7: usuário sem nada → `count = 0` e lista vazia.
- CA8: dois usuários, cada um vê só os próprios alertas; dispensar a chave de outro usuário responde `404`.
- Bordas: título cancelado e baixado ignorados; categoria sem orçamento no mês gera nada; muitos títulos geram um alerta por tipo; orçado zero não gera alerta.
- JS (`api.test.mjs`): `api.alerts.*` chamam as rotas certas; requisição de escrita dispara `api:changed` e `GET` não.
- Manual (Chrome real, como na 006): sino com e sem número em desktop e celular, painel, dispensar, clique levando à tela filtrada, alerta sumindo depois de baixar um título.

## Riscos e alternativas descartadas

- Cálculo a cada requisição e a cada troca de tela: são poucas queries agregadas por usuário (títulos em aberto e orçamentos do mês); sem cache. Se pesar com dados reais, entra cache curto depois.
- Dispensa só no navegador (`localStorage`): descartada, o usuário espera que valha para a conta.
- Tabela de alertas persistidos com "lida/não lida": descartada, está em "Fora de escopo".
- Nova barra no topo de todas as telas: descartada, mexeria no cabeçalho de cada tela.
- Reaparecer o alerta de orçamento a cada gasto novo: descartado a pedido (só ao mudar de faixa).

## Deploy

Uma migration nova e vazia (`V10`); sem backup necessário, mas Flyway roda no boot. Merge em `main` só com ok explícito do Gustavo, junto com as outras specs pendentes. Rollback: reverter o merge; a tabela `dismissed_alerts` pode ficar sem uso.
