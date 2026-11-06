# 015 — Tarefas

**Plano:** [plan.md](plan.md)

Cada tarefa vira um commit (ou poucos) e aponta para os critérios que atende. Testes primeiro.

- [ ] **T1** — Migration V19 + `Account.countsAsAvailable` + padrão por tipo em `AccountType` (CA1)
- [ ] **T2** — `AccountRequest`/`AccountResponse` e regra 5 em `AccountService`, com `@Schema` (CA4, CA7)
- [ ] **T3** — `ReportRepository.getAvailableBalance` e `ReportService` nas duas previsões (CA2, CA3)
- [ ] **T4** — Testes de integração: migration, regra 5, isolamento, previsões, regressão de relatório e saldo da conta (CA1–CA4)
- [ ] **T5** — `OpenApiContractIntegrationTest` com o campo novo e a descrição de `currentBalance` (CA7)
- [ ] **T6** — Dashboard: "Saldo disponível", "Guardado" e identificação das contas fora do disponível, com teste JS (CA5)
- [ ] **T7** — Perfil em abas acessíveis, com teste JS (CA8)
- [ ] **T8** — Cartão "Saldo disponível" no Perfil, com desfazer em erro e teste JS (CA6)
- [ ] **T9** — Aviso em Novidades com o passo a passo, conforme a decisão do plano (CA9)
- [ ] **T10** — Verificação manual local (desktop e mobile)
- [ ] **T11** — Skills: `web-design-guidelines`, `accessibility-compliance`, `responsive-design`, `api-design-principles`; anotar a revisão aqui
- [ ] **T12** — README (API, decisão de domínio, mudança de `currentBalance`), `./mvnw verify` e `node --test`, atualizar o quadro

## Notas de implementação

Descobertas durante a implementação que afetaram a spec ou o plano.
