# 015 — Tarefas

**Plano:** [plan.md](plan.md)

Cada tarefa vira um commit (ou poucos) e aponta para os critérios que atende. Testes primeiro.

- [x] **T1** — Migration V19 + `Account.countsAsAvailable` + padrão por tipo em `AccountType` (CA1)
- [x] **T2** — `AccountRequest`/`AccountResponse` e regra 5 em `AccountService`, com `@Schema` (CA4, CA7)
- [x] **T3** — `ReportRepository.getAvailableBalance` e `ReportService` nas duas previsões (CA2, CA3)
- [x] **T4** — Testes de integração: migration, regra 5, isolamento, previsões, regressão de relatório e saldo da conta (CA1–CA4)
- [x] **T5** — `OpenApiContractIntegrationTest` com o campo novo e a descrição de `currentBalance` (CA7)
- [x] **T6** — Dashboard: "Saldo disponível", "Guardado" e identificação das contas fora do disponível, com teste JS (CA5)
- [x] **T7** — Perfil em abas acessíveis, com teste JS (CA8)
- [x] **T8** — Cartão "Saldo disponível" no Perfil, com desfazer em erro e teste JS (CA6)
- [x] **T9** — Aviso em Novidades com o passo a passo, conforme a decisão do plano (CA9)
- [x] **T10** — Verificação manual local (desktop e mobile)
- [x] **T11** — Skills: `web-design-guidelines`, `accessibility-compliance`, `responsive-design`, `api-design-principles`; anotar a revisão aqui
- [x] **T12** — README (API, decisão de domínio, mudança de `currentBalance`), `./mvnw verify` e `node --test`, atualizar o quadro

## Notas de implementação

Descobertas durante a implementação que afetaram a spec ou o plano.

- **Abas do Perfil:** a aba aberta fica em memória, não em `location.hash`, porque o hash é o roteador do app. Os painéis ficam todos no DOM (inativos `hidden`); as contas só são buscadas quando a aba "Preferências" abre.
- **Aviso em Novidades (T9):** como o mecanismo de Novidades ainda não existe (027), entrou um cartão dispensável na Visão geral, com o passo a passo. Aparece só para quem já tem conta. A dispensa fica no aparelho (`localStorage`); a 027 generaliza e passa a guardá-la no servidor.
- **Teste da migration:** o banco de teste já nasce migrado, então o teste reaplica o `UPDATE` do arquivo V19 sobre linhas que valem o DEFAULT.
- **V19 × V18:** a 027 precisa entrar na `develop` antes desta branch.
- **Cartão de crédito:** `creditCardCountsByDefault` prova a regra 8.

## Revisão com as skills (2026-11-06)

- `web-design-guidelines`: corrigidos `name`/`autocomplete="off"` do checkbox, estado de hover das abas e "Carregando…" no cartão. Não aplicado: aba do Perfil na URL (o hash é o roteador do app).
- `accessibility-compliance`: abas no padrão WAI-ARIA (tablist/tab/tabpanel, setas, Home/End, foco visível); caixinha com rótulo que envolve o controle (sem zona morta) e erro em `role="alert"`.
- `responsive-design`: conferido a 375px no navegador (abas quebram linha, sem rolagem horizontal; alvos de 44px).
- `api-design-principles`: sem rota nova; campo opcional documentado em OpenAPI, `PUT` sem mudança de status, `OpenApiContractIntegrationTest` verde.
