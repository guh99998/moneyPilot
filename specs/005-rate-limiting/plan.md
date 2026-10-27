# 005 — Plano técnico

**Spec:** [spec.md](spec.md)
**Status:** aprovado

## Abordagem

Limitador em memória próprio (`auth/AttemptLimiter`), sem dependência nova, com o `Clock` injetado. Janela fixa que começa no primeiro evento e expira após a duração. Uma instância só: reiniciar o app zera os contadores.

- `check(key, limit, window)` lança `TooManyAttemptsException` quando o contador já atingiu o limite; `record(key, window)` incrementa; `reset(key)` apaga. Limpeza oportunista de janelas expiradas.
- Login: `check` em `login-ip:<ip>` e `login-email:<email normalizado>` antes de olhar a senha; `record` no IP a cada tentativa; `record` no e-mail a cada falha; `reset` do e-mail no sucesso.
- Cadastro: `check` + `record` em `register-ip:<ip>` para toda tentativa que passou da validação de campos, inclusive e-mail já cadastrado.
- IP real: `request.getRemoteAddr()`; em produção `server.forward-headers-strategy=framework` o resolve a partir do `X-Forwarded-For` do Caddy.
- Limites em `app.rate-limit.*`, lidos por um `@ConfigurationProperties` record.

## Banco de dados

Nenhuma migration. **Impacto em dados existentes:** nenhum.

## API

| Método | Rota | Mudança |
|---|---|---|
| `POST` | `/auth/login` | Pode responder `429` com `Retry-After` (segundos) |
| `POST` | `/auth/register` | Pode responder `429` com `Retry-After` (segundos) |

Mudanças incompatíveis com o cliente atual: nenhuma.

## Cliente web

`static/js/api.js`: `429` vira `ApiError` com "Muitas tentativas. Tente de novo em N minutos." a partir do `Retry-After`. `views/auth.js` não muda.

## Testes

`RateLimitIntegrationTest` com os limites reais e `Clock` mockado: CA1 (3 erros, 4ª recusada), CA2 (cadastro por IP), CA3 (janela expira), CA4 (2 erros + acerto), CA5 (`X-Forwarded-For`), limite por IP com e-mails diferentes. Teste JS em `api.test.mjs`. Manual: errar a senha 3 vezes na tela.

## Riscos e alternativas descartadas

- `X-Forwarded-For` forjável se o Caddy confiar em proxies amplos → conferir o Caddyfile antes do merge em `main`.
- Bloqueio por e-mail permite travar a conta de outra pessoa por 15 min → risco aceito.
- Bucket4j/Redis descartados: dependência nova e uma só instância.

## Deploy

Sem backup nem migration. Na VPS (só leitura, Gustavo): o Caddyfile não pode ter `trusted_proxies` amplo e o `docker-compose.prod.yml` não pode publicar `ports:` do app. Rollback: reverter o merge.
