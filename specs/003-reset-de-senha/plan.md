# 003 — Plano técnico

**Spec:** [spec.md](spec.md)
**Status:** aprovado

## Abordagem

Tudo no pacote `auth`, no mesmo desenho da verificação (002) e da troca de e-mail (008): `OpaqueTokens` para o link, uma linha de token por usuário, `EmailSender` para o link e o aviso, `AttemptLimiter` para os limites e `RefreshTokenService.endAllExcept` para encerrar as sessões.

**Pedido — `POST /api/v1/auth/password/forgot` `{email}`** (`permitAll`, sempre `202`)
- `email` normalizado por `EmailAddress.normalize`, como no cadastro e no login.
- Limites: `password-reset-ip:<ip>` (5 por hora) e `password-reset:<email>` (3 por hora), conferidos e contados antes de buscar a conta. Acima do limite, `429`; o limite por e-mail vale também para e-mails inexistentes, para não diferir.
- Conta existe: gera o token (substituindo o anterior, que deixa de valer) e envia o link `<public-url>/#/redefinir-senha?token=<raw>`, válido por 30 minutos.
- Conta não existe: nada é enviado.
- Falha no envio vai para o log e a resposta continua `202`, para não revelar a conta.

**Redefinição — `POST /api/v1/auth/password/reset` `{token, newPassword}`** (`permitAll`)
- `newPassword` com as regras do cadastro (`@NotBlank`, `@Size(min = 8)`); fora disso, `400` com erro no campo.
- Token não encontrado ou expirado → `400` (`InvalidPasswordResetTokenException`). O token expirado é apagado.
- Válido, em uma transação:
  1. grava o hash da senha nova;
  2. se `emailVerifiedAt` é nulo, preenche com `clock.instant()` (CA6);
  3. apaga o token (uso único: usado é igual a inexistente, CA5);
  4. apaga **todos** os refresh tokens da conta (`endAllExcept(userId, null)`), e as sessões abertas caem na próxima renovação (CA4);
  5. zera o contador `login-email:<email>`, desbloqueando o login.
- Por último, envia o aviso "sua senha foi alterada" (CA7), como o aviso da troca de e-mail: falha no envio só vai para o log e não desfaz a troca.
- Responde `204`, sem tokens: a pessoa vai para o login.
- A senha nova pode ser igual à atual: quem esqueceu a senha não sabe qual era, e recusar revelaria a senha antiga a quem tem o link.

## Banco de dados

`V13__create_password_reset_tokens_table.sql`:
```sql
CREATE TABLE password_reset_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE(user_id),
    UNIQUE(token_hash),

    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

**Impacto em dados existentes:** nenhum. Só cria uma tabela.

## API

| Método | Rota | Mudança |
|---|---|---|
| `POST` | `/auth/password/forgot` | novo: `{email}` → sempre `202`; `429` acima do limite |
| `POST` | `/auth/password/reset` | novo: `{token, newPassword}` → `204`; `400` link inválido ou expirado, ou senha fora das regras |

Mudanças incompatíveis com o cliente atual: nenhuma. `POST /auth/password` (troca logado) continua igual.

## Cliente web

- `js/api.js`: `forgotPassword(email)` e `resetPassword(token, newPassword)`, ambas anônimas (um `400` não pode derrubar sessão).
- `js/views/auth.js`: link "Esqueci minha senha" no login, que abre um formulário só com o e-mail. Enviado, mostra "Se houver uma conta com esse e-mail, enviamos um link para redefinir a senha (vale por 30 minutos)" e "Voltar para entrar".
- `js/views/reset-password.js` (novo, mobile-first, no padrão de `verify-email.js`, com as mesmas regras de acessibilidade): rota pública `#/redefinir-senha`. Lê o token da URL e o tira da barra de endereço; formulário com a senha nova (mínimo 8). Sucesso → "Senha alterada. Entre com a nova senha." e "Entrar". `400` de link → "Link inválido ou expirado" com campo para pedir outro.
- `js/app.js`: a rota pública entra antes da checagem de login.

## Testes

`PasswordResetIntegrationTest` (`AbstractIntegrationTest`, `Clock` mockado, `RecordingEmailSender`):
- CA1: pedido para e-mail cadastrado → `202` e um e-mail com o link.
- CA2: pedido para e-mail inexistente → mesmo status e corpo do CA1, nenhum e-mail.
- CA3: redefinir → `204`; login com a nova `200`, com a antiga `401`.
- CA4: sessão aberta antes do reset → refresh `401` depois.
- CA5: link expirado (clock + 30 min), já usado e substituído por novo pedido → `400`; o novo link funciona.
- CA6: conta não verificada → depois do reset, verificada e login `200`.
- CA7: aviso enviado ao e-mail da conta; falha no aviso mantém a troca.
- Bordas: login bloqueado por 3 falhas é liberado pelo reset; senha com menos de 8 → `400` com erro no campo e o link continua valendo; limites por e-mail e por IP → `429`; e-mail com maiúsculas no pedido encontra a conta; falha no envio do link → `202`.
- JS: chamadas novas anônimas (`400` não dispara `session:expired`); telas com o DOM falso (`fake-dom.mjs`): formulário de "esqueci", resultado e erros anunciados, token tirado da URL.
- Manual (1280px e 390px): pedir o link, abrir pelo log, redefinir, entrar; link expirado.

## Riscos e alternativas descartadas

- **Diferença de tempo de resposta** entre e-mail existente (envia) e inexistente (não envia) pode revelar contas. Aceito como na 002: exige muitas tentativas e os limites restringem.
- **Link no painel do Resend**: vale só 30 minutos, é de uso único e não abre sessão.
- Entrar logado direto pelo link: descartado (decisão da spec).
- Recusar senha igual à atual: descartado (ver Abordagem).
- Guardar o token usado (como na 002) para mostrar "já usado": descartado; para reset, usado e inválido levam à mesma ação (pedir outro).

## Deploy

1. Backup (`pg_dump`) antes de subir, por haver migration nova.
2. Merge em `main` com ok explícito do Gustavo; `pull` + `up -d app`; conferir no log o Flyway aplicando a `V13`.
3. Teste em produção: pedir o reset da conta de teste, redefinir, entrar com a senha nova e conferir o aviso.

Rollback: voltar a imagem anterior. A tabela nova não atrapalha o código antigo.
