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
| Build | Maven (wrapper incluso) |
| Frontend | HTML, CSS e JavaScript puro com ES modules, sem build step e sem biblioteca de gráficos |

## Arquitetura

**Pacotes por domínio**, não por camada técnica. Cada domínio concentra entidade, repositório, records de request/response, service e controller:

```
br.com.desenvolvedorgustavolopes.moneyPilot
├── auth          registro, login, refresh token, filtro JWT, usuário autenticado
├── account       contas e saldo
├── category      categorias do usuário e do sistema
├── transaction   lançamentos e transferências
├── budget        orçamento por categoria e mês
├── bill          títulos a pagar/receber, parcelamento, baixa e recorrência
├── report        consultas agregadas, previsão de fluxo de caixa
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

### Modelo

`users` · `accounts` · `categories` · `transactions` · `budgets` · `bill_recurrences` · `bills` · `refresh_tokens` — migrations `V1` a `V9` em `src/main/resources/db/migration`. `V6` adiciona os índices das tabelas originais.

## API

Base: `/api/v1`. Tudo exige `Authorization: Bearer <token>`, exceto registro, login, refresh e logout. A documentação interativa fica em `/swagger-ui.html` (spec em `/v3/api-docs`).

| Método | Rota | O que faz |
|---|---|---|
| `POST` | `/auth/register` | Cria usuário |
| `POST` | `/auth/login` | Devolve o access token e grava o refresh token em cookie |
| `POST` | `/auth/refresh` | Rotaciona o refresh token e devolve um novo access token |
| `POST` | `/auth/logout` | Revoga o refresh token e limpa o cookie |
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
| `GET` | `/reports/bills-summary` | A pagar, a receber, vencidos e baixados no mês |
| `GET` | `/actuator/health` | Health check (público) |

Listagens aceitam `page`, `size` e `sort` (por exemplo `sort=date,asc`).

- `/transactions` filtra por `accountId`, `categoryId`, `type`, `from` e `to`.
- `/bills` filtra por `type`, `status` (incluindo `OVERDUE`), `categoryId`, `accountId`, `dueDateFrom` e `dueDateTo`.

A previsão de fluxo de caixa parte do mesmo saldo que `/accounts/{id}/balance` mostra, soma os títulos em aberto bloco a bloco e joga os vencidos inteiros no primeiro bloco — dívida vencida não some da projeção por ser antiga. O último bloco pode ter menos de 7 dias (30 dias = quatro de 7 e um de 2).

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
- **Visão geral** com saldo em contas, projeção, títulos a pagar e a receber, próximos vencimentos e gráficos de previsão e de receitas × despesas — desenhados em SVG por `js/charts.js`, com tooltip e versão em tabela.
- **A pagar e receber** com seleção em lote e prévia ao vivo da divisão das parcelas, calculada em centavos com a mesma regra do backend.
- **Ordenação por coluna** em Lançamentos, A pagar e receber e Contas: clicar no cabeçalho ordena, clicar de novo inverte.

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
| `JDBC_DATABASE_URL` | Derivada automaticamente pelo buildpack Java quando há Postgres provisionado |
| `JDBC_DATABASE_USERNAME` | idem |
| `JDBC_DATABASE_PASSWORD` | idem |
| `JWT_SECRET` | Obrigatória. Mínimo de 32 caracteres; segredo vazio derruba a aplicação no boot |
| `JWT_EXPIRATION_MS` | Opcional, default 900000 (15 minutos) |
| `COOKIE_SECURE` | Opcional, default `true` |
| `CANONICAL_HOST` | Opcional. Quando definida, requisições para outro host recebem `308` para `https://<CANONICAL_HOST>` |
| `PORT` | Fornecida pela plataforma |

As credenciais do banco e o `JWT_SECRET` não têm valor default no código: variável faltando é falha de boot, não comportamento silencioso. Em produção, `server.forward-headers-strategy=framework` faz o Spring enxergar o HTTPS do router do Heroku — sem isso o cookie `Secure` não é enviado.

## Deploy

Hospedado no Heroku. `Procfile` e `system.properties` definem o processo web e o runtime Java 21; o jar sai com nome fixo (`target/moneyPilot.jar`), então trocar a `<version>` do `pom.xml` não quebra o boot.

Toda vez que houver migration nova:

```bash
heroku pg:backups:capture   # antes do push: o plano essential-0 não tem rollback nem PITR
git push heroku main
heroku logs --tail          # conferir o Flyway aplicando as migrations
heroku releases             # ter o número de rollback à mão
```

O build do buildpack roda com `-DskipTests`, porque não há Docker disponível lá. Quem garante os testes é o CI.

## Testes

```bash
./mvnw verify                          # testes Java (precisa do Docker rodando)
node --test "src/test/js/*.test.mjs"   # testes do cliente web
```

Os testes de integração estendem `AbstractIntegrationTest`, que sobe um Postgres 18 via Testcontainers. O Flyway aplica as migrations reais e o Hibernate só valida — o mesmo contrato de produção, sem schema gerado. A suíte cobre autenticação e rotação de refresh token, isolamento entre usuários, saldos e transferências, filtros e paginação, ciclo de vida dos títulos e baixa concorrente, arredondamento de parcelas, baixa em lote, recorrência, previsão de fluxo de caixa e os relatórios.

O GitHub Actions (`.github/workflows/ci.yml`) roda as duas suítes a cada push e pull request.

## Próximos passos

- E-mail transacional, verificação de conta, reset de senha e login com Google por ID token verificado no backend.
- Rate limiting em `/auth/login`.
- Ordenar títulos e lançamentos pelo nome da categoria e da conta (hoje a ordenação cobre data, descrição, valor e tipo).
- Fora do horizonte imediato, de propósito: anexo de comprovante (exige S3), fatura de cartão de crédito, importação de extrato CSV, Redis e RabbitMQ.
