# 015 — Investimentos

**Status:** rascunho (ideia para conversar, ainda sem regras)
**Branch:** feature/015-investimentos
**Depende de:** a definir

## Problema

Quando a pessoa investe (compra ações, fundos, renda fixa, cripto...), o dinheiro sai da conta, mas **não é despesa**: ela continua dona do valor, só mudou de lugar. Também **não é receita**. Hoje o app só tem receita e despesa, então a pessoa precisa escolher uma das duas, e qualquer escolha distorce os relatórios, o orçamento e a previsão.

## Objetivo

A pessoa registra um investimento sem que ele conte como despesa nem como receita, e o app continua mostrando números honestos.

## Regras de negócio

Nenhuma definida ainda. Esta spec nasceu de uma ideia e precisa de conversa antes de qualquer regra (ver "Perguntas em aberto").

## Critérios de aceite

A escrever depois da conversa.

## Casos de borda

- Resgate (o dinheiro volta para a conta): não é receita, mas o rendimento talvez seja
- Investimento feito com título agendado ou recorrente (aporte mensal)
- Aporte que estoura o orçamento da categoria
- Investimento apagado ou editado depois de já ter entrado em relatórios

## Fora de escopo

A definir. Candidatos, a confirmar: cotação em tempo real, importação de corretora, imposto de renda sobre ganho de capital.

## Perguntas em aberto

- Qual é o modelo? (a) novo tipo de lançamento "investimento", ao lado de receita e despesa; (b) transferência entre a conta corrente e uma conta de investimento; (c) só uma categoria especial que os relatórios ignoram.
- O app só registra o dinheiro que entrou e saiu (aporte e resgate), ou também acompanha o valor atual e o rendimento?
- O saldo e a previsão contam o valor investido como parte do patrimônio, ou ele "some" do saldo da conta?
- Rendimento e dividendos entram como receita? Só o ganho, ou o resgate inteiro?
- Aporte entra no orçamento mensal? (Hoje não é despesa, mas é dinheiro que a pessoa separou.)
- Os relatórios ganham uma visão própria de investimentos, ou basta não poluir os números atuais?
- Precisa migrar lançamentos antigos que a pessoa já registrou como despesa ou receita?
