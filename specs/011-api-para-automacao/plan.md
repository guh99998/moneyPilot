# 011 — Plano técnico

**Spec:** [spec.md](spec.md)
**Status:** implementado

## Abordagem

São três frentes independentes, que viram commits separados.

**A. Sessão para clientes fora do navegador** (domínio `auth/`)

- `LoginRequest` ganha `Boolean refreshTokenInBody`, opcional. Ausente ou `false` mantém o comportamento de hoje.
- Record novo `RefreshRequest(String refreshToken)`. `/auth/refresh` e `/auth/logout` passam a receber `@RequestBody(required = false) RefreshRequest`, além do `@CookieValue` que já existe. Com `refreshToken` não nulo no corpo, vale o corpo: o cookie é ignorado e nenhum `Set-Cookie` é enviado. Sem corpo (o `api.js` manda `POST` vazio), tudo continua como hoje.
- `AuthResponse` passa a `(token, refreshToken, expiresIn)` com `@JsonInclude(NON_NULL)`. O navegador continua recebendo só `{token}`, e o teste existente que garante que a palavra "refresh" não aparece no corpo continua valendo (CA4).
- `expiresIn` vem de um getter novo em `JwtService`, que lê `jwt.expiration-ms` e converte para segundos.
- Rotação e reuso usam o mesmo `RefreshTokenService.rotate`. Um token revogado que volta a ser usado já revoga todos os tokens ativos do usuário, então o código de rotação não muda.
- Conta só com Google: o `AuthService.login` já responde `401` igual ao de senha errada. Nada muda.
- O login com Google e a troca de senha não mudam.

**B. Contrato OpenAPI** (CA1)

- Os `@Operation`/`@Schema` vêm do `swagger-annotations`, que já chega transitivamente pelo springdoc 3.1.0, então não entra dependência nova. Conferir com `./mvnw dependency:tree` antes de começar.
- Classe nova `config/OpenApiConfig.java`, com:
  - `Info` em português (título, versão e descrição que aponta para `#/usando-com-ia`);
  - esquema `bearerAuth` (HTTP bearer, JWT) aplicado a todas as rotas;
  - rotas públicas marcadas com `@SecurityRequirements()` vazio.
- Em cada controller: `@Tag` por domínio, e em cada método:
  - `@Operation(summary, description)`;
  - `@ApiResponse` do sucesso e de cada erro possível, com o schema `ErrorResponse`;
  - o `401` sem corpo das rotas protegidas, que vem do entry point.
- Nos records de request e response: `@Schema(description, example)` campo a campo. Os exemplos das requisições e respostas saem desses campos.
  - Dinheiro: número com 2 casas e ponto decimal, exemplo `50.00`.
  - Datas `LocalDate`: exemplo `2026-10-28`.
  - `month=YYYY-MM` do materialize: `@Parameter(example)`.
- Enums com `@Schema(enumAsRef = true, description)`, para aparecerem como componentes de valores fechados: `AccountType`, `CategoryType`, `TransactionType`, `TransactionFilterType`, `BillType`, `BillStatus`, `BillStatusFilter`, `AlertKind`, `SupportCategory`.

**C. Dinheiro com 2 casas** (CA7)

- `@Digits(integer = 17, fraction = 2)` em todo campo de dinheiro dos requests, como já existe em `InstallmentPlanRequest.totalAmount`, com a mesma mensagem: transação, transferência, conta a pagar ou receber, recorrência, quitação, orçamento e saldo inicial da conta.
- No `GlobalExceptionHandler`, o tratamento de corpo ilegível passa a olhar a causa. Erro de formato do Jackson com caminho de campo (ex.: `"10,50"` em `amount`) vira `400` com `fieldErrors` naquele campo. Os outros casos continuam com a mensagem genérica de hoje.

## Banco de dados

Nenhuma migration.

**Impacto em dados existentes:** nenhum.

## API

| Método | Rota | Mudança |
|---|---|---|
| POST | `/auth/login` | Campo opcional `refreshTokenInBody`. Com `true`, responde `{token, refreshToken, expiresIn}` e não envia `Set-Cookie`. |
| POST | `/auth/refresh` | Corpo opcional `{refreshToken}`. Se vier, vale o corpo e a resposta é `{token, refreshToken, expiresIn}`, sem cookie. |
| POST | `/auth/logout` | Corpo opcional `{refreshToken}`. Se vier, revoga esse token e responde `204` sem `Set-Cookie`. |
| todas com dinheiro | | Mais de 2 casas ou vírgula: `400` com erro no campo. |
| todas | | Descrições, exemplos, erros e enums no `/v3/api-docs`. |

Mudanças incompatíveis com o cliente atual: nenhuma para o navegador, que já manda centavos com 2 casas. Um cliente que mandava 3 casas agora recebe `400` em vez de ter o valor arredondado sem aviso.

## Cliente web

- `js/views/ai-guide.js` (novo), com `renderAiGuide()`, contém:
  - o passo a passo: entrar com `refreshTokenInBody`, renovar diante de um `401` ou perto de `expiresIn`, listar contas e categorias, lançar uma despesa e sair;
  - um aviso para guardar o refresh token como se fosse a senha, porque ele vale 30 dias e o logout o revoga;
  - a orientação para contas só com Google, que precisam usar o "Criar senha" do perfil;
  - o aviso de que a troca de senha encerra as sessões da API;
  - os links para `/swagger-ui.html` e `/v3/api-docs`;
  - o texto pronto para o assistente, com botão "Copiar" (`navigator.clipboard`; se falhar, seleciona o texto).
  - As URLs dos exemplos usam `window.location.origin`.
- `js/app.js`: rota pública `#/usando-com-ia`, no mesmo padrão do `SUPPORT_ROUTE` (standalone sem sessão, dentro do layout com sessão), e um item no rodapé do menu ao lado de "Falar com o suporte".
- `js/views/auth.js`: link "Usar com IA" no login, perto do link de suporte.

## Testes

- **`ApiSessionIntegrationTest`** (`auth/`):
  - CA2: login com `refreshTokenInBody` devolve os três campos e não envia `Set-Cookie`;
  - CA3: renovação pelo corpo rotaciona, e o token antigo responde `401`;
  - borda: reuso pelo corpo derruba todas as sessões do usuário;
  - borda: com cookie e corpo, vale o corpo;
  - CA6: logout pelo corpo revoga;
  - borda: conta só com Google responde `401` no modo corpo;
  - CA4: refresh sem corpo e sem content-type, como o `api.js`, continua funcionando.
- **`RefreshTokenIntegrationTest`**: continua sem nenhuma alteração. É a prova do CA4.
- **`OpenApiContractIntegrationTest`** (CA1) lê o `/v3/api-docs` e verifica que:
  - toda operação tem `summary`, `description` e ao menos uma resposta de erro;
  - toda propriedade dos schemas tem `example` ou é enum/`$ref`;
  - os enums aparecem como componentes com os valores fechados;
  - o `bearerAuth` existe e as rotas públicas têm `security: []`.
  - Como o teste falha quando uma rota nova vem sem documentação, ele também evita que o contrato desatualize.
- **`MoneyScaleIntegrationTest`** (CA7), parametrizado por rota e campo: `10.555` e `"10,50"` respondem `400` com `fieldErrors` no campo; `10.50` é aceito.
- **JS:**
  - `ai-guide-view.test.mjs` (CA5): a tela desenha sem sessão, mostra os passos e o texto do assistente, e o botão copia;
  - `auth-view.test.mjs`: link no login.

## Riscos e alternativas descartadas

- Refresh token de 30 dias nas mãos do cliente → o modo é opt-in, a página orienta a guardar o token como senha, o logout revoga e o reuso derruba todas as sessões.
- Volume de anotações (cerca de 70 operações), que deixa os controllers mais verbosos → aceito porque o contrato é o produto desta spec, e o teste de contrato impede regressões.
- `@Digits` recusa o que antes era arredondado sem aviso → o navegador não é afetado, e um cliente externo passa a receber um erro claro.
- Descartado: tokens de API pessoais (decisão do Gustavo).
- Descartado: CORS (decisão do Gustavo).
- Descartado: um `OperationCustomizer` que gerasse os erros automaticamente. Ele esconde quais erros cada rota tem, e anotar explicitamente fica mais claro.
- Descartado: entregar o refresh no corpo e também no cookie. Isso duplicaria o token sem benefício.

## Deploy

Sem migration e sem variável de ambiente nova. O rollback é voltar para a imagem anterior. Antes de liberar, conferir em produção que `/v3/api-docs` e `#/usando-com-ia` abrem sem login.

## Verificação (ao implementar)

- Rodar `./mvnw verify` e `node --test "src/test/js/*.test.mjs"`.
- Checagem manual com o app local:
  - via `curl`: login no modo corpo, renovação, lançamento e logout;
  - no navegador: entrar e renovar, para confirmar que só o cookie é usado;
  - abrir o Swagger e conferir exemplos e enums.
- Validar com as skills:
  - `api-design-principles` para as rotas de auth alteradas e o contrato;
  - `web-design-guidelines`, `accessibility-compliance` e `responsive-design` para a tela `#/usando-com-ia`.
  - Anotar a revisão em `tasks.md`.
- Atualizar o README: tabela da API (login, refresh e logout), decisão de domínio do refresh no corpo, regra das 2 casas e link para a página.
