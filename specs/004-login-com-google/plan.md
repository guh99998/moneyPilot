# 004 — Plano técnico

**Spec:** [spec.md](spec.md)
**Status:** aprovado

## Abordagem

Tudo no pacote `auth`, ao lado do login por senha. O front envia o ID token do Google (`credential`); o backend o valida com o jjwt que já existe e as chaves públicas do Google, sem dependência nova, e emite os mesmos tokens do `AuthService.login`.

**Validação do ID token**
- Interface `GoogleTokenVerifier` → `GoogleIdentity(subject, email, name, picture)`.
- `JjwtGoogleTokenVerifier`: busca as chaves em `https://www.googleapis.com/oauth2/v3/certs` com `RestClient` (montado como em `mail/ResendEmailSender.java`, com timeouts), lê o JWK Set com `io.jsonwebtoken.security.Jwks` e guarda em cache pelo `max-age`. Usa `Jwts.parser().keyLocator(...)`, `requireAudience(clientId)`, emissor `accounts.google.com` ou `https://accounts.google.com` e o `Clock` injetado para a expiração. Exige `email_verified = true`. Qualquer falha vira `InvalidGoogleTokenException` → `401`.
- A busca das chaves fica atrás de `GoogleKeySource`, para testar com uma chave RSA gerada no teste.

**Configuração:** `app.google.client-id` ← `GOOGLE_CLIENT_ID`. Vazio desliga o recurso: o botão não aparece e o endpoint responde `401`.

**`GoogleSignInService`**
- `signIn(credential, ip)`: limite por IP (`login-ip`, do `AttemptLimiter`); busca a conta pelo `google_subject`; se não achar, pelo e-mail normalizado — vinculada a outro `sub` → `409`; não verificada → verifica, apaga a senha e o token de verificação pendente e vincula; verificada → vincula; sem conta → cria verificada e sem senha, com o nome do Google (ou a parte antes do `@`). Por fim atualiza `google_email` e `avatar_url` (se não estiver oculta) e emite os tokens.
- `link(userId, credential, currentPassword)`: confere a senha com o contador `password-change:<id>`; `sub` já ligado a outra conta → `409`; conta já vinculada → `409`; grava vínculo e foto.
- `unlink(userId, currentPassword)`: sem senha → `409`; senha errada → `400` no campo; limpa `google_subject`, `google_email` e `avatar_url`.

**Ajustes:** `AuthService.login` com `passwordHash` nulo segue o caminho de senha errada (`401`). `ProfileService.changePassword` e `EmailChangeService` tratam hash nulo como senha atual errada (`400` no campo). O reset (003) já grava a senha sem depender da anterior, então serve para "Criar senha".

## Banco de dados

`V14__add_google_sign_in.sql`:
```sql
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users ADD COLUMN google_subject VARCHAR(255) NULL UNIQUE;
ALTER TABLE users ADD COLUMN google_email VARCHAR(255) NULL;
ALTER TABLE users ADD COLUMN avatar_url VARCHAR(2048) NULL;
ALTER TABLE users ADD COLUMN avatar_hidden BOOLEAN NOT NULL DEFAULT FALSE;
```

**Impacto em dados existentes:** nenhuma linha é alterada; só o schema muda e a coluna booleana nasce `FALSE`. Por haver migration, o backup antes do deploy é obrigatório.

## API

| Método | Rota | Mudança |
|---|---|---|
| `GET` | `/auth/google/config` (público) | novo: `{clientId}` (vazio = desligado) |
| `POST` | `/auth/google` (público) | novo: `{credential}` → `200 {token, email}` + cookie de refresh; `401` token inválido; `409` e-mail já ligado a outro Google; `429` |
| `POST` | `/profile/google` | novo: `{credential, currentPassword}` → `204`; `400` senha errada ou token recusado (erro no campo `credential`, para o cliente não tratar como sessão expirada); `409` Google já usado ou conta já vinculada; `429` |
| `POST` | `/profile/google/unlink` | novo: `{currentPassword}` → `204`; `400`; `409` sem senha |
| `DELETE` | `/profile/avatar` | novo: oculta a foto → `204` |
| `POST` | `/profile/avatar/google` | novo: volta a usar a foto do Google → `204`; `409` sem vínculo |
| `GET`/`PUT` | `/profile` | `ProfileResponse` ganha `hasPassword`, `googleEmail` (nulo = não vinculado) e `avatarUrl` (nulo se oculta ou sem foto), `avatarHidden` |

O desvincular é `POST` com corpo, para não usar `DELETE` com corpo. Mudanças incompatíveis com o cliente atual: nenhuma (só campos novos).

## Cliente web

- `js/google.js` (novo): carrega o script do Google uma vez, só quando um botão vai aparecer; pega o client ID em `/auth/google/config` e desenha o botão oficial. Sem client ID ou com o script bloqueado, o contêiner some.
- `js/api.js`: `googleConfig()` e `loginWithGoogle(credential)` anônimas; `profile.linkGoogle`, `unlinkGoogle`, `hideAvatar`, `useGoogleAvatar`.
- `views/auth.js`: "Entrar com Google" no login e no cadastro depois de um separador "ou"; dica fixa no login; `409` na caixa de erro (`role=alert`).
- `views/profile.js`: cartão Google (vincular/desvincular), "Criar senha" para quem não tem senha, aviso no cartão de e-mail, foto no cartão Dados com "Remover foto" e "Usar foto do Google".
- `app.js`: avatar (foto ou iniciais) ao lado do nome no rodapé do menu.
- `css/app.css`: `.avatar`, separador "ou", contêiner do botão e cartão Google, mobile-first, alvos de 44px.

## Testes

- `GoogleSignInIntegrationTest` (verificador falso em `@TestConfiguration`): CA1–CA8 e as bordas da spec.
- `JjwtGoogleTokenVerifierTest` (unitário, chave RSA gerada e `GoogleKeySource` falso): token válido; assinatura errada; audience de outro app; emissor errado; expirado; `email_verified` falso.
- JS (`fake-dom.mjs`): botão escondido sem client ID e com script bloqueado; credencial leva ao login; `409` anunciado; perfil com e sem senha; vincular, desvincular, foto e iniciais; chamadas novas do `api.js` anônimas onde devem ser.
- Revisão final com `web-design-guidelines`, `accessibility-compliance`, `responsive-design` (telas) e `api-design-principles` (rotas novas).

## Riscos e alternativas descartadas

- **Vincular automaticamente pelo e-mail, sem senha:** aceito só no login (o Google garante o e-mail verificado); no perfil, vincular exige a senha atual.
- **Biblioteca oficial do Google no backend:** descartada (dependência nova); jjwt + JWKS resolve.
- **Guardar a foto:** descartado; só a URL.

## Deploy

1. Criar o client OAuth (Web) com a origem de produção (Gustavo).
2. Backup (`pg_dump`) antes de subir, por haver migration.
3. Definir `GOOGLE_CLIENT_ID` no `.env`; merge em `main` só com ok explícito do Gustavo; conferir no log o Flyway aplicando a `V14`.

Rollback: voltar a imagem anterior. A V14 só relaxa um `NOT NULL` e adiciona colunas, então o código antigo segue funcionando, exceto para contas criadas pelo Google, que ficam sem login até o retorno.
