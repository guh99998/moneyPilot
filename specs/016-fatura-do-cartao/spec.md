# 016 — Fatura do cartão de crédito

**Status:** rascunho (ideia para conversar, ainda sem regras)
**Branch:** feature/016-fatura-do-cartao
**Depende de:** a definir

## Problema

Quem compra no cartão de crédito não vê quanto vai pagar no fim do mês. Hoje o app aceita uma conta do tipo cartão de crédito, mas ela é só um rótulo: não existe fatura, fechamento, vencimento nem limite. A compra é lançada como despesa na data da compra, mas o dinheiro só sai da conta no vencimento da fatura, então o saldo e a previsão ficam errados.

## Objetivo

A pessoa vê, a qualquer momento, quanto está a fatura aberta do cartão e quando ela vence, e o pagamento da fatura não é contado de novo como despesa.

## Regras de negócio

Nenhuma definida ainda. Esta spec nasceu de uma ideia e precisa de conversa antes de qualquer regra (ver "Perguntas em aberto").

## Critérios de aceite

A escrever depois da conversa.

## Casos de borda

- Compra no dia do fechamento, ou depois dele (cai na fatura seguinte)
- Compra parcelada: a primeira parcela cai na fatura atual e as outras nas seguintes
- Estorno ou compra cancelada
- Fatura paga parcialmente, ou paga em atraso
- Cartão com fechamento no dia 31 em meses mais curtos
- Compra apagada ou editada depois de a fatura ter sido paga

## Fora de escopo

A definir. Candidatos, a confirmar: juros e multa de atraso, parcelamento da fatura, importação da fatura do banco, pontos e milhas.

## Perguntas em aberto

- A compra continua contando como despesa na data da compra (visão de competência) e o pagamento da fatura é só uma transferência? Ou o gasto só aparece quando a fatura é paga (visão de caixa)?
- O app precisa saber o dia de fechamento e o de vencimento de cada cartão?
- O limite do cartão entra? Mostrar quanto ainda está disponível?
- Como tratar compras parceladas: o app já tem parcelamento ou recorrência que possa ser reaproveitado?
- Como a pessoa paga a fatura no app: uma ação "pagar fatura", que tira o valor de uma conta corrente?
- A previsão (relatórios) passa a mostrar o vencimento da fatura como uma saída futura?
- Os orçamentos por categoria continuam contando pela data da compra?
- Relação com a 015: a conta do tipo investimento e o pagamento de fatura são ambos "dinheiro que muda de conta sem ser despesa". Vale resolver os dois com o mesmo modelo de transferência?
