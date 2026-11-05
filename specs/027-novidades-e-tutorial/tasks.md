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
- [x] **T11** — Guia depois do pop-up: testes JS primeiro, depois `onboarding.js`, `app.js` e CSS (CA10, CA11, CA12)
- [x] **T12** — Rodar as suítes, revisar com as skills e checar no navegador (guia)
- [ ] **T10** — Checagem no navegador a 390px e 1280px, e checagem manual (Gustavo)

## Notas de implementação

- Backend: testes ajustados primeiro (7 falhavam), depois V18, service, controller e records. `./mvnw verify`: 507 testes, nenhuma falha. `node --test`: 224 testes, nenhuma falha.
- Decisão: a lógica do fluxo (flag do login, link da ajuda, busca silenciosa) ficou em `onboarding.js` (`markLogin`, `maybeOpenOnboarding`) e `app.js` só chama. O `app.js` roda `render()` ao ser importado e não é testável sem navegador; assim os testes de fluxo (CA1, CA3, CA7) cobrem a mesma lógica.
- Bug pego pelo teste: `replaceChildren` com `null` no DOM real escreveria "null"; os filhos nulos são filtrados.
- Navegador (Playwright, 390px e 1280px, conta nova): login abre o pop-up no passo 1 com o foco no título; F5 não reabre; novo login reabre; Avançar move o foco para o título; Esc fecha; desmarcar a caixinha e entrar de novo não abre; o link da ajuda reabre (caixinha desmarcada refletida) e o F5 depois não repete; o painel não mostra mais o cartão (CA8). Alvos de 44px e sem rolagem horizontal. Não testado no navegador: concluir os três passos pela interface e o login com Google (mesmo callback da senha).

- Guia (T11): testes JS escritos antes (14 novos, falhando), depois `onboarding.js` (`startGuide`, `guideBand`, `checkGuide`), `app.js` e CSS. `node --test`: 240 testes, nenhuma falha. A faixa só avança quando o número de passos feitos aumenta, usando o evento `api:changed` que o `api.js` já disparava; nenhuma mudança no backend nem no `api.js`. A dica da faixa nomeia o botão ("Nova conta" etc.) em vez de abri-lo, para não mexer nas telas.
- Navegador (390px, usuário de teste): link do pop-up leva a Contas com a faixa do passo 1; criar a conta leva sozinho a Lançamentos com a faixa do passo 2 e o aviso; Dispensar tutorial remove a faixa e ela não volta em Orçamentos. Não testei no navegador o fim do terceiro passo (coberto por teste JS).

## Revisão

- `web-design-guidelines` (regras lidas e aplicadas ao `onboarding.js` e ao CSS): caixinha e rótulo sem zona morta de clique (gap trocado por padding no rótulo) e `overscroll-behavior: contain` nos dialogs. Foco com `:focus-visible` no título; nenhum `outline: none` sem substituto.
- `accessibility-compliance`: dialog nativo com `aria-labelledby`, foco no título ao abrir e a cada passo, Esc como Dispensar, estado escrito, erro com `role="alert"`. Corrigido: a caixinha ficava `disabled` durante o salvamento, o que tira o foco de quem usa o teclado; agora não desabilita e as mudanças seguidas entram numa fila (testes `never disabled while saving` e `two quick changes`).
- `responsive-design`: mobile-first, largura `min(480px, 100vw - 24px)`, botões que quebram linha, alvos de 44px e nenhuma rolagem horizontal a 390px (conferido no navegador). Nada a corrigir.
- `api-design-principles`: recurso no substantivo sob `/api/v1`; `GET` `200` e `PUT /preferences` idempotente com `204`, `400` e `401` documentados com `ErrorResponse`. Observação aceita: o `GET` grava os marcos dos passos (idempotente), como na 013.
