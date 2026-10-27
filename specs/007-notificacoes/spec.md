# 007 — Notificações

**Status:** em implementação
**Branch:** feature/007-notificacoes

## Problema

A visão geral mostra vencimentos, mas só para quem abre a visão geral. Quem entra direto em Lançamentos ou Contas não fica sabendo que tem conta vencendo ou orçamento estourando, e é justamente isso que gera juros e surpresa no fim do mês.

## Objetivo

Em qualquer tela do app, o usuário vê de relance se algo precisa de atenção e chega em um clique ao que precisa resolver.

## Regras de negócio

**Formato**

- Um sino ao lado do logo, visível em todas as telas (na sidebar no desktop e na barra do topo no celular), com o número de alertas ativos. Sem alertas, o sino aparece sem número.
- Clicar no sino abre a lista de alertas. Clicar num alerta leva para a tela já filtrada (por exemplo, A pagar e receber com os vencidos).
- Alertas são **calculados a partir da situação atual**: quando o problema é resolvido (a conta é paga, o orçamento é ajustado), o alerta some sozinho. Não há histórico nem "lida/não lida".
- "Hoje" segue o fuso de São Paulo, como no resto do app.
- Os alertas se atualizam a cada troca de tela, ao voltar para a aba do app e depois de qualquer alteração feita pelo próprio usuário (baixar um título, ajustar um orçamento).

**Alertas desta versão**

| Alerta | Quando aparece | Mostra |
|---|---|---|
| Contas a pagar a vencer | Títulos a pagar em aberto com vencimento de hoje até hoje + 7 dias | Quantidade, total e a data limite (o maior vencimento entre os títulos do alerta) |
| Contas a receber a vencer | Títulos a receber em aberto com vencimento de hoje até hoje + 7 dias | Quantidade, total e a data limite (o maior vencimento entre os títulos do alerta) |
| Contas a pagar vencidas | Títulos a pagar em aberto com vencimento antes de hoje | Quantidade e total |
| Contas a receber atrasadas | Títulos a receber em aberto com vencimento antes de hoje | Quantidade e total |
| Orçamento em 80% | Categoria com orçamento no mês corrente e gasto entre 80% e 100% | Categoria, gasto e orçado |
| Orçamento estourado | Categoria com gasto acima de 100% do orçado no mês corrente | Categoria, gasto e orçado |

- Título que vence hoje conta como "a vencer", não como "vencido".
- Títulos são agrupados num único alerta por tipo; orçamento gera um alerta por categoria.
- O gasto do orçamento é o mesmo da tela Orçamentos: despesas do mês corrente na categoria, sem transferências.
- Faixas do orçamento: de 80,00% até 100,00% do orçado é "em 80%"; só acima de 100,00% é "estourado".

**Dispensar**

- Cada alerta tem um botão "dispensar", que o esconde da lista e do contador.
- O alerta dispensado **volta se a situação mudar**: muda a quantidade ou o total dos títulos, ou o orçamento passa de 80% para estourado. Enquanto o conteúdo for o mesmo, ele continua escondido.
- Dispensar vale para a conta inteira, em todos os aparelhos.
- Alerta de orçamento dispensado só volta ao mudar de faixa (80% para estourado) ou na virada do mês; gasto maior dentro da mesma faixa não o traz de volta.
- Quando o problema some (título baixado, orçamento ajustado), a dispensa é esquecida: se o mesmo alerta reaparecer depois, ele aparece de novo.

## Critérios de aceite

- [ ] **CA1** — Dados 3 títulos a pagar vencendo nos próximos 7 dias, quando o usuário abre qualquer tela, então o sino mostra o contador e a lista tem um alerta com quantidade 3, o total e a data limite.
- [ ] **CA2** — Dado um título a pagar vencido, quando ele é baixado, então o alerta de vencidos some (ou é recalculado sem ele) sem ação do usuário.
- [ ] **CA3** — Dado um título que vence hoje, então ele aparece em "a vencer" e não em "vencidas".
- [ ] **CA4** — Dada uma categoria com 85% do orçamento gasto no mês, então aparece o alerta de 80%; ao passar de 100%, aparece o de estourado no lugar dele.
- [ ] **CA5** — Dado um alerta dispensado, quando nada muda, então ele continua escondido; quando entra um título novo na mesma janela, então ele volta.
- [ ] **CA6** — Dado um clique num alerta de títulos, então o app abre A pagar e receber já filtrado pelo que o alerta descreve.
- [ ] **CA7** — Dado um usuário sem nada a vencer, vencido ou estourado, então o sino aparece sem número e a lista diz que está tudo em dia.
- [ ] **CA8** — Os alertas de um usuário nunca incluem dados de outro.

## Casos de borda

- Título cancelado ou já baixado não entra em nenhum alerta
- Categoria sem orçamento no mês não gera alerta
- Orçamento com gasto exatamente em 80% ou exatamente em 100%
- Virada do dia: um título "a vencer" passa a "vencido" à meia-noite de São Paulo
- Usuário com muitos títulos: o alerta é um só por tipo, não um por título

## Fora de escopo

- Notificação por e-mail ou push no celular (depois da 001, como opção que o usuário liga)
- Saldo projetado negativo e recorrências não geradas (candidatos à próxima versão)
- Janela de "a vencer" configurável por usuário (fixa em 7 dias)
- Histórico de notificações e "lida/não lida"

## Perguntas resolvidas

Decisões de 27/10/2026:

- Dispensar vale em todos os aparelhos (guardado no servidor).
- 80,00% já entra na faixa de 80%; 100,00% exato ainda não estourou.
- O sino fica ao lado do logo, na sidebar e na barra do celular.
- A "data limite" é o maior vencimento entre os títulos do alerta.
- Alerta de orçamento dispensado só volta ao mudar de faixa.
