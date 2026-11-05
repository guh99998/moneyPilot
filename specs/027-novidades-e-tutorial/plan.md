**Spec:** [spec.md](spec.md)

# 027 — Plano

## Contexto

As regras da 027 foram fechadas em 2026-11-05 e o pedido de seguir para o plano aprova a spec. O pop-up substitui o cartão de primeiros passos da 013, que é fácil de ignorar, e usa o mesmo progresso. 

Aprovar este plano aprova a implementação na `feature/027-novidades-e-tutorial`. Merge em `develop` e na `main` e o push continuam dependendo de um ok dele.

## Decisões novas (2026-11-05)

- **"Entrar" é fazer login**, com senha ou Google. F5, aba nova e sessão renovada sozinha não reabrem. Quem fica logado só vê o pop-up de novo quando precisar entrar outra vez, inclusive depois de a sessão expirar.
- **O pop-up abre no primeiro passo pendente.** Sem pendência (reabertura pela ajuda), abre no passo 1.
- **`POST /onboarding/dismiss` sai.** A 013 ainda não está em produção. A coluna `dismissed_at` fica, e quem já dispensou continua sem ver o pop-up.
- **Link da ajuda sem sessão:** depois do login a pessoa vai para a Visão geral, sem reabrir sozinho.

## Ajustes na spec 027

- `Status` e critérios: CA1 passa a dizer "primeiro passo pendente"; CA1, CA3, CA4, CA5 e CA6 trocam "entra"/"próxima entrada" por "login"/"próximo login".
- **Conta antiga pode reabrir pela ajuda (CA7):** os passos são calculados na hora, sem gravar nada, e a caixinha não aparece, porque não teria efeito. O mesmo vale para quem dispensou o cartão da 013 ou concluiu os passos.
- **Caso de borda trocado:** a spec diz que o pop-up mostra os passos feitos se a pessoa concluir o terceiro com ele aberto, mas o modal bloqueia a tela e os passos são feitos em outras telas. Fica: "concluiu em outra aba com o pop-up aberto: ele mostra o estado de quando abriu e não volta no próximo login".
- Novo caso de borda: link da ajuda sem sessão, como decidido acima.
- A regra "recurso de outra pessoa responde `404`" sai: as rotas não têm id, sempre valem para a conta autenticada, e o isolamento segue testado.

## Schema

`V18__add_show_on_start_to_onboarding.sql`: `ALTER TABLE onboarding ADD COLUMN show_on_start BOOLEAN NOT NULL DEFAULT TRUE;`

Não altera dado existente além do default. É compatível com a versão anterior do app, porque o `INSERT` nativo de `OnboardingRepository` não cita a coluna e o default a preenche. Pela regra do projeto, o backup antes do deploy continua.

## API

| Rota | Mudança |
| --- | --- |
| `GET /onboarding` | Passa a devolver `{visible, eligible, showOnStart, steps}`, sempre com os três passos |
| `PUT /onboarding/preferences` | Nova: corpo `{showOnStart}`, `@NotNull`, resposta `204`, `400` com `ErrorResponse` e `401`. Idempotente; conta sem linha não muda nada |
| `POST /onboarding/dismiss` | Removida |

- `eligible`: tem linha, não dispensou e ainda há passo pendente. Decide se a caixinha aparece.
- `visible = eligible && showOnStart`. Decide se o pop-up abre sozinho no login.
- Em `OnboardingService.get()`, os passos passam a ser conferidos em todos os casos. Só grava quando existe linha, e passo feito continua feito.
- Atualizar as anotações OpenAPI (`OnboardingController`, `OnboardingResponse`) e o novo record `OnboardingPreferencesRequest`. O `OpenApiContractIntegrationTest` cobra isso.

## Cliente web

- **`js/views/onboarding.js` (reescrito):** `openOnboarding(state)` monta um `<dialog>` nativo, como `formDialog` e `confirmDialog` em `js/ui.js`. O `showModal` já prende o foco, trata o Esc e devolve o foco ao fechar.
  - Título com "Passo N de 3", estado escrito ("Feito"/"A fazer") e link para a tela do passo (`#/accounts`, `#/transactions`, `#/budgets`, os de `STEP_TEXT`). O link fecha o pop-up.
  - **Voltar** não existe no passo 1 e **Avançar** não existe no passo 3. **Dispensar** existe em todos e só fecha.
  - Ao trocar de passo, o foco vai para o título, porque o botão usado pode sumir.
  - A caixinha salva ao mudar. Se falhar, volta ao valor anterior e mostra o erro em `.dialog-error`.
- **`js/app.js`:**
  - O callback de `renderAuth` é o único ponto de login, para senha e Google. Ele liga uma flag em memória.
  - Depois de desenhar a rota, `render()` consome a flag de forma síncrona, porque login e `hashchange` disparam duas renderizações, e chama `api.onboarding.get()`. Abre se `visible`.
  - Falha ao buscar não mostra pop-up nem erro: ele é um extra, como o cartão era.
  - Reabertura: `#/dashboard?primeiros-passos` abre em qualquer estado e tira o parâmetro com `replaceState`, para o F5 não repetir.
- **`js/views/dashboard.js`:** remover `loadOnboarding`, `welcomeCard` e o import.
- **`js/api.js`:** trocar `dismiss` por `savePreferences`.
- **`css/app.css`:** ajustar as regras `.onboarding*` para o dialog, mobile-first, com alvos de 44px e botões que quebram linha (`dialog-actions-wrap`).
- **Ajuda:** link em `ajuda/primeiros-passos.html` e novo texto do trecho do cartão em `ajuda/visao-geral.html`.

## Testes (um por critério)

- **`OnboardingIntegrationTest`:** cobre os estados de `visible`/`eligible`, o `PUT` (dois aparelhos, corpo inválido, `401`, conta antiga) e passos calculados para conta antiga sem criar linha (CA4, CA5, CA6, CA7).
  - Os testes atuais mudam: `dismiss` sai, a dispensa antiga passa a ser preparada direto no banco, e `steps` deixa de vir vazio. Isso fica explicado no commit, porque é mudança de regra.
- **`src/test/js/onboarding-card.test.mjs` vira `onboarding-dialog.test.mjs`:**
  - navegação e botões por passo (CA2), Dispensar sem chamar a API (CA3), caixinha com sucesso e falha (CA4), abertura no primeiro pendente (CA1), dialog nativo e foco (CA9);
  - o `fake-dom.mjs` pode precisar de `showModal`/`close`.
- **Fluxo em `app.js`:** abre depois do login, não abre em F5 nem em troca de rota, e abre pelo link da ajuda (CA1, CA3, CA7). Sem o card no painel (CA8).

## Documentação e quadro

- README: decisão de domínio (linha 116), tabela da API (linhas 156-157) e menção à 027.
- `specs/027-novidades-e-tutorial/`: criar `plan.md` a partir deste plano e `tasks.md`; atualizar `spec.md`.
- Mudar o título do item 027 no quadro para "027 — Pop-up de primeiro contato", com `Status` "Plano" ao começar, "Implementando" durante e "Revisão" no fim.

## Passos

1. Atualizar `spec.md`, criar `plan.md` e `tasks.md`, atualizar o quadro e commitar.
3. Backend com testes primeiro: migration, service, controller.
4. Cliente com testes primeiro: pop-up, `app.js`, remoção do cartão, CSS.
5. Ajuda e README.
6. Rodar as validações abaixo, marcar `tasks.md` e passar o quadro para "Revisão".

## Verificação

- `./mvnw verify` (inclui `OpenApiContractIntegrationTest`, `MoneyScaleIntegrationTest` não muda) e `node --test "src/test/js/*.test.mjs"`, com falhas reportadas.
- `web-design-guidelines`, `accessibility-compliance`, `responsive-design` e `api-design-principles`, com correções e revisão anotadas em `tasks.md`.
- No navegador, com app local e Playwright, a 390px e 1280px, em conta nova: login abre o pop-up no passo pendente; Dispensar e novo login reabrem; desmarcar e novo login não reabrem; F5 não reabre; o link da ajuda reabre; concluir os passos encerra; o teclado funciona.
- Depois, Gustavo faz a checagem manual dele.

## Depois deste plano

- **Release:** se a 013 e a 027 saírem juntas, como parece provável com o merge adiado, o cartão nunca chega à produção. O `release 013 checklist.md` precisa ser refeito: passa a citar V17 e V18, e as conferências viram as do pop-up. Faço isso quando o Gustavo decidir o merge.
- O teste de rollback precisa ser repetido com a V18 antes do release.

## Limites

Sem merge em `develop` ou `main` e sem push sem ok do Gustavo. Claude não faz deploy nem acessa produção. Nada de novidades, tutoriais por tela nem dependências novas.

## Adendo: guia depois do pop-up (2026-11-05)

Pedido do Gustavo ao testar: o link só jogava a pessoa na tela. Decisões: **faixa de instrução** (sem destacar elementos), **avanço automático** para o próximo passo, **três passos**, e **Dispensar tutorial só desta vez**. Sem schema nem API novos.

- `onboarding.js`: `startGuide`/`endGuide` (`sessionStorage`), `guideBand(rota)` (faixa do primeiro passo pendente, com link se a pessoa estiver em outra tela e o botão "Dispensar tutorial") e `checkGuide()` (confere o progresso e navega quando um passo novo foi concluído). O link do passo no pop-up inicia o guia; `markLogin` o encerra.
- `app.js`: depois de desenhar a rota, insere a faixa no topo do `main`; escuta `api:changed` (já disparado por toda escrita bem-sucedida) para chamar `checkGuide`.
- Só avança se o número de passos feitos aumentou: editar ou excluir não navega.
- `css/app.css`: faixa mobile-first, alvos de 44px. Testes: `onboarding-dialog.test.mjs` (CA10 a CA12).
