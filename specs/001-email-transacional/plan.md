# 001 — Plano técnico

**Spec:** [spec.md](spec.md)
**Status:** proposto

## Abordagem

Pacote novo `mail`, sem dependência nova: o Resend é chamado por HTTP com `RestClient` (já incluído no Spring Web).

- `EmailMessage(String to, String subject, String text, String html)`: `text` obrigatório (versão legível sem HTML), `html` opcional.
- `EmailSender` (interface com `void send(EmailMessage)`): é só ela que as outras features usam ("envie este e-mail para esta pessoa").
- `ResendEmailSender` (`@Profile("prod")`): `POST https://api.resend.com/emails` com `Authorization: Bearer <chave>`, `from` no formato `Money Pilot <endereço>`. Timeouts de conexão (3 s) e leitura (10 s) pelo `JdkClientHttpRequestFactory`, para um provedor lento não segurar a requisição. Resposta não-2xx, timeout ou erro de rede viram `EmailSendException`.
- `LoggingEmailSender` (`@Profile("!prod")`): não envia nada e escreve destinatário, assunto e texto no log (CA3).
- `MailProperties` (`@ConfigurationProperties("app.mail")`, `@Validated`): `fromAddress`, `fromName` (padrão `Money Pilot`), `resendApiKey`, `baseUrl` do provedor (padrão `https://api.resend.com`, sobrescrevível nos testes). No profile `prod`, `fromAddress` e `resendApiKey` são obrigatórios: `application-prod.properties` usa `${MAIL_FROM_ADDRESS}` e `${RESEND_API_KEY}` sem default e a validação recusa valor vazio, então o boot falha com mensagem clara (CA2), como o `JWT_SECRET`.
- **Erro (CA4):** `EmailSendException` em `exception/`, tratada no `GlobalExceptionHandler` com `ErrorResponse` e status `502`, mensagem em português ("Não foi possível enviar o e-mail. Tente novamente."). O log registra o status do provedor e a causa, nunca a chave nem o cabeçalho `Authorization`. O `api.js` só trata `401` como sessão expirada, então `502` aparece como erro comum na tela.
- **URL pública:** propriedade `app.public-url` (`PUBLIC_URL`, default `http://localhost:8080` no local) para as features montarem links nos e-mails. Em prod é obrigatória (sem default), pelo mesmo motivo do remetente.
- Sem templates nesta spec: cada feature (002, 003, 008) monta o seu `EmailMessage`.

## Banco de dados

Nenhuma migration.
**Impacto em dados existentes:** nenhum.

## API

Nenhuma rota nova. Nova resposta de erro possível nas features que enviarem e-mail: `502` no formato `ErrorResponse`.

Mudanças incompatíveis com o cliente atual: nenhuma.

## Cliente web

Nenhuma mudança.

## Testes

- `ResendEmailSenderTest` (com `MockRestServiceServer`, sem rede): envio correto (cabeçalhos, remetente, corpo em texto e HTML) e erro do provedor/timeout → `EmailSendException` sem a chave na mensagem nem no log (CA4).
- `LoggingEmailSenderTest`: o conteúdo vai para o log e nada sai pela rede (CA3).
- `MailConfigurationTest` (contexto com profile `prod` e sem `RESEND_API_KEY`/`MAIL_FROM_ADDRESS`): o boot falha com a mensagem da propriedade faltando (CA2).
- Teste de integração do handler: `EmailSendException` → `502` com `ErrorResponse`.
- Suporte de teste: `RecordingEmailSender` (`@Primary` em `src/test`) que guarda as mensagens enviadas, para as specs 002, 003 e 008 verificarem o que foi pedido.
- **CA1 é manual** (depende de domínio verificado e do provedor real): enviar um e-mail de teste em produção e conferir caixa de entrada, remetente e que não caiu no spam.

## Riscos e alternativas descartadas

- Envio síncrono segura a requisição até o timeout (10 s) → aceito na spec; fila de reenvio está fora de escopo.
- Provedor fora do ar bloqueia features que dependem do e-mail → o erro é claro na tela e a pessoa tenta de novo.
- SDK do Resend ou `spring-boot-starter-mail`/SMTP foram descartados: dependência nova sem aprovação e o roteiro decidiu por HTTP.
- Chave só em variável de ambiente; nunca no repositório nem no log.

## Deploy

Sem migration, sem backup. Antes do merge em `main`, o Gustavo precisa:
1. Verificar o domínio no Resend (SPF/DKIM) e criar a chave de API.
2. Adicionar `RESEND_API_KEY`, `MAIL_FROM_ADDRESS` e `PUBLIC_URL` ao `.env` da VPS (o Claude atualiza `docker-compose.prod.yml` e `.env.production.example` com placeholders). Sem elas o app não sobe.
3. Depois do deploy, rodar o teste manual do CA1.

Rollback: reverter o merge e a imagem anterior volta a subir; as variáveis novas ficam sem uso.
