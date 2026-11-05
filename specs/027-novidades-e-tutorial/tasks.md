# 027 — Tarefas

**Plano:** [plan.md](plan.md)

Cada tarefa aponta para os critérios que atende. Teste primeiro, depois o código.

- [ ] **T1** — Testes de integração `OnboardingIntegrationTest` ajustados e falhando (CA1, CA4, CA5, CA6, CA7)
- [ ] **T2** — Migration `V18__add_show_on_start_to_onboarding.sql`, entidade e `OnboardingService` (`eligible`, `visible`, passos sempre calculados) (CA1, CA5, CA6, CA7)
- [ ] **T3** — `PUT /onboarding/preferences`, remoção de `POST /onboarding/dismiss`, OpenAPI (CA4)
- [ ] **T4** — Testes JS do pop-up (`onboarding-dialog.test.mjs`) e do fluxo em `app.js`, falhando (CA1–CA4, CA7, CA9)
- [ ] **T5** — Cliente: `onboarding.js` (dialog), `api.js`, `app.js`, CSS (CA1–CA4, CA7, CA9)
- [ ] **T6** — Remover o cartão do painel (CA8)
- [ ] **T7** — Ajuda (`static/ajuda/`) e README
- [ ] **T8** — Rodar `./mvnw verify` e `node --test "src/test/js/*.test.mjs"`
- [ ] **T9** — Validação com as skills do projeto e anotar a revisão aqui
- [ ] **T10** — Checagem no navegador a 390px e 1280px, e checagem manual (Gustavo)

## Notas de implementação

## Revisão
