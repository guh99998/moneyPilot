# 001 — Tarefas

**Plano:** [plan.md](plan.md)

- [x] **T1** — Spec, plano e tarefas
- [x] **T2** — `EmailMessage`, `EmailSender`, `MailProperties` e `app.public-url` (CA2)
- [x] **T3** — `LoggingEmailSender` para `local` e testes (CA3)
- [x] **T4** — `EmailSendException`, handler `502` e `ResendEmailSender` (CA4)
- [x] **T5** — Configuração de `prod`, `docker-compose.prod.yml` e `.env.production.example` (CA2)
- [x] **T6** — Testes (CA2, CA3, CA4) e `RecordingEmailSender` de apoio
- [x] **T7** — README (variáveis, domínio, decisão síncrona) e checklist de deploy
- [x] **T8** — Teste manual em produção do e-mail chegando fora do spam (CA1), feito pelo Gustavo

## Notas de implementação

- `ResendEmailSender` valida remetente e chave no construtor (e não por `@NotBlank` na propriedade), para a exigência valer só em `prod`. `app.public-url` é validada com `@NotBlank` em todos os profiles, com default local.
- CA2 é coberto com `ApplicationContextRunner` em vez de subir o contexto inteiro com Postgres.
- Em produção o primeiro deploy falhou no boot: `RestClient.Builder` não é um bean neste projeto e o `ResendEmailSender` o pedia por injeção. Corrigido criando o cliente dentro do sender; o `MailConfigurationTest` deixou de fornecer o bean, então passa a reproduzir esse erro.
- CA1 comprovado em produção: o e-mail da troca de e-mail chegou à caixa de entrada.
