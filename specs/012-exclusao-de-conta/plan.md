# 012 — Plano técnico

**Spec:** [spec.md](spec.md)
**Status:** aprovado

## Abordagem

Uma rota nova no pacote `auth` apaga a conta numa única transação. A confirmação reaproveita o que já existe: a senha segue o modelo de `ProfileService.changePassword` (mesmo `AttemptLimiter`, 5 em 15 minutos) e o Google usa o `GoogleTokenVerifier` da 004. As remoções são feitas por queries em lote (`@Modifying`) nos repositórios de cada domínio, sem carregar entidades. Não há `ON DELETE CASCADE` no schema, então a ordem é filho antes de pai.

## Banco de dados

- **Nenhuma migration.**
- **Impacto em dados existentes:** nenhum no schema. A rota apaga dados da própria conta, por ação explícita da pessoa.

## API

| Método | Rota | Mudança |
|---|---|---|
| POST | `/api/v1/profile/deletion` | Nova. Corpo `DeleteAccountRequest(currentPassword?, credential?)`. Resposta `204`. |

- POST com corpo, pelo mesmo motivo do `/profile/google/unlink`.
- Exatamente um campo deve vir, conforme o tipo da conta: conta com senha usa `currentPassword`; conta só com Google usa `credential`. Caso contrário, `400` com erro no campo.
- Erros: `400` (campo ausente ou duplicado, senha errada, Google de outro `subject` em `credential`), `401` (sem sessão), `429` (limite de tentativas).
- OpenAPI completo: `@Operation`, `@ApiResponse` 204/400/401/429 com `ErrorResponse` e `@Schema(description, example)` nos campos.
- Sem campo de dinheiro: `MoneyScaleIntegrationTest` não muda.

Mudanças incompatíveis com o cliente atual: nenhuma.

### Serviço

`AccountDeletionService`:
1. Senha: confere com `AttemptLimiter` na chave `password-change:{id}` e lança `InvalidCurrentPasswordException`.
2. Google: valida com `GoogleTokenVerifier` e compara o `subject`; se não bater, `GoogleAccountMismatchException` (`400` no campo `credential`, tratada no `GlobalExceptionHandler`).
3. Transação, nesta ordem: bills → bill_recurrences → transactions (pelas contas do usuário) → budgets → dismissed_alerts → support_tickets → categorias do usuário → accounts → refresh_tokens, email_verification_tokens, password_reset_tokens, email_change_tokens → users.
4. Depois do commit, aviso por `EmailSender`. Falha no envio só vai para o log.

Sessões: o cookie de refresh é limpo com `RefreshCookies`; os access tokens caem em até 15 minutos porque `AuthenticatedUserProvider.getCurrentUserId` faz `existsById` e responde `401`; as outras sessões caem porque os refresh tokens foram apagados.

## Cliente web

`static/js/views/profile.js`: cartão "Excluir conta" no fim do perfil.
- Texto (CA7): a conta e os dados serão excluídos **de imediato** e não há como desfazer; os chamados de suporte serão **encerrados**; "Sentimos muito por ver você ir 😢" e um agradecimento por ter usado a plataforma (tristes, mas felizes por isso); a despedida fica só na tela, não no e-mail; "As portas continuam abertas: você pode voltar quando quiser, com um novo cadastro".
- Pede a senha, ou mostra o `googleButton` (`js/google.js`) para conta só com Google, mais o campo "EXCLUIR", que habilita o botão.
- Depois de excluir: limpa o `localStorage` da sessão (mesmas chaves do logout) e vai ao login com "Sua conta foi excluída".
- `api.profile.deleteAccount` no cliente da API.
- Atualizar `ajuda/perfil-e-seguranca.html`.
- Mobile-first, seguindo os breakpoints e o tema existentes.

## Testes

Integração (`AbstractIntegrationTest`, MockMvc), com `EmailSender` falso:

| Critério | Prova |
|---|---|
| CA1 | conta com dados em todas as tabelas; nada sobra por `user_id` |
| CA2 | senha errada não apaga e dá erro no campo; a 6ª tentativa dá `429` |
| CA3 | Google com o `subject` certo exclui; com outro, `400` |
| CA4 | login com o e-mail antigo dá `401`; o recadastro funciona |
| CA5 | refresh de outra sessão recusado; access token antigo dá `401` |
| CA6 | e-mail enviado; se o envio falha, a exclusão vale |
| CA7 | teste JS (`node --test`) do texto e do botão só habilitado com "EXCLUIR" |
| Isolamento | dados de outro usuário ficam intactos |

Também: `OpenApiContractIntegrationTest` e testes JS do cliente da API.

## Riscos e alternativas descartadas

- Esquecer uma tabela com FK para `users` → o teste do CA1 confere todas as tabelas com `user_id` e falha se alguma sobrar.
- Conta com muitos dados → queries em lote numa transação só.
- Alternativa descartada: `ON DELETE CASCADE` por migration, pois mexe no schema de produção e não é necessário.
- Alternativa descartada: prazo de arrependimento (decidido: exclusão na hora).

## Deploy

Sem migration. Convém um dump antes, por precaução. Rollback: reverter a imagem anterior. Os dados já excluídos não voltam, só pelo backup. Merge na `main` só com ok do Gustavo.

## Validação

Antes de dar por pronta: `./mvnw verify`, `node --test "src/test/js/*.test.mjs"` e as skills `api-design-principles`, `web-design-guidelines`, `accessibility-compliance` e `responsive-design`, anotadas em `tasks.md`.
