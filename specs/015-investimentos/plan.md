# 015 — Plano técnico

**Spec:** [spec.md](spec.md)
**Status:** proposto

## Abordagem

`ReportRepository.getTotalBalance` soma todas as contas. Cada conta ganha a coluna `counts_as_available`, e o saldo usado pelo dashboard e pelas duas previsões passa a somar só as contas marcadas. O "Guardado" é a soma das desmarcadas, calculada no cliente a partir dos saldos por conta. A opção é editada em `PUT /accounts/{id}`, num cartão do Perfil, que passa a ter abas.

## Banco de dados

`V19__add_counts_as_available_to_accounts.sql`:

```sql
ALTER TABLE accounts ADD COLUMN counts_as_available BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE accounts SET counts_as_available = FALSE WHERE type = 'INVESTMENT';
```

- **Impacto em dados existentes (aprovado):** nenhum lançamento muda. Contas `INVESTMENT` ficam desmarcadas, então o saldo e a previsão de quem as tem caem no deploy.
- **Ordem das migrations:** a `develop` termina na V17. A V18 está na branch 027 (ainda não mergeada). Usamos V19 como no plano aprovado, então **a 027 precisa entrar na `develop` antes desta branch**. Se a V19 chegar à produção antes da V18, o Flyway recusa a V18 depois.

## API

| Método | Rota | Mudança |
|---|---|---|
| POST | `/accounts` | `countsAsAvailable` opcional; omitido usa o padrão do tipo |
| PUT | `/accounts/{id}` | `countsAsAvailable` opcional; omitido mantém, ou usa o padrão do novo tipo se o tipo mudou |
| GET | `/accounts`, `/accounts/{id}` | resposta expõe `countsAsAvailable` |
| GET | `/reports/cash-flow-forecast`, `/reports/monthly-forecast` | `currentBalance` passa a ser o saldo disponível |

- Backend: `Account`, `AccountRequest` (`Boolean`, com `@Schema`), `AccountResponse`, `AccountType` (padrão por tipo), `AccountService` (regra 5), `ReportRepository.getAvailableBalance` (filtra as contas marcadas nas duas subqueries) e `ReportService` (linhas 84 e 159).
- Mudança de significado de `currentBalance` para clientes da API (spec 011): registrada na descrição OpenAPI e no README. Sem rota nova, então o `MoneyScaleIntegrationTest` não muda.

## Cliente web

- `views/profile.js`: tabs acessíveis (`role="tablist"`, setas, Home/End, foco, `aria-selected`, `aria-controls`). Proposta de divisão, para você aprovar:
  - **Dados**: cartão Dados.
  - **Acesso e segurança**: E-mail, Senha, Google e Excluir conta.
  - **Saldo disponível**: novo cartão, com uma caixinha por conta. Cada mudança chama `api.accounts.update`; em erro, a caixinha volta ao estado real e o erro fica visível.
  - A aba ativa fica em `location.hash`, para o foco e o `reload` dos cartões continuarem funcionando.
- `views/dashboard.js`: `hero()` passa a "Saldo disponível", com "Guardado: R$ X" quando houver conta desmarcada (centavos inteiros); `loadBalances()` inclui `countsAsAvailable`; `accountBalances()` identifica as contas fora do disponível.
- `views/accounts.js`: sem mudança.
- Aviso em Novidades: **ainda não existe mecanismo de Novidades** (a 027 hoje cobre só o pop-up de primeiros passos). Ver "Riscos".

## Testes

| Critério | Prova |
|---|---|
| CA1 | `AccountIntegrationTest`: migration com Flyway real, conta `INVESTMENT` desmarcada e as outras marcadas |
| CA2 | `CashFlowForecastIntegrationTest` e `MonthlyForecastIntegrationTest`: transferência corrente → investimento reduz `currentBalance` |
| CA3 | `ReportIntegrationTest` e `AccountBalanceIntegrationTest`: sem regressão |
| CA4 | `AccountIntegrationTest` (3 variações da regra 5) e `CrossUserIsolationIntegrationTest` |
| CA5 | teste JS do dashboard (`hero`, Guardado em centavos) |
| CA6 | teste JS do cartão no Perfil: erro desfaz a caixinha |
| CA7 | `OpenApiContractIntegrationTest` |
| CA8 | teste JS das abas: roles, setas, hash |
| CA9 | teste do aviso (depende da decisão abaixo) |

Mais verificação manual local (criar corrente e investimento, transferir, alternar a caixinha, conferir dashboard e previsão, mobile) e as skills `web-design-guidelines`, `accessibility-compliance`, `responsive-design` e `api-design-principles`.

## Riscos e alternativas descartadas

- **Novidades não existe.** Para o aviso, a menor opção é um banner dispensável no dashboard, com texto e passo a passo, dispensa guardada no servidor como os alertas. Isso é parte do que a 027 vai generalizar; a alternativa é esperar a 027. **Preciso da sua decisão.**
- **V18 × V19** (acima): a 027 entra primeiro.
- **Cartão de crédito marcado** até a 016, como decidido; quem desmarcar assume que a fatura não aparece.
- Alternativa descartada: calcular o disponível só pelo tipo, sem coluna. Tira a escolha da pessoa.

## Deploy

Backup do banco antes, a cargo do Gustavo. A V19 é rápida e reversível por `ALTER TABLE accounts DROP COLUMN counts_as_available`. Depois do deploy, o saldo de quem tem investimento cai: o aviso em Novidades deve sair junto.
