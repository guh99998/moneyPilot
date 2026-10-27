# CLAUDE.md

Guia de trabalho para o Claude neste repositório. O **README.md** é a referência do produto e da arquitetura (stack, decisões de domínio, API, deploy) — leia-o antes de mexer em qualquer domínio. Este arquivo cobre **como** trabalhamos.

O moneyPilot tem **usuários reais em produção**. Na dúvida entre rapidez e segurança dos dados, escolha segurança e pergunte.

## Fluxo de desenvolvimento (SDD)

Toda feature ou mudança de comportamento segue Spec-Driven Development, com uma pasta em `specs/` (ver `specs/README.md`):

1. **Spec** (`spec.md`) — o quê e por quê, critérios de aceite, fora de escopo. Gustavo aprova.
2. **Plano** (`plan.md`) — como, impacto em schema, API e telas. Gustavo aprova.
3. **Tarefas** (`tasks.md`) — passos pequenos, cada um ligado a um critério de aceite.
4. **Implementação** — código + testes que provam os critérios.

Regras:

- Não comece a implementar sem spec e plano aprovados. Correção pequena de bug pode pular a spec, mas não o teste que reproduz o bug.
- **Regra de negócio que a spec não define, pergunte** — não invente. Vale em especial para dinheiro, datas e status.
- Não implemente nada que esteja em "Fora de escopo" nem "melhorias" não pedidas.
- Se a implementação revelar que a spec está errada ou incompleta, pare e proponha a mudança na spec antes de seguir.
- Ao terminar, marque as tarefas concluídas em `tasks.md` e atualize o README se a API ou uma decisão de domínio mudou.

## Branches e commits

- Fluxo: `feature/*` ou `fix/*` → `develop` → `main` → `deploy`. Nunca commitar direto em `main` ou `deploy`.
- Uma branch por spec, criada a partir da `develop` atualizada, com o mesmo slug da pasta da spec (`feature/007-orcamento-recorrente`).
- Merges com `--no-ff` e mensagem `merge: branch 'x' into y`, como no histórico.
- Commits em inglês, Conventional Commits com escopo do domínio: `feat(bill): ...`, `fix(web): ...`, `test(report): ...`, `docs: ...`. Commits pequenos e separados entre código e testes quando fizer sentido.

## Comandos

```bash
docker compose up -d                   # Postgres local
./mvnw spring-boot:run                 # app em localhost:8080 (profile local)
./mvnw verify                          # testes Java (precisa do Docker)
node --test "src/test/js/*.test.mjs"   # testes do cliente web
```

Rode as duas suítes antes de dizer que algo está pronto. Se algum teste falhar, reporte a falha — não a esconda nem ajuste o teste para passar sem explicar.

## Regras invioláveis

- **Migrations**: nunca editar migration já aplicada (`V1`..`Vn`); toda mudança de schema é uma migration nova. Migration que altera ou apaga dados existentes precisa ser destacada no plano e aprovada.
- **Dinheiro** é `BigDecimal` escala 2, nunca `double`/`float`. No front, contas em centavos inteiros.
- **"Hoje"** vem do `Clock` injetado (`America/Sao_Paulo`), nunca de `LocalDate.now()` sem clock.
- **Isolamento**: toda query é escopada ao usuário autenticado; recurso de outro usuário responde `404`.
- **Erros** de domínio são exceções em `exception/` tratadas pelo `GlobalExceptionHandler`, no formato `ErrorResponse`.
- **Produção**: o Claude não faz deploy, não roda nada contra o banco de produção e não mexe em segredos. Pode preparar scripts, checklists e rollback para o Gustavo executar.
- Não adicionar dependências (Maven ou JS) sem aprovação. O front é JS puro, sem build step e sem bibliotecas.

## Convenções de código

- Pacote por domínio (`account`, `bill`, `report`...): entidade, repositório, records de request/response, service e controller juntos. Siga o padrão do domínio vizinho.
- Testes de integração estendem `AbstractIntegrationTest` (Testcontainers + Flyway real) e usam MockMvc. Cada critério de aceite da spec deve ter ao menos um teste.
- Telas novas do cliente web são módulos em `src/main/resources/static/js/views/`, mobile-first, seguindo os breakpoints e o tema existentes.
