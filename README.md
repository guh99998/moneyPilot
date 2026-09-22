# money-pilot

Aplicação de controle financeiro pessoal: contas, lançamentos, transferências, orçamento mensal e relatórios. Backend em Spring Boot com API REST, e um cliente web servido pela própria aplicação.

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Validation, Actuator) |
| Banco | PostgreSQL |
| Migrations | Flyway |
| Autenticação | JWT stateless (jjwt 0.12.6), senha com BCrypt |
| Build | Maven (wrapper incluso) |
| Frontend | HTML, CSS e JavaScript puro com ES modules, sem build step |

## Arquitetura

**Pacotes por domínio**, não por camada técnica. Cada domínio concentra entidade, repositório, records de request/response, service e controller:

```
br.com.desenvolvedorgustavolopes.moneyPilot
├── auth          registro, login, filtro JWT, usuário autenticado
├── account       contas e saldo
├── category      categorias do usuário e do sistema
├── transaction   lançamentos e transferências
├── budget        orçamento por categoria e mês
├── report        consultas agregadas
├── config        Spring Security
└── exception     exceções de domínio e handler global
```

### Decisões que valem saber antes de mexer

- **Flyway é a única fonte de verdade do schema.** `spring.jpa.hibernate.ddl-auto=validate` em todo ambiente: entidade que não casar com a migration derruba a aplicação no boot, de propósito. Migration já aplicada nunca se edita — o Flyway compara checksum.
- **Dinheiro é `BigDecimal` com escala 2**, moeda única (BRL).
- **Saldo é calculado em leitura**, nunca persistido: `initial_balance` da conta somado às receitas menos as despesas dos lançamentos.
- **Transferência é um par de lançamentos** ligados por `transfer_group_id` — uma saída e uma entrada, sem categoria. Não é um tipo especial de lançamento. Transferência não pode ser editada, e apagar uma perna apaga o par.
- **Categorias são flat** (sem hierarquia) e podem ser globais (`user_id` nulo, criadas por migration) ou do usuário. Categoria global é visível para todos e não pode ser editada nem apagada.
- **Todo acesso é escopado ao usuário autenticado.** Recurso de outro usuário responde `404`, nunca `403`: a API não confirma a existência do que não é seu.
- **Autenticação é stateless.** Nada de sessão — o cliente é separado da API e o token viaja no header.

### Modelo

`users` · `accounts` · `categories` · `transactions` · `budgets` — migrations `V1` a `V5` em `src/main/resources/db/migration`.

## API

Base: `/api/v1`. Tudo exige `Authorization: Bearer <token>`, exceto registro e login.

| Método | Rota | O que faz |
|---|---|---|
| `POST` | `/auth/register` | Cria usuário |
| `POST` | `/auth/login` | Devolve o token |
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
| `GET` | `/reports/monthly-summary` | Receita, despesa e saldo do mês |
| `GET` | `/reports/spending-by-category` | Gasto por categoria no mês |
| `GET` | `/reports/budget-vs-actual` | Orçado contra realizado |
| `GET` | `/actuator/health` | Health check (público) |

Listagens aceitam `page`, `size` e `sort`. `/transactions` filtra por `accountId`, `categoryId`, `type`, `from` e `to`.

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

## Rodando localmente

Pré-requisitos: JDK 21 e Docker.

```bash
docker compose up -d      # sobe o Postgres em localhost:5432
./mvnw spring-boot:run    # sobe a aplicação em localhost:8080
```

O profile `local` é o default e já aponta para o banco do compose. O Flyway cria o schema e semeia as categorias padrão no primeiro boot. A interface web fica em `http://localhost:8080`.

O segredo JWT do profile `local` é um placeholder de desenvolvimento — ele não vale para nada além da sua máquina.

## Variáveis de ambiente (profile `prod`)

| Variável | Observação |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `JDBC_DATABASE_URL` | Derivada automaticamente pelo buildpack Java quando há Postgres provisionado |
| `JDBC_DATABASE_USERNAME` | idem |
| `JDBC_DATABASE_PASSWORD` | idem |
| `JWT_SECRET` | Mínimo de 32 caracteres. Segredo vazio derruba a aplicação no boot |
| `JWT_EXPIRATION_MS` | Opcional, default 3600000 |
| `PORT` | Fornecida pela plataforma |

Nenhuma delas tem valor default no código: variável faltando é falha de boot, não comportamento silencioso.

## Deploy

Hospedado no Heroku. `Procfile` e `system.properties` definem o processo web e o runtime Java 21.

```bash
heroku pg:backups:capture   # o plano essential-0 não tem rollback nem PITR
git push heroku main
heroku logs --tail          # conferir o Flyway aplicando as migrations
```

O build do buildpack roda com `-DskipTests`, porque não há Docker disponível lá para os testes de integração.

## Testes

Hoje existe apenas o teste de carga de contexto, e ele precisa de um Postgres acessível para subir. A suíte de integração com Testcontainers é o primeiro item do próximo ciclo.

```bash
./mvnw test
```

## Próximos passos

Contas a pagar e a receber, parcelamento, baixa em lote, recorrência, previsão de fluxo de caixa, refresh token, verificação de e-mail e login com Google — precedidos por uma base de testes de integração, CI e OpenAPI.
