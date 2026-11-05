# 027 — Tarefas

**Plano:** [plan.md](plan.md)

Cada tarefa aponta para os critérios que atende. Teste primeiro, depois o código.

- [x] **T1** — Testes de integração `OnboardingIntegrationTest` ajustados e falhando (CA1, CA4, CA5, CA6, CA7)
- [x] **T2** — Migration `V18__add_show_on_start_to_onboarding.sql`, entidade e `OnboardingService` (`eligible`, `visible`, passos sempre calculados) (CA1, CA5, CA6, CA7)
- [x] **T3** — `PUT /onboarding/preferences`, remoção de `POST /onboarding/dismiss`, OpenAPI (CA4)
- [x] **T4** — Testes JS do pop-up (`onboarding-dialog.test.mjs`) e do fluxo em `app.js`, falhando (CA1–CA4, CA7, CA9)
- [x] **T5** — Cliente: `onboarding.js` (dialog), `api.js`, `app.js`, CSS (CA1–CA4, CA7, CA9)
- [x] **T6** — Remover o cartão do painel (CA8)
- [x] **T7** — Ajuda (`static/ajuda/`) e README
- [x] **T8** — Rodar `./mvnw verify` e `node --test "src/test/js/*.test.mjs"`
- [x] **T9** — Validação com as skills do projeto e anotar a revisão aqui
- [ ] **T10** — Checagem no navegador a 390px e 1280px, e checagem manual (Gustavo)

## Notas de implementação

- Backend: testes ajustados primeiro (7 falhavam), depois V18, service, controller e records. `./mvnw verify`: 507 testes, nenhuma falha. `node --test`: 224 testes, nenhuma falha.
- Decisão: a lógica do fluxo (flag do login, link da ajuda, busca silenciosa) ficou em `onboarding.js` (`markLogin`, `maybeOpenOnboarding`) e `app.js` só chama. O `app.js` roda `render()` ao ser importado e não é testável sem navegador; assim os testes de fluxo (CA1, CA3, CA7) cobrem a mesma lógica.
- Bug pego pelo teste: `replaceChildren` com `null` no DOM real escreveria "null"; os filhos nulos são filtrados.
- Navegador (Playwright, 390px e 1280px, conta nova): login abre o pop-up no passo 1 com o foco no título; F5 não reabre; novo login reabre; Avançar move o foco para o título; Esc fecha; desmarcar a caixinha e entrar de novo não abre; o link da ajuda reabre (caixinha desmarcada refletida) e o F5 depois não repete; o painel não mostra mais o cartão (CA8). Alvos de 44px e sem rolagem horizontal. Não testado no navegador: concluir os três passos pela interface e o login com Google (mesmo callback da senha).

## Revisão

- `web-design-guidelines` (regras lidas e aplicadas ao `onboarding.js` e ao CSS): caixinha e rótulo sem zona morta de clique (gap trocado por padding no rótulo) e `overscroll-behavior: contain` nos dialogs. Foco com `:focus-visible` no título; nenhum `outline: none` sem substituto.
- `accessibility-compliance`, `responsive-design` e `api-design-principles`: **ainda não rodei as skills**; conferi à mão o que o plano pedia (dialog nativo com `aria-labelledby`, foco no título, estado escrito, mobile-first com 44px, `PUT` idempotente com `204`/`400`/`401` documentados). Falta rodar as três com a skill antes de fechar a revisão.
