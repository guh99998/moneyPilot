# 016 — Fatura do cartão de crédito

**Status:** em implementação
**Branch:** feature/016-fatura-do-cartao
**Depende de:** 015 (campo `countsAsAvailable`). Ordem de merge: 027, 015, 016 (migrations V18, V19, V20).

## Problema

A conta do tipo cartão de crédito é só um rótulo: não há fatura, fechamento nem vencimento. A compra reduz o saldo na data da compra, mas o dinheiro só sai no vencimento da fatura, então saldo e previsão ficam errados. Na 015 o cartão ficou marcado no saldo disponível de propósito, porque desmarcado o disponível subia sem nada avisar que a fatura vinha.

## Objetivo

A pessoa vê quanto está a fatura do cartão e quando vence. O vencimento aparece como saída na previsão. Pagar a fatura não conta como despesa de novo.

Exemplo: cartão com fechamento no dia 10 e vencimento no dia 17. Uma compra de R$ 200,00 no dia 5 entra na fatura que fecha no dia 10 e vence no dia 17. Pagar R$ 200,00 da conta corrente é uma transferência: o resultado do mês não muda.

## Regras de negócio

**Configuração**

1. Cartão pode ter `closingDay` e `dueDay` (1 a 31), sempre os dois juntos. Em mês mais curto vale o último dia. Conta de outro tipo com esses campos é `400`. Trocar o tipo para outro limpa os dias.
2. Ao informar os dias pela primeira vez, o cartão sai do saldo disponível (salvo escolha explícita de `countsAsAvailable` no mesmo pedido) e `invoicesFrom` recebe o início do ciclo atual. Nada muda em cartão sem os dias.
3. Compras anteriores a `invoicesFrom` e o saldo inicial não entram em fatura, para o histórico antigo não virar dívida. Quem tem compra em andamento lança com a data original: só as parcelas a partir do ciclo atual entram.

**Fatura (calculada em leitura, nada gravado, como o saldo)**

4. Ciclo: do dia do fechamento até a véspera do fechamento seguinte. A compra no dia do fechamento cai na próxima fatura. O vencimento é o primeiro `dueDay` depois do fechamento.
5. Total da fatura = despesas do ciclo menos receitas do ciclo (estorno é receita no cartão). Transferências não entram.
6. Pagamento é uma transferência para o cartão, pela rota de transferência existente. Abate das faturas mais antigas para as mais novas: pagamento parcial deixa o resto na mesma fatura, sem juros; pagamento a mais vira crédito nas seguintes.
7. Situação: `OPEN` (ciclo atual), `CLOSED` (fechada, a pagar), `OVERDUE` (venceu sem quitar), `PAID`, `FUTURE` (só parcelas futuras).

**Parcelas**

8. Compra parcelada cria N despesas ligadas por um grupo, no mesmo dia dos meses seguintes. O centavo que sobra fica na primeira parcela, como em `BillService.createInstallmentPlan`.
9. Parcela não é editada sozinha (`409`, como transferência); apagar remove o grupo todo.

**Saldo e previsão**

10. Cartão desmarcado com dias: cada fatura não quitada entra como saída no vencimento nas duas previsões, e a vencida cai no primeiro período. Cartão marcado não projeta, porque a compra já reduziu o disponível.
11. "Guardado" deixa de incluir cartões. Dívida não é dinheiro guardado.
12. Relatórios e orçamento não mudam: a parcela conta no mês da sua data.

**Telas**

13. Formulário de conta pede fechamento e vencimento quando o tipo é cartão.
14. Tela "Faturas" no menu: fatura atual em destaque, lista das outras, compras do ciclo e ações "Pagar fatura" e "Compra parcelada".
15. No Perfil, o cartão com dias não diz "Guardado": diz que entra pela fatura. Editar o cartão sem enviar os dias mantém os dias.
16. Aviso em Novidades sobre a fatura e página na ajuda.

## Critérios de aceite

1. Dias sem par, ou em conta de outro tipo, respondem `400`; trocar o tipo limpa os dias.
2. Informar os dias pela primeira vez desmarca o cartão do disponível e define `invoicesFrom`; cartão sem dias não muda nenhum número.
3. O ciclo e o vencimento seguem as regras 4: fechamento 31 em fevereiro, compra no dia do fechamento, vencimento antes e depois do fechamento, virada de ano.
4. O total da fatura considera estorno e ignora transferências e compras anteriores a `invoicesFrom`.
5. Pagamento parcial, pagamento a mais e atraso resultam nas situações e valores da regra 6 e 7.
6. A compra parcelada cria N parcelas em faturas seguidas, com o centavo na primeira; editar parcela sozinha dá `409`; apagar remove o grupo.
7. Cartão desmarcado com dias projeta as faturas nas duas previsões; marcado não projeta; pagar reduz o disponível e a fatura sem contar duas vezes.
8. "Guardado" não inclui cartões; relatórios, orçamento e `/accounts/{id}/balance` ficam iguais.
9. Conta de outro usuário responde `404` em todas as rotas novas.
10. O contrato OpenAPI documenta as rotas e campos novos, e o `MoneyScaleIntegrationTest` cobre a rota de parcelas.
11. As telas funcionam no mobile, com teclado e leitor de tela.

## Casos de borda

- Fechamento no dia 31 em meses mais curtos.
- Compra no dia do fechamento (próxima fatura).
- Estorno maior que as compras do ciclo (fatura negativa vira crédito).
- Pagamento a mais ou em atraso; fatura paga e depois alterada por compra apagada.
- Cartão marcado no disponível com dias informados.

## Fora de escopo

Limite do cartão, juros e multa, parcelar a fatura, antecipar parcelas, importar fatura, pontos e milhas, alerta de vencimento da fatura, somar a fatura no quadro "A pagar" da Visão geral, migrar lançamentos antigos.

## Migration

`V20__add_credit_card_invoices.sql` só adiciona colunas nulas em `accounts` (`closing_day`, `due_day`, `invoices_from`) e `transactions` (`installment_group_id`, `installment_number`, `installment_total`). Não altera dado existente; nenhum número muda no deploy.

## Decisões tomadas

- A compra é despesa na data da compra; o pagamento é transferência.
- Parcelada vira uma parcela por mês.
- O cartão existente só muda quando a pessoa informa os dias.
- A compra no dia do fechamento cai na próxima fatura.
- Faturas em tela própria no menu.

## Decisões a confirmar

- Limite do cartão fica fora.
- A fatura entra na previsão, mas não no quadro "A pagar" nem nos alertas; fica uma diferença entre os dois números na Visão geral até uma próxima spec.
- Tamanho: é a maior das três specs; revisar com calma antes da `main` pode ficar apertado.
