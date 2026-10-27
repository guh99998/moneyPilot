# Specs

Cada feature ou mudança de comportamento ganha uma pasta numerada, copiada de `_template/`:

```
specs/
├── _template/                 spec.md, plan.md, tasks.md em branco
└── 001-nome-curto/
    ├── spec.md                o quê e por quê — escrito/aprovado pelo Gustavo
    ├── plan.md                como — proposto pelo Claude, aprovado pelo Gustavo
    └── tasks.md               passos de implementação e progresso
```

## Ciclo

| Fase | Responsável | Pronto quando |
|---|---|---|
| Spec | Gustavo decide, Claude ajuda a redigir e questiona | Critérios de aceite testáveis e "fora de escopo" preenchido |
| Plano | Claude propõe, Gustavo aprova | Impacto em schema, API, telas e dados existentes explícito |
| Tarefas | Claude quebra, Gustavo aprova a ordem | Cada tarefa cabe num commit e aponta para um critério |
| Implementação | Claude implementa, Gustavo revisa e faz o merge | Testes cobrindo cada critério, suítes verdes, README atualizado |

## Status

Use o campo **Status** no topo do `spec.md`: `rascunho` → `aprovada` → `em implementação` → `entregue` (ou `descartada`).

A spec é viva: mudou de ideia no meio, atualize a spec antes do código.
