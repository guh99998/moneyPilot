# 010 — Plano técnico

**Spec:** [spec.md](spec.md)
**Status:** aprovado

## Abordagem

O chamado vira um e-mail para o suporte, sem banco. Um domínio novo `support/` valida o pedido, identifica quem envia, confere os limites no `AttemptLimiter` e envia pelo `EmailSender` existente, que ganha um `replyTo` opcional. A rota é pública com login opcional. O cliente ganha a tela `#/suporte`.

- **Quem envia:** logado (JWT válido) usa o e-mail e o nome da conta e ignora o `email` do corpo; sem login, o `email` é obrigatório (400 no campo `email`).
- **Token expirado:** requisição anônima com `Authorization: Bearer` responde `401`, para o `api.js` renovar e reenviar em vez de pedir e-mail.
- **Limites:** `support-account:{id}` ou `support-email:{email}` (3/h) e `support-ip:{ip}` (10/h). O registro no limiter só acontece depois do envio com sucesso, então uma falha não gasta a cota.
- **Número do chamado:** 6 caracteres de `SecureRandom`, sem caracteres ambíguos (`23456789ABCDEFGHJKMNPQRSTUVWXYZ`).
- **Destino:** `app.support.email` (`SUPPORT_EMAIL`), obrigatório em prod.

## Banco de dados

Nenhuma migration. **Impacto em dados existentes:** nenhum.

## API

| Método | Rota | Mudança |
|---|---|---|
| POST | `/api/v1/support/tickets` | Nova. Pública, com login opcional. Corpo `{category, message, email?}`. `200` com `{reference, replyTo}`; `400`, `401` (Bearer inválido), `429` (`Retry-After`), `502` (falha no envio). |

Mudanças incompatíveis com o cliente atual: nenhuma. `EmailMessage` ganha `replyTo` mantendo os construtores atuais.

## Cliente web

- `js/views/support.js` (novo): `renderSupport()` com categoria, mensagem (contador, `maxlength` 2000) e e-mail (só sem login). Erros por campo, botão "Enviando…" contra duplo clique, mensagem de sucesso focada com o número do chamado.
- `js/app.js`: rota pública `#/suporte` (standalone sem sessão, dentro do layout com sessão) e item "Falar com o suporte" no rodapé do menu.
- `js/views/auth.js`: link "Não consegue entrar? Falar com o suporte" no login.
- `js/api.js`: `api.sendSupportTicket(body)`.

## Testes

- `SupportIntegrationTest`: CA1 a CA5 e bordas (HTML/acentos, conta só com Google, Bearer expirado).
- `MailConfigurationTest`/teste de configuração: prod sem `SUPPORT_EMAIL` não sobe.
- `ResendEmailSenderTest`: envia `reply_to`.
- `support-view.test.mjs`, mais o link no `auth-view.test.mjs`.

## Riscos e alternativas descartadas

- Abuso do formulário público → limites por e-mail/conta e por IP.
- E-mail de outra pessoa no `Reply-To` → aceito pela spec; o suporte responde para esse endereço.
- Guardar chamados no banco → descartado (fora de escopo; dado de usuário a proteger e apagar na 012).

## Deploy

`SUPPORT_EMAIL` precisa estar no `.env` da VPS antes de subir a versão (sem ela o app não sobe). Sem migration, o rollback é voltar a imagem anterior.
