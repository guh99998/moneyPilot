# 013 — Plano técnico

**Spec:** [spec.md](spec.md)
**Status:** aprovado e implementado (em revisão)

## Abordagem

Um pacote novo `onboarding` com uma tabela própria, uma linha por conta que **passou pelas boas-vindas**. A linha é, ao mesmo tempo, o registro de "o e-mail já foi enviado" (CA3, CA8) e o estado do cartão (CA4, CA5, CA7). Conta antiga não tem linha, então fica de fora sem nenhum backfill (CA6).

- `WelcomeService.welcome(user)`: se a conta ainda não tem linha, cria a linha e envia o e-mail. Falha no envio é registrada no log e engolida; a linha já existe, então o e-mail não é reenviado (CA8).
- Chamado em dois pontos, depois de a conta estar verificada:
  - `EmailVerificationService.confirm`, só na primeira confirmação (o ramo de "link já usado" retorna antes, então clicar de novo não envia nada);
  - `GoogleSignInService.signIn`, quando a conta é **criada** pelo Google ou era não verificada e passou a verificada. Conta antiga verificada que só vincula o Google não passa por aqui.
- `OnboardingService.get()` devolve o cartão da conta autenticada. Os passos são calculados ao ler: se a conta tem ao menos uma conta bancária, um lançamento e um orçamento, o passo é marcado e **o marco é gravado** (`*_done_at`). Passo gravado nunca desmarca, mesmo que a pessoa apague o que o cumpriu (CA7).
- Limitação aceita: o marco é gravado quando o painel é lido. Quem cria e apaga algo sem abrir o painel no meio não marca o passo.
- `visible` = existe linha, não foi dispensado e os três passos não estão todos feitos (CA4, CA5, CA6).
- `AccountDeletionService` passa a apagar a linha (spec 012).

## Banco de dados

- Migration nova `V17__create_onboarding_table.sql`:

  ```sql
  CREATE TABLE onboarding (
      user_id BIGINT PRIMARY KEY REFERENCES users(id),
      welcomed_at TIMESTAMPTZ NOT NULL,
      account_done_at TIMESTAMPTZ NULL,
      transaction_done_at TIMESTAMPTZ NULL,
      budget_done_at TIMESTAMPTZ NULL,
      dismissed_at TIMESTAMPTZ NULL
  );
  ```

- **Impacto em dados existentes:** nenhum. Tabela nova e vazia; nenhuma linha de `users` é alterada ou apagada. Contas existentes não ganham linha, que é o que a spec pede. (Não é migration que altere ou apague dados.)

## API

| Método | Rota | Mudança |
|---|---|---|
| GET | `/api/v1/onboarding` | Novo. Autenticada. `200` com `{ visible, steps: [{ key, done }] }`; `visible: false` para conta antiga, dispensado ou concluído. |
| POST | `/api/v1/onboarding/dismiss` | Novo. Autenticada. `204`, idempotente. Sem linha (conta antiga), também `204` sem efeito. |

Chaves dos passos: `ACCOUNT`, `TRANSACTION`, `BUDGET`. Sem campo de dinheiro, então fora do `MoneyScaleIntegrationTest`. Sem `id` na rota, então não há recurso de outra conta a esconder: o escopo é sempre o usuário autenticado (`CrossUserIsolationIntegrationTest` ganha o caso). Mudanças incompatíveis com o cliente atual: nenhuma (rotas novas).

OpenAPI: `@Tag`, `@Operation`, `@ApiResponse` de sucesso e de `401`, `@Schema` com descrição e exemplo nos records, enum do passo como componente (`enumAsRef = true`).

## Cliente web

- `js/api.js`: `api.onboarding.get()` e `api.onboarding.dismiss()`.
- `js/views/dashboard.js`: busca o cartão junto do resto e, se `visible`, mostra o cartão "Primeiros passos" no topo do painel, com os três passos (cada um com texto e estado, nunca só cor), um link para a tela do passo e o botão "Dispensar". Falha ao buscar o cartão não quebra o painel (o cartão simplesmente não aparece).
- CSS no tema existente, mobile-first. Acessível: lista ordenada, estado lido como texto ("feito" / "a fazer"), alvos de 44px.
- `static/ajuda/` do painel: acrescentar uma linha sobre o cartão de primeiros passos (convenção da 021).

## E-mail

Texto da spec, em `WelcomeService`, texto puro como os demais e-mails, com o link `{publicUrl}/app/`. Sem rastreamento.

## Testes

- `OnboardingIntegrationTest` (Testcontainers, `RecordingEmailSender`, `FakeGoogleTokenVerifier`):
  - CA1: cadastro + confirmação envia o e-mail de boas-vindas;
  - CA2: Google cria conta e envia;
  - CA3: confirmar de novo e entrar de novo não reenviam;
  - CA4: `GET` devolve `visible` com os passos, que marcam ao criar conta, lançamento e orçamento;
  - CA5: dispensar esconde; concluir os três esconde; vale em outro login;
  - CA6: conta sem linha recebe `visible: false` e nenhum e-mail;
  - CA7: passo marcado continua marcado depois de apagar o que o cumpriu;
  - CA8: provedor falhando não impede a verificação nem o login, e não reenvia.
- `AccountDeletionIntegrationTest`: a linha some junto com a conta.
- `OpenApiContractIntegrationTest` passa com as rotas novas.
- Testes existentes que contam e-mails depois da verificação são ajustados **só** onde o novo e-mail de boas-vindas muda a contagem, explicando no commit.
- JS: teste da view do cartão (`onboarding-card.test.mjs`) com o `fake-dom`: mostra, marca passos, dispensa, some em falha da API.

## Riscos e alternativas descartadas

- **Enviar e-mail dentro da transação da verificação:** a falha do provedor não pode derrubar a verificação → o envio é protegido por `try/catch`, e a linha é gravada antes.
- **Alternativa descartada — colunas em `users`:** exigiria `DEFAULT` e backfill para distinguir conta antiga de conta nova. A tabela própria resolve isso pela ausência da linha.
- **Alternativa descartada — marcar o passo dentro de cada service (conta, lançamento, orçamento):** acopla três domínios a um recurso novo. Marcar ao ler é mais simples, com a limitação aceita acima.
- **Alternativa descartada — `GET` devolvendo `404` para conta antiga:** obriga o cliente a tratar um erro no caminho normal.

## Deploy

Migration só cria uma tabela; o `pg_dump` não é obrigatório (faça mesmo assim, como de costume). Ordem: subir a imagem (o Flyway cria a tabela). **Rollback:** voltar a imagem anterior; a tabela nova fica inerte e pode ser mantida. Quem se verificar depois do deploy recebe o e-mail; ninguém existente recebe nada.
