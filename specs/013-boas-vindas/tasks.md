# 013 — Tarefas

**Plano:** [plan.md](plan.md)

Cada tarefa aponta para os critérios que atende. Teste primeiro, depois o código.

- [ ] **T1** — Migration `V17__create_onboarding_table.sql` (CA3, CA6)
- [ ] **T2** — Testes de integração `OnboardingIntegrationTest` escritos e falhando (CA1–CA8)
- [ ] **T3** — Pacote `onboarding`: entidade, repositório, `WelcomeService` com o e-mail (CA1, CA3, CA8)
- [ ] **T4** — Chamar as boas-vindas na confirmação da verificação e no login Google que cria ou verifica a conta (CA1, CA2, CA3, CA6)
- [ ] **T5** — `OnboardingService`, `OnboardingController` (`GET /onboarding`, `POST /onboarding/dismiss`) e records com OpenAPI (CA4, CA5, CA6, CA7)
- [ ] **T6** — `AccountDeletionService` apaga a linha, com teste (spec 012)
- [ ] **T7** — Ajustar testes existentes que contam e-mails, só onde o novo e-mail muda a contagem
- [ ] **T8** — Cliente: `api.onboarding`, cartão "Primeiros passos" no painel, CSS e teste JS (CA4, CA5, CA7)
- [ ] **T9** — Ajuda do painel (`static/ajuda/`) e README (API e decisão de domínio)
- [ ] **T10** — Rodar `./mvnw verify` e `node --test "src/test/js/*.test.mjs"`
- [ ] **T11** — Validação com as skills do projeto (`web-design-guidelines`, `accessibility-compliance`, `responsive-design`, `api-design-principles`) e anotar a revisão aqui
- [ ] **T12** — Checagem manual no navegador a 390px e 1280px (Gustavo) e backup antes do deploy (Gustavo)

## Notas de implementação

## Revisão
