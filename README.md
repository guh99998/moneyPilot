# money-pilot

Aplicação de controle financeiro pessoal: contas, lançamentos, transferências, orçamento mensal, contas a pagar e a receber (com parcelamento, baixa em lote e recorrência), previsão de fluxo de caixa e relatórios. Backend em Spring Boot com API REST, e um cliente web servido pela própria aplicação.

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Validation, Actuator) |
| Banco | PostgreSQL 18 |
| Migrations | Flyway |
| Autenticação | JWT stateless de curta duração (jjwt 0.12.6) + refresh token rotativo em cookie httpOnly; senha com BCrypt |
| Documentação da API | springdoc-openapi 3.x (Swagger UI) |
| Testes | JUnit 5, MockMvc e Testcontainers (Postgres real); `node --test` para o cliente web |
| CI | GitHub Actions |
| Deploy | Docker Compose em VPS, imagem publicada no GHCR, HTTPS pelo Caddy |
| Build | Maven (wrapper incluso) |
| Frontend | HTML, CSS e JavaScript puro com ES modules, sem build step e sem biblioteca de gráficos |

## Arquitetura

**Pacotes por domínio**, não por camada técnica. Cada domínio concentra entidade, repositório, records de request/response, service e controller:

```
br.com.desenvolvedorgustavolopes.moneyPilot
├── auth          registro, verificação de conta, login, refresh token, filtro JWT, usuário autenticado, perfil e troca de senha e de e-mail
├── account       contas e saldo
├── category      categorias do usuário e do sistema
├── transaction   lançamentos e transferências
├── budget        orçamento por categoria e mês
├── bill          títulos a pagar/receber, parcelamento, baixa e recorrência
├── report        consultas agregadas, previsão de fluxo de caixa
├── notification  alertas calculados (vencimentos e orçamento) e dispensa
├── config        Spring Security, Clock, host canônico
└── exception     exceções de domínio e handler global
```

### Decisões que valem saber antes de mexer

**Base**

- **Flyway é a única fonte de verdade do schema.** `spring.jpa.hibernate.ddl-auto=validate` em todo ambiente: entidade que não casar com a migration derruba a aplicação no boot, de propósito. Migration já aplicada nunca se edita — o Flyway compara checksum.
- **Regras de estado vivem em CHECKs do Postgres**, e o Hibernate não os valida. Violação chega como `DataIntegrityViolationException`; o código checa antes para devolver um erro que explique o problema.
- **Dinheiro é `BigDecimal` com escala 2**, moeda única (BRL).
- **Saldo é calculado em leitura**, nunca persistido: `initial_balance` da conta somado às receitas menos as despesas dos lançamentos.
- **Transferência é um par de lançamentos** ligados por `transfer_group_id` — uma saída e uma entrada, sem categoria. Não é um tipo especial de lançamento. Transferência não pode ser editada, e apagar uma perna apaga o par.
- **Categorias são flat** (sem hierarquia) e podem ser globais (`user_id` nulo, criadas por migration) ou do usuário. Categoria global é visível para todos e não pode ser editada nem apagada.
- **Todo acesso é escopado ao usuário autenticado.** Recurso de outro usuário responde `404`, nunca `403`: a API não confirma a existência do que não é seu.
- **"Hoje" vem de um `Clock` injetado**, fixado em `America/Sao_Paulo`. O servidor roda em UTC; sem isso, depois das 21h um título que vence hoje apareceria como vencido. Nos testes o `Clock` é substituído por um relógio fixo.

**Títulos (contas a pagar e a receber)**

- **Título não é lançamento.** Ele é um compromisso com data; só ao ser **baixado** gera o lançamento no extrato, com o sinal invertido conforme o tipo (`PAYABLE` vira despesa, `RECEIVABLE` vira receita).
- **`OVERDUE` não existe no banco.** O status persistido é `OPEN`, `SETTLED` ou `CANCELED`; "vencido" é `OPEN` com vencimento antes de hoje, traduzido no service.
- **A baixa é à prova de duplo clique**: `UPDATE ... WHERE status = 'OPEN'` condicional, e zero linhas afetadas vira `409`. Dois cliques nunca geram dois lançamentos.
- **A baixa tem data própria** (`settledOn`, opcional, padrão hoje, nunca no futuro): o lançamento entra no dia em que o dinheiro saiu ou entrou, não no dia do registro. O "baixado no mês" do resumo segue essa data.
- **Pagamento com juros ou multa** é baixado pelo valor efetivamente pago, maior que o do título; o lançamento registra esse valor.
- **Baixar por valor menor quita o título inteiro.** Um título de R$ 100,00 baixado com R$ 95,00 fica `SETTLED`, sem resíduo. É intencional e a tela avisa.
- **Lançamento gerado por baixa não se edita nem se apaga** pelo endpoint de transações (`409`); o caminho é `unsettle`, que apaga o lançamento e reabre o título. Esses lançamentos chegam com `billId` e `billType`, e a tela os marca como pagamento ou recebimento de título.
- **Parcelamento divide com arredondamento DOWN e o resíduo em centavos na primeira parcela** (100,00 / 3 → 33,34 + 33,33 + 33,33). Vencimentos partem sempre da data original (`firstDueDate.plusMonths(i)`), então 31/01 vira 28/02 e volta a 31/03.
- **Baixa em lote é tudo ou nada** (até 200 títulos) e o erro cita o id que falhou.
- **Recorrência é materializada sob demanda e de forma idempotente**, sem scheduler: chamar duas vezes para o mesmo mês não cria nada na segunda. O dia 31 é limitado ao último dia do mês.

**Sessão**

- **Access token de 15 minutos, refresh token de 30 dias.** O refresh é opaco (256 bits), gravado como SHA-256, entregue em cookie `httpOnly` + `Secure` + `SameSite=Strict` (restrito a `/api/v1/auth`) e rotacionado a cada uso.
- **Reuso de refresh token já rotacionado revoga a família inteira** daquele usuário — sinal de token vazado.
- **O cliente faz uma única renovação compartilhada** quando várias chamadas recebem `401` ao mesmo tempo; uma renovação por chamada se autoinvalidaria pela detecção de reuso.
- **Limite de tentativas no login e no cadastro**, em memória e por janela fixa (`AttemptLimiter`). Login: 10 tentativas por IP e 3 falhas por e-mail a cada 15 min — o bloqueio por e-mail vale de qualquer IP e recusa até a senha certa; login bem-sucedido zera as falhas. Cadastro: 3 por IP por hora, contando também e-mail já cadastrado. Excedido, responde `429` com `Retry-After`. O IP vem do `X-Forwarded-For` do Caddy (`server.forward-headers-strategy=framework`). Os contadores zeram no restart (uma instância só); limites em `app.rate-limit.*`.
- **Só entra quem verificou o e-mail.** A conta nasce não verificada e recebe um link (`/#/verificar-email?token=…`, 256 bits, só o SHA-256 no banco, 24 horas, uso único, uma linha por usuário: reenviar invalida o anterior). O link verifica, mas não abre sessão. Login de conta não verificada com a senha certa responde `403`; com a senha errada, o `401` de sempre. Contas criadas antes da verificação existir entram como verificadas (`V12`).
- **Cadastro e reenvio não revelam quais e-mails existem.** O cadastro responde sempre `202` sem corpo: e-mail livre cria a conta e manda o link; e-mail de conta não verificada reaproveita a conta com o nome e a senha novos e manda outro link; e-mail de conta verificada não grava nada e avisa o dono (no máximo 1 aviso por hora). Falha no envio do link desfaz o cadastro (`502`). O reenvio responde sempre `202`; para conta já verificada, manda um e-mail dizendo que ela está ativa. Limites do reenvio: 3 por e-mail e 10 por IP por hora.
- **E-mail sem distinção de maiúsculas.** Cadastro, login, reenvio e troca gravam e buscam o e-mail sem espaços nas pontas e em minúsculas (`EmailAddress.normalize`); a `V12` converteu os existentes e um `CHECK` no banco recusa o que fugir disso.
- **O access token identifica o usuário pelo id, não pelo e-mail** (`sub` = id). Assim a troca de e-mail não deixa tokens órfãos nem os faz apontar para outra conta. Tokens antigos, com e-mail no `sub`, são recusados (`401`) e o cliente renova sozinho.
- **Troca de e-mail em duas etapas.** O pedido exige a senha atual e manda um link ao e-mail novo; até o clique, o e-mail antigo segue valendo para login e recuperação. O token (256 bits, só o SHA-256 vai ao banco) vale 24 horas, é de uso único e há no máximo um pedido por usuário: pedir de novo invalida o link anterior. O link usa `#` (`/#/confirmar-email?token=…`), então o token não chega a log de servidor ou proxy. Confirmar não exige login: grava o e-mail novo (minúsculas, sem espaços), apaga o pedido, encerra as outras sessões como na troca de senha e avisa o e-mail antigo (falha no aviso só vai para o log). Limites: senha errada divide o contador da troca de senha; no máximo 3 pedidos por hora por usuário.
- **Troca de senha encerra as outras sessões e mantém a atual.** O servidor identifica a sessão atual pelo cookie de refresh e **apaga** (não revoga) os refresh tokens ativos das demais: um token revogado que voltasse a ser usado acionaria a detecção de reuso acima e derrubaria também a sessão que trocou a senha. As outras sessões caem na próxima renovação, quando o access token de 15 minutos expira. A rota fica sob `/auth/` porque o cookie só é enviado a essa árvore.
- **Senha atual errada na troca é `400` com erro no campo, nunca `401`** (o cliente trata `401` como sessão expirada). Mais de 5 erros em 15 min por usuário respondem `429` mesmo com a senha certa; acertar zera o contador. A nova senha precisa ter 8 caracteres ou mais e ser diferente da atual.
- **Perfil:** nome obrigatório, sem espaços nas pontas, até 255 caracteres. O e-mail é só leitura (a troca é a spec 008).
- **Alertas são calculados, não armazenados.** `GET /alerts` recalcula a situação atual do usuário a cada chamada; só a dispensa fica gravada (`dismissed_alerts`). Seis tipos: títulos a pagar e a receber a vencer (de hoje até hoje + 7 dias; o que vence hoje ainda é "a vencer"), títulos vencidos (antes de hoje), orçamento em 80% (de 80,00% até 100,00% do orçado) e orçamento estourado (acima de 100,00%). Títulos entram num alerta por tipo (quantidade, total e, nos a vencer, o maior vencimento); orçamento gera um por categoria no mês corrente, com o mesmo gasto da tela Orçamentos (despesas sem transferências). "Hoje" vem do `Clock`.
- **Dispensar vale para a conta, em todos os aparelhos.** Cada alerta tem uma chave (`PAYABLE_OVERDUE`, `BUDGET_WARNING:<categoria>:<aaaa-mm>`...) e uma impressão digital do conteúdo (`quantidade|total` nos títulos). O alerta dispensado volta se a impressão mudar; o de orçamento só volta ao mudar de faixa ou de mês, porque a faixa e o mês fazem parte da chave. Quando o alerta deixa de existir, a dispensa é esquecida na listagem seguinte.
- **O sino se atualiza no cliente** a cada troca de tela, ao voltar para a aba e após qualquer requisição de escrita bem-sucedida (evento `api:changed`), sem polling.

### Modelo

`users` · `accounts` · `categories` · `transactions` · `budgets` · `bill_recurrences` · `bills` · `refresh_tokens` · `dismissed_alerts` · `email_change_tokens` · `email_verification_tokens` — migrations `V1` a `V12` em `src/main/resources/db/migration`. `V6` adiciona os índices das tabelas originais; `V12` marca as contas existentes como verificadas e converte os e-mails para minúsculas.

## API

Base: `/api/v1`. Tudo exige `Authorization: Bearer <token>`, exceto registro, login, refresh, logout, verificação de conta e a confirmação de troca de e-mail. A documentação interativa fica em `/swagger-ui.html` (spec em `/v3/api-docs`).

| Método | Rota | O que faz |
|---|---|---|
| `POST` | `/auth/register` | Cadastra e envia o link de verificação; resposta igual exista ou não a conta (`202` sem corpo; `502` se o link não puder ser enviado; `429` acima de 3 por IP/hora) |
| `POST` | `/auth/login` | Devolve o access token e grava o refresh token em cookie (`403` conta não verificada; `429` após muitas tentativas) |
| `POST` | `/auth/verification/confirm` | Verifica a conta com o `token` do link (`204`, também para link já usado; `400` inválido ou expirado) |
| `POST` | `/auth/verification/resend` | Reenvia o link para o `email` (sempre `202`; `429` acima de 3 por e-mail ou 10 por IP por hora) |
| `POST` | `/auth/refresh` | Rotaciona o refresh token e devolve um novo access token |
| `POST` | `/auth/logout` | Revoga o refresh token e limpa o cookie |
| `POST` | `/auth/password` | Troca a senha (`currentPassword`, `newPassword`) e encerra as outras sessões (`204`; `400` com erro no campo; `429` após 5 erros) |
| `POST` | `/profile/email` | Pede a troca de e-mail (`newEmail`, `currentPassword`): envia o link ao e-mail novo (`204`; `400` com erro no campo; `429`; `502` se o envio falhar) |
| `POST` | `/auth/email/confirm` | Confirma a troca com o `token` do link, sem exigir login (`204`; `400` link inválido, expirado ou e-mail em uso) |
| `GET` `PUT` | `/profile` | Nome, e-mail, data de cadastro e `pendingEmail` (troca aguardando confirmação) do usuário autenticado; altera o nome |
| `GET` | `/alerts` | Alertas ativos do usuário (`count` e `alerts[]`); os dispensados ficam de fora |
| `POST` | `/alerts/dismiss` | Dispensa um alerta pela chave (`204`; `404` se não há alerta ativo com ela) |
| `GET` `POST` | `/accounts` | Lista e cria contas |
| `GET` `PUT` `DELETE` | `/accounts/{id}` | Detalha, altera e remove |
| `GET` | `/accounts/{id}/balance` | Saldo inicial e saldo atual |
| `GET` `POST` | `/categories` | Lista e cria categorias |
| `GET` `PUT` `DELETE` | `/categories/{id}` | Detalha, altera e remove |
| `GET` `POST` | `/transactions` | Lista (com filtros e paginação) e cria |
| `GET` `PUT` `DELETE` | `/transactions/{id}` | Detalha, altera e remove |
| `POST` | `/transactions/transfers` | Cria a transferência entre duas contas |
| `GET` `POST` | `/budgets` | Lista e cria orçamentos |
| `GET` `PUT` `DELETE` | `/budgets/{id}` | Detalha, altera e remove |
| `GET` `POST` | `/bills` | Lista (com filtros e paginação) e cria títulos |
| `GET` `PUT` `DELETE` | `/bills/{id}` | Detalha, altera e remove |
| `POST` | `/bills/{id}/settle` | Baixa o título e gera o lançamento na data do pagamento (`settledOn`) |
| `POST` | `/bills/{id}/unsettle` | Desfaz a baixa e apaga o lançamento gerado |
| `POST` | `/bills/{id}/cancel` | Cancela o título |
| `POST` | `/bills/settle` | Baixa em lote, tudo ou nada |
| `POST` | `/bills/installments` | Cria um parcelamento |
| `DELETE` | `/bills/installments/{groupId}` | Remove o parcelamento inteiro (`409` se alguma parcela já foi baixada) |
| `POST` | `/bills/materialize?month=AAAA-MM` | Gera os títulos do mês a partir das recorrências ativas |
| `GET` `POST` | `/bill-recurrences` | Lista e cria recorrências |
| `GET` `PUT` `DELETE` | `/bill-recurrences/{id}` | Detalha, altera e remove |
| `POST` | `/bill-recurrences/{id}/deactivate` | Desativa a recorrência |
| `GET` | `/reports/monthly-summary` | Receita, despesa e saldo do mês |
| `GET` | `/reports/spending-by-category` | Gasto por categoria no mês |
| `GET` | `/reports/budget-vs-actual` | Orçado contra realizado |
| `GET` | `/reports/cash-flow-forecast?days=30` | Saldo projetado em blocos de 7 dias (`days` ∈ 30, 60, 90) |
| `GET` | `/reports/monthly-forecast?months=6` | Saldo projetado mês a mês (`months` de 1 a 12), incluindo recorrências ainda não materializadas |
| `GET` | `/reports/bills-summary` | A pagar, a receber, vencidos e baixados no mês |
| `GET` | `/actuator/health` | Health check (público) |

Listagens aceitam `page`, `size` e `sort` (por exemplo `sort=date,asc`).

- `/transactions` filtra por `accountId`, `categoryId`, `type`, `from` e `to`.
- `/bills` filtra por `type`, `status` (incluindo `OVERDUE`), `categoryId`, `accountId`, `dueDateFrom` e `dueDateTo`.

A previsão de fluxo de caixa parte do mesmo saldo que `/accounts/{id}/balance` mostra, soma os títulos em aberto bloco a bloco e joga os vencidos inteiros no primeiro bloco — dívida vencida não some da projeção por ser antiga. O último bloco pode ter menos de 7 dias (30 dias = quatro de 7 e um de 2).

A previsão mensal segue a mesma base, agrupada por mês a partir do mês corrente, e vai além: **projeta as recorrências ativas que ainda não viraram título**, sem persistir nada. Uma ocorrência só entra se vence a partir de hoje, está dentro da vigência da recorrência e ainda não foi materializada naquele vencimento — assim um título já gerado nunca conta duas vezes. Os vencidos também caem inteiros no primeiro mês e vêm somados à parte em `overdue`.

Erro sempre no mesmo formato, com `fieldErrors` preenchido quando a falha é de validação:

```json
{
  "timestamp": "2026-09-22T14:31:10.577Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "fieldErrors": { "amount": "must be greater than 0" }
}
```

## Cliente web

Servido em `/` pela própria aplicação, a partir de `src/main/resources/static`. Sem framework e sem etapa de build: cada tela é um módulo em `js/views/` que devolve um elemento e se re-renderiza no `load()`.

- **Mobile-first**, com breakpoints em 600, 900, 1200 e 1440px. No celular a navegação vira uma barra no topo, tabelas viram cartões compactos e os filtros ficam recolhidos.
- **Tema claro único** nas cores da marca (azul-marinho e verde da logo).
- **Previsão mensal** com colunas de a pagar e a receber por mês, linha do saldo projetado e tabela mês a mês.
- **Visão geral** com saldo em contas, projeção, títulos a pagar e a receber, próximos vencimentos e gráficos de previsão e de receitas × despesas — desenhados em SVG por `js/charts.js`, com tooltip e versão em tabela.
- **A pagar e receber** com seleção em lote e prévia ao vivo da divisão das parcelas, calculada em centavos com a mesma regra do backend.
- **Ordenação por coluna** em Lançamentos, A pagar e receber e Contas: clicar no cabeçalho ordena, clicar de novo inverte.

## Desenvolvimento

Novas features seguem Spec-Driven Development: cada uma tem uma pasta em [`specs/`](specs/README.md) com spec, plano técnico e tarefas, aprovados antes do código. As regras de trabalho, branches e commits estão no [`CLAUDE.md`](CLAUDE.md).

## Rodando localmente

Pré-requisitos: JDK 21 e Docker.

```bash
docker compose up -d      # sobe o Postgres em localhost:5432
./mvnw spring-boot:run    # sobe a aplicação em localhost:8080
```

O profile `local` é o default e já aponta para o banco do compose. O Flyway cria o schema e semeia as categorias padrão no primeiro boot. A interface web fica em `http://localhost:8080` e o Swagger em `http://localhost:8080/swagger-ui.html`.

O segredo JWT do profile `local` é um placeholder de desenvolvimento — ele não vale para nada além da sua máquina. Localmente o cookie do refresh token sai sem `Secure` (`COOKIE_SECURE=false`), porque o servidor roda em HTTP.

## Variáveis de ambiente (profile `prod`)

| Variável | Observação |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `JDBC_DATABASE_URL` | Obrigatória. No `docker-compose.prod.yml` aponta para o serviço `db` |
| `JDBC_DATABASE_USERNAME` | Obrigatória |
| `JDBC_DATABASE_PASSWORD` | Obrigatória. No compose vem de `POSTGRES_PASSWORD` do `.env` |
| `JWT_SECRET` | Obrigatória. Mínimo de 32 caracteres; segredo vazio derruba a aplicação no boot |
| `JWT_EXPIRATION_MS` | Opcional, default 900000 (15 minutos) |
| `COOKIE_SECURE` | Opcional, default `true` |
| `CANONICAL_HOST` | Opcional. Quando definida, requisições para outro host recebem `308` para `https://<CANONICAL_HOST>` |
| `PUBLIC_URL` | Obrigatória. URL pública do app com `https://` e sem barra final; base dos links enviados por e-mail |
| `MAIL_FROM_ADDRESS` | Obrigatória. Remetente dos e-mails, de um domínio verificado no Resend (SPF/DKIM) |
| `RESEND_API_KEY` | Obrigatória. Chave da API do Resend |
| `PORT` | Opcional, default 8080 |

**E-mail transacional:** o pacote `mail` expõe `EmailSender`, e as features só pedem "envie esta mensagem para esta pessoa". Em `prod` o envio é **síncrono** via Resend (API HTTP com `RestClient`, timeouts de 3 s e 10 s, sem dependência nova); falha vira `502` no formato `ErrorResponse`, sem expor a chave no log. Fora de `prod` nada é enviado e o conteúdo aparece no log. Sem `PUBLIC_URL`, `MAIL_FROM_ADDRESS` ou `RESEND_API_KEY` em `prod`, o app não sobe.

As credenciais do banco e o `JWT_SECRET` não têm valor default no código: variável faltando é falha de boot, não comportamento silencioso. Em produção, `server.forward-headers-strategy=framework` faz o Spring enxergar o HTTPS terminado no Caddy — sem isso o cookie `Secure` não é enviado.

## Deploy

Hospedado em uma VPS com Docker Compose (`docker-compose.prod.yml`): Postgres 18 com volume nomeado e a aplicação, com limite de memória e heap da JVM fixados porque a VPS é compartilhada com outros apps. O HTTPS fica num Caddy compartilhado da VPS, que alcança a aplicação pela rede Docker externa `proxy`.

O `Dockerfile` é multi-stage: compila com o Maven wrapper e roda só com o JRE 21, com usuário sem privilégio. O jar sai com nome fixo (`target/moneyPilot.jar`), então trocar a `<version>` do `pom.xml` não quebra o boot.

**Publicação:** todo push na `main` que passa nos testes publica a imagem `ghcr.io/guh99998/moneypilot` com as tags `latest` e o SHA do commit. A VPS mantém um clone do repositório na `main`, só para ter o compose e o `.env`, e baixa a imagem pronta; não compila nada.

Primeira instalação:

```bash
docker network create proxy                           # uma vez por VPS, compartilhada com o Caddy
cp .env.production.example .env                       # preencher POSTGRES_PASSWORD, JWT_SECRET, CANONICAL_HOST, PUBLIC_URL, MAIL_FROM_ADDRESS e RESEND_API_KEY
docker compose -f docker-compose.prod.yml up -d
```

A cada versão nova:

```bash
git pull                                                # traz mudanças no compose, se houver
docker compose -f docker-compose.prod.yml exec -T db pg_dump -U moneypilot moneypilot > backup-$(date +%F).sql   # antes, se houver migration nova
docker compose -f docker-compose.prod.yml pull app
docker compose -f docker-compose.prod.yml up -d app
docker compose -f docker-compose.prod.yml logs -f app   # conferir o Flyway aplicando as migrations
```

Para voltar uma versão, use a tag com o SHA do commit anterior no lugar de `latest`. Migration já aplicada não é desfeita pela troca de imagem — por isso o dump antes.

**Backup:** um cron diário na VPS faz o dump do Postgres e o envia, criptografado, para um armazenamento externo. O dump manual acima é a garantia extra para o momento exato antes de uma migration.

## Testes

```bash
./mvnw verify                          # testes Java (precisa do Docker rodando)
node --test "src/test/js/*.test.mjs"   # testes do cliente web
```

Os testes de integração estendem `AbstractIntegrationTest`, que sobe um Postgres 18 via Testcontainers. O Flyway aplica as migrations reais e o Hibernate só valida — o mesmo contrato de produção, sem schema gerado. A suíte cobre autenticação, verificação de conta e rotação de refresh token, isolamento entre usuários, saldos e transferências, filtros e paginação, ciclo de vida dos títulos e baixa concorrente, arredondamento de parcelas, baixa em lote, recorrência, previsões de fluxo de caixa semanal e mensal e os relatórios.

O GitHub Actions (`.github/workflows/ci.yml`) roda as duas suítes a cada push e pull request.

## Próximos passos

- E-mail transacional, reset de senha e login com Google por ID token verificado no backend.
- Ordenar títulos e lançamentos pelo nome da categoria e da conta (hoje a ordenação cobre data, descrição, valor e tipo).
- Fora do horizonte imediato, de propósito: anexo de comprovante (exige S3), fatura de cartão de crédito, importação de extrato CSV, Redis e RabbitMQ.
