# 002 — Plano técnico

**Spec:** [spec.md](spec.md)
**Status:** aprovado

## Abordagem

Tudo no pacote `auth`, reaproveitando o que a 008 criou: `OpaqueTokens` para o link, `EmailSender` da 001, `AttemptLimiter` para os limites e o mesmo desenho de "uma linha de token por usuário".

**Normalização do e-mail**
- `EmailAddress.normalize(String)`: `strip()` + `toLowerCase(Locale.ROOT)`. Usado nos compact constructors de `RegisterRequest`, `LoginRequest`, `ResendVerificationRequest` e no `EmailChangeRequest` (que já faz isso e passa a usar o helper).
- Depois da migration todo e-mail gravado está em minúsculas, então as buscas continuam por igualdade (`findUserByEmail`), sem `IgnoreCase`.

**Cadastro — `UserService.createUser`** (`@Transactional`, resposta `202` sem corpo nos três casos)
1. Limite atual por IP (`register-per-ip`).
2. E-mail livre: cria `User` com `emailVerifiedAt = null`, gera o token e envia o link.
3. Conta não verificada: atualiza nome e senha, substitui o token e envia o link.
4. Conta verificada: não grava nada e envia o aviso "tentaram criar uma conta com seu e-mail", respeitando `register-notice:<email>` (1 por hora). Acima do limite, não envia, e a resposta é a mesma.
5. Falha no envio do link (casos 2 e 3) desfaz a transação e responde `502`. No caso 4 a falha só vai para o log, para a resposta não diferir dos outros casos.

**Login — `AuthService.login`**
- Depois de conferir a senha: se `emailVerifiedAt == null`, lança `EmailNotVerifiedException` → `403` no formato `ErrorResponse`. Senha errada continua `401`, então o `403` só aparece para quem sabe a senha. O contador de falhas de login não conta esse caso.

**Verificação — `POST /api/v1/auth/verification/confirm` `{token}`** (`permitAll`)
- Tabela `email_verification_tokens`, uma linha por usuário (`UNIQUE(user_id)`), com `used_at`. Reenviar substitui hash, validade e zera `used_at`, então o link anterior deixa de existir.
- Token não encontrado ou expirado → `400` (`InvalidVerificationTokenException`).
- Token já usado → `204` (idempotente; a tela mostra "conta já verificada").
- Válido → `emailVerifiedAt = clock.instant()`, `usedAt = clock.instant()`, `204`. Não emite tokens nem mexe em sessão aberta.

**Reenvio — `POST /api/v1/auth/verification/resend` `{email}`** (`permitAll`, sempre `202`)
- Limites: `verification-resend:<email>` (3 por hora) e `verification-resend-ip:<ip>` (10 por hora). Acima do limite, `429` (o limite por IP não revela nada; o por e-mail também vale para e-mails inexistentes, para não diferir).
- Conta não verificada → link novo. Conta verificada → e-mail "sua conta já está ativa". Sem conta → nada.
- Falha de envio vai para o log e a resposta continua `202`, para não revelar a conta.

**Links**: `<public-url>/#/verificar-email?token=<raw>`, token no fragmento como na 008.

**Troca de e-mail (008)**: confirmar a troca já prova a posse do e-mail novo, então nada muda. `existsByEmailIgnoreCase` pode virar `existsByEmail` depois da migration, mas fica como está (continua correto).

## Banco de dados

`V12__add_email_verification.sql`, em uma transação (Postgres desfaz tudo se algo falhar):
```sql
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ NULL;
UPDATE users SET email_verified_at = created_at;           -- CA5: quem já existe entra verificado
UPDATE users SET email = LOWER(TRIM(email));               -- CA8
ALTER TABLE users ADD CONSTRAINT users_email_normalized CHECK (email = LOWER(TRIM(email)));

CREATE TABLE email_verification_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id),
    UNIQUE(token_hash),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

**⚠ Impacto em dados existentes — precisa de aprovação:**
- Todos os usuários atuais recebem `email_verified_at = created_at` (passam a contar como verificados).
- Todos os e-mails são convertidos para minúsculas e sem espaços nas pontas.
- Se existirem duas contas iguais ignorando maiúsculas, o `UNIQUE(email)` faz a migration falhar e o app não sobe. **Antes do deploy, o Gustavo roda na VPS:**
  ```sql
  SELECT LOWER(TRIM(email)), COUNT(*) FROM users GROUP BY 1 HAVING COUNT(*) > 1;
  ```
  Se voltar alguma linha, paramos e decidimos o que fazer com essas contas antes de seguir.
- Backup obrigatório antes do deploy (`pg_dump`).

## API

| Método | Rota | Mudança |
|---|---|---|
| `POST` | `/auth/register` | **muda**: `202` sem corpo nos três casos (antes `201` com o usuário, e `409` para e-mail existente); `502` se o link não puder ser enviado |
| `POST` | `/auth/login` | **muda**: `403` para conta não verificada com a senha certa |
| `POST` | `/auth/verification/confirm` | novo: `{token}` → `204`; `400` inválido ou expirado |
| `POST` | `/auth/verification/resend` | novo: `{email}` → sempre `202`; `429` acima do limite |

Mudanças incompatíveis com o cliente atual: cadastro e login, mas o único cliente é o web, atualizado junto.

## Cliente web

- `js/api.js`: `register` passa a não esperar corpo; `resendVerification(email)` e `confirmVerification(token)` (anônimas).
- `js/views/auth.js`: depois do cadastro, mostra "Enviamos um link para <e-mail>. Abra para ativar a conta" com o botão "Reenviar link", em vez de logar. Login com `403` mostra a explicação e o mesmo botão.
- `js/views/verify-email.js` (novo, mobile-first, no padrão do `confirm-email.js`): rota pública `#/verificar-email`, confirma uma vez, mostra "Conta verificada" com "Entrar", ou "Link inválido ou expirado" com campo para pedir outro.
- `js/app.js`: a rota pública entra antes da checagem de login.

## Testes

`EmailVerificationIntegrationTest` (`AbstractIntegrationTest`, `Clock` mockado, `RecordingEmailSender`):
- CA1: cadastro → `202`, conta não verificada, link enviado ao e-mail.
- CA2: login com senha certa antes de verificar → `403`; senha errada → `401`.
- CA3: confirmar → `204` e login `200`.
- CA4: expirado (clock + 24h) e substituído por reenvio → `400`; pedir outro funciona.
- CA5: `MigrationV12Test` roda o Flyway num schema separado até a `V11`, insere usuários como existem hoje (alguns com maiúsculas), migra para a `V12` e confere que todos ficaram verificados, com e-mail em minúsculas, e conseguem logar.
- CA6: reenvio para e-mail inexistente, não verificado e verificado → mesmo status e corpo.
- CA7: cadastro com e-mail verificado → mesma resposta do cadastro novo, nenhuma conta nova, aviso ao dono; segundo aviso na mesma hora não é enviado.
- CA8: cadastro com maiúsculas grava minúsculas; login com outra caixa funciona; cadastro com outra caixa cai no CA7.
- Bordas: link usado duas vezes → `204` nas duas; confirmar logado em outra conta não altera a sessão; cadastro repetido de não verificada troca a senha e invalida o link anterior; falha de envio no cadastro → `502` e nada gravado; limites do reenvio → `429`.
- **Testes existentes**: 24 classes cadastram e logam em seguida. Um helper em `AbstractIntegrationTest` (`markVerified(email)`, via `JdbcTemplate`) entra nos helpers de cadastro de cada classe. Nenhuma asserção existente muda, exceto as que esperam `201`/`409` do cadastro, que passam a esperar `202`.
- JS: chamadas novas; `403` no login não dispara `session:expired`; confirmar com `400` também não.
- Manual (1280px e 390px): cadastrar, ver o e-mail no log, abrir o link, entrar; tentar entrar antes de verificar e reenviar.

## Riscos e alternativas descartadas

- **Duplicatas por maiúsculas em produção** → consulta antes do deploy; migration falha sem alterar nada se existirem.
- **Diferença de tempo de resposta** pode revelar se o e-mail existe (há envio síncrono em alguns casos e não em outros). No cadastro os três casos enviam e-mail, então o tempo é parecido; no reenvio, e-mail inexistente responde mais rápido. Aceito: exige muitas tentativas e o limite por IP restringe.
- **Abuso do aviso ao dono** (alguém cadastra o e-mail de outro para enchê-lo de avisos) → no máximo 1 aviso por hora por e-mail, além do limite por IP.
- **Link no painel do Resend**: o link só verifica, não abre sessão, e expira em 24h.
- Verificar e já entrar logado foi descartado (decisão da spec).
- Bloquear o e-mail para sempre por uma conta não verificada foi descartado: o cadastro repetido reaproveita a conta.
- Feature flag para desligar a verificação nos testes foi descartada: mais um caminho de código em produção só para facilitar teste.

## Deploy

1. Na VPS, rodar a consulta de duplicatas acima. Só seguir se voltar vazia.
2. Backup (`pg_dump`) antes de subir.
3. Merge em `main` com ok explícito do Gustavo; `pull` + `up -d app`; conferir no log o Flyway aplicando a `V12`.
4. Teste em produção: cadastrar uma conta de teste, receber o link, verificar, entrar.

Rollback: voltar a imagem anterior. A coluna e a tabela novas não atrapalham o código antigo, e os e-mails continuam em minúsculas (o código antigo funciona com eles; quem digitar com maiúsculas no código antigo não entra, como já acontecia). Restaurar o backup só se algo der errado com dados.
