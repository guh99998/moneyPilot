# 026 — Por que o moneyPilot existe

**Status:** rascunho
**Branch:** feature/026-por-que-o-moneypilot
**Depende de:** 020 (site do produto)

## Problema

O site explica **o que** o moneyPilot faz, mas não **por que** ele existe. Quem chega não sabe quem está por trás, o que motivou o projeto nem por que é grátis e de código aberto. Isso enfraquece a confiança, principalmente num app que lida com dinheiro, e deixa o portfólio sem a história que dá sentido ao produto.

## Objetivo

O site ganha uma seção em primeira pessoa contando por que o moneyPilot foi criado, com o texto escrito pelo próprio Gustavo.

## Regras de negócio

- **O texto é do Gustavo.** O Claude estrutura e revisa a forma; não inventa motivações, fatos nem números. Conteúdo pessoal ainda não redigido entra como pergunta em aberto, não como texto de exemplo na página.
- Primeira pessoa, tom próximo e curto: um a três parágrafos, mais um fecho que leva ao próximo passo ("Criar conta" ou "Apoie o projeto").
- Seção própria no site (`/`), com `<h2>`, seguindo a identidade visual e o mobile-first do restante.
- **Repositório público:** sem dados de terceiros, sem usuários reais, sem métricas internas e sem detalhes de infraestrutura. Só o que o Gustavo aceita ver publicado.
- Links para GitHub, licença ou política de privacidade usam os que o site já tem.
- Foto não é obrigatória; se houver, tem texto alternativo.

## Critérios de aceite

- [ ] **CA1** — Dado o site em `/`, então existe a seção com o motivo da criação do moneyPilot, com título e o texto aprovado pelo Gustavo.
- [ ] **CA2** — Dada a seção, então termina com uma chamada para uma ação existente (criar conta ou apoiar), e os links funcionam.
- [ ] **CA3** — Dadas telas de 390px e 1280px, então o texto não gera rolagem horizontal e mantém largura de leitura confortável.
- [ ] **CA4** — Dado um leitor de tela, então a seção tem um `<h2>` na ordem correta dos títulos e contraste adequado nos dois temas.
- [ ] **CA5** — Dado o texto publicado, então não cita dados de usuários reais, métricas internas nem detalhes de infraestrutura.

## Casos de borda

- Texto longo demais no mobile: limite de tamanho acordado na redação.
- Tom diferente das seções vizinhas (hero, recursos, pilares "Grátis / Só seu / Código aberto").

## Fora de escopo

- Página "Sobre" separada, blog ou linha do tempo do projeto.
- Depoimentos e avaliações de usuários.
- Tradução do site.
- Mexer em textos do app (só o site).

## Perguntas em aberto

- **Qual é a história?** O que motivou o projeto (uma dor própria com controle financeiro, aprender, portfólio, tudo isso)? Escreva livremente em tópicos; eu organizo.
- Posição na página: logo depois do hero, entre os recursos e os pilares, ou antes do fecho?
- Quer se identificar (nome e link do GitHub/LinkedIn) ou manter o texto sem identificação pessoal?
- Entra a ligação com "grátis e código aberto" (por que não cobra)?
