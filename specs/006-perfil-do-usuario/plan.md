# 006 — Plano técnico

**Spec:** [spec.md](spec.md)
**Status:** proposto

## Abordagem

Tudo no pacote `auth` (onde vivem `User`, `UserRepository` e `RefreshTokenService`), seguindo o padrão de `UserController`/`UserService`. O usuário vem sempre do `AuthenticatedUserProvider`, sem id na rota: não há como acessar o perfil de outra pessoa.

- **Nome:** `UpdateProfileRequest(@NotBlank @Size(max = 255) String name)`, com compact constructor que faz `strip()` antes da validação. Só espaços vira vazio e é recusado pelo `@NotBlank`.
- **Senha:** `ChangePasswordRequest(@NotBlank currentPassword, @NotBlank @Size(min = 8) newPassword)`, mesma regra do `RegisterRequest`.
- **Ordem em `ProfileService.changePassword`:** `attemptLimiter.check("password-change:<userId>")` → confere a senha atual (errada: `record` + `InvalidCurrentPasswordException`) → nova igual à atual (`SamePasswordException`) → grava o hash BCrypt → `reset` do contador → revoga as outras sessões.
- **Outras sessões:** nova query `RefreshTokenRepository.revokeAllActiveByUserIdExcept(userId, keepTokenId, now)`, exposta por `RefreshTokenService.revokeAllExcept(userId, rawCurrentToken)`. Se o cookie faltar ou for inválido, revoga todas. O access token (15 min) das outras sessões vale até expirar; elas caem na próxima renovação, como pede o CA6. Duas abas compartilham o cookie e seguem logadas.
- **Rota da troca de senha sob `/api/v1/auth/`:** o cookie `refresh_token` tem `path=/api/v1/auth`, então só chega ao servidor nessa árvore, e é ele que identifica a sessão a preservar. A rota exige access token (não entra no `permitAll`).
- **Senha atual errada responde `400`, não `401`:** o `api.js` trata `401` como sessão expirada (tenta refresh e desloga).
- **Limites:** `app.rate-limit.password-change-failures=5` e `password-change-window=15m` em `RateLimitProperties` e `application.properties`.

## Banco de dados

Nenhuma migration: `users.name` (255) e `created_at` já existem.
**Impacto em dados existentes:** nenhum.

## API

| Método | Rota | Corpo | Resposta |
|---|---|---|---|
| `GET` | `/api/v1/profile` | — | `200 {name, email, createdAt}` |
| `PUT` | `/api/v1/profile` | `{name}` | `200 {name, email, createdAt}`; `400` com `fieldErrors.name` |
| `POST` | `/api/v1/auth/password` | `{currentPassword, newPassword}` | `204`; `400` com `fieldErrors.currentPassword` ou `newPassword`; `429` com `Retry-After` |

Erros de domínio (`InvalidCurrentPasswordException`, `SamePasswordException`) em `exception/`, tratados pelo `GlobalExceptionHandler` como `400` com `fieldErrors` no `ErrorResponse`.

Mudanças incompatíveis com o cliente atual: nenhuma.

## Cliente web

- `js/api.js`: `session.name` (`localStorage`, limpo em `session.clear()`), `api.profile()`, `api.updateProfile(name)`, `api.changePassword(current, next)`.
- `js/app.js`: rota `profile` fora da lista do menu; o chip do rodapé vira link para `#/profile` e mostra `session.name`. Sem nome na sessão (logins anteriores), busca `api.profile()` uma vez e grava.
- `js/views/profile.js` (novo, mobile-first): dados (nome, e-mail só leitura, data de cadastro), formulário de nome e formulário de senha, com erro por campo. Salvar o nome atualiza `session.name` e a sidebar (CA2).
- `css`: ajuste mínimo para o chip funcionar como link, com o tema atual.

## Testes

`ProfileIntegrationTest` (`AbstractIntegrationTest`, MockMvc, `Clock` mockado):

- CA1: GET devolve os dados do próprio usuário; outro usuário vê só os seus.
- CA2: PUT com `"  Novo Nome  "` grava `"Novo Nome"` e o GET seguinte reflete.
- CA3: `""`, `"   "` e 256 caracteres → `400` com `fieldErrors.name`.
- CA4: troca com senha certa → `204`; login com a nova funciona e com a antiga não.
- CA5: senha atual errada → `400` e a senha antiga continua valendo.
- CA6: duas sessões (A e B); troca enviando o cookie de A; refresh de B → `401`, de A → `200`.
- Bordas: igual à atual → `400` e sessões intactas; 7 caracteres → `400`; 5 falhas e a 6ª com senha certa → `429` com `Retry-After`; passada a janela, processa.
- JS (`api.test.mjs`): `session.name` grava e limpa; `400` da troca de senha não dispara `session:expired`.
- Manual: trocar o nome (sidebar atualiza); trocar a senha com outra sessão aberta em janela anônima.

## Riscos e alternativas descartadas

- Contadores em memória zeram no restart (mesma limitação aceita na 005).
- `401` para senha atual errada: descartado, deslogaria o usuário no cliente.
- Rota `/api/v1/profile/password`: descartada, o cookie de refresh não chegaria a ela.

## Deploy

Sem migration nem backup. Merge em `main` só com ok explícito do Gustavo. Rollback: reverter o merge.
