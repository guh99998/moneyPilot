# 008 — Plano técnico

**Spec:** [spec.md](spec.md)
**Status:** aprovado

## Abordagem

Tudo no pacote `auth`, seguindo o padrão de `ProfileService.changePassword`. O envio de e-mail usa o serviço da 001, que precisa estar entregue antes do T5.

**Pré-requisito: o JWT passa a usar o id como subject** (commit `refactor(auth)` separado)
- `JwtService.generateToken(Long userId)` grava o subject como `String.valueOf(userId)`. `extractEmail` vira `extractUserId` e devolve `Long`.
- `JwtAuthenticationFilter`: subject não numérico (token emitido antes do deploy) segue sem autenticação, como `JwtException`. O resultado é `401` e o `api.js` renova o token sozinho.
- `AuthenticatedUserProvider.getCurrentUserId()` converte o nome para `Long` e confere com `existsById` (mantém o `401` para usuário inexistente).
- `AuthService.login/refresh` passam `user.getId()`.

**Token de confirmação**
- A geração do token (32 bytes, base64url) e o SHA-256 saem de `RefreshTokenService` para o componente `OpaqueTokens`, usado pelos dois serviços.
- Só o hash fica no banco. O link é `<url pública>/#/confirmar-email?token=<raw>`; o token fica no fragmento e não aparece em log de servidor.
- Cada usuário tem no máximo um pedido (`UNIQUE(user_id)`). Pedir de novo substitui a linha. Confirmar apaga a linha, então "já usado" e "substituído" caem em link não encontrado.

**Pedido — `EmailChangeService.request`** (`@Transactional`)
1. `attemptLimiter.check("password-change:<id>", ...)` e `check("email-change:<id>", ...)`.
2. Senha errada → `record` no contador de senha + `InvalidCurrentPasswordException`.
3. `newEmail` igual ao atual (ignorando maiúsculas) → `SameEmailException` (`400 fieldErrors.newEmail`).
4. Em uso por outra conta (`existsByEmailIgnoreCase`) → `EmailAlreadyInUseException` (`400 fieldErrors.newEmail`).
5. Grava ou substitui o pedido (`expiresAt = clock.instant() + 24h`), envia o link e registra no contador `email-change`. Falha no envio desfaz a transação.

**Confirmação — `EmailChangeService.confirm(rawToken, refreshCookie)`** (`@Transactional`)
1. Não achou pelo hash → `InvalidEmailChangeTokenException` (`400`).
2. Expirado → apaga a linha e lança a mesma exceção.
3. E-mail tomado depois do pedido → apaga a linha e lança `EmailAlreadyInUseException` (o `UNIQUE` de `users.email` cobre a corrida).
4. `user.setEmail(newEmail)` + `saveAndFlush`, apaga o pedido e chama `refreshTokenService.endAllExcept(userId, refreshCookie)`.
5. Avisa o e-mail antigo. Falha → `log.error` sem dados sensíveis, e a troca continua valendo.

**Rotas**: pedido em `/api/v1/profile/email` (autenticada); confirmação em `/api/v1/auth/email/confirm` (`permitAll`), para o cookie `refresh_token` chegar.

**Limites**: `app.rate-limit.email-change-requests=3` e `email-change-window=1h`.

## Banco de dados

`V11__create_email_change_tokens_table.sql`: tabela `email_change_tokens` (`user_id` UNIQUE, `new_email`, `token_hash` UNIQUE, `expires_at`, `created_at`, FK para `users`).

**Impacto em dados existentes:** nenhum (tabela nova, `users` não muda).

## API

| Método | Rota | Mudança |
|---|---|---|
| `POST` | `/api/v1/profile/email` | novo: `{newEmail, currentPassword}` → `204`; `400` com `fieldErrors`; `429` com `Retry-After` |
| `POST` | `/api/v1/auth/email/confirm` | novo: `{token}` → `204`; `400` link inválido/expirado ou e-mail em uso |
| `GET` | `/api/v1/profile` | ganha `pendingEmail` (`null` sem pedido válido) |

Mudanças incompatíveis com o cliente atual: nenhuma. A troca do subject do JWT invalida os access tokens já emitidos no deploy, e o cliente renova sozinho.

## Cliente web

- `js/api.js`: `requestEmailChange` e `confirmEmailChange`.
- `js/app.js`: `#/confirmar-email` vira rota pública.
- `js/views/confirm-email.js` (novo): confirma ao abrir e mostra sucesso ou "Link inválido ou expirado".
- `js/views/profile.js`: formulário "Trocar e-mail", pendente e Reenviar.

## Testes

`EmailChangeIntegrationTest` cobre CA1–CA6, as bordas e o JWT (subject antigo → `401`; token antigo segue apontando para o mesmo usuário após a troca). Teste JS para as rotas novas. Verificação manual em 1280px e 390px.

## Riscos e alternativas descartadas

- O login continua sensível a maiúsculas até a 002 normalizar → a tela mostra o e-mail gravado.
- O envio é síncrono dentro da transação → timeout é o da 001.
- Contadores em memória zeram no restart (limitação aceita na 005).
- Manter o e-mail no subject do JWT foi descartado: tokens ficariam órfãos ou apontariam para outra conta.
- Histórico de tokens com `used_at` foi descartado: uma linha por usuário já cobre uso único e substituição.

## Deploy

Migration só cria tabela, sem backup extra. Depende da configuração de envio da 001 em produção. Rollback: reverter o merge; a tabela fica órfã e não atrapalha.
