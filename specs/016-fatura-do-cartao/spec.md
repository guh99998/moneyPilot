# 016 — Fatura do cartão de crédito

**Status:** em revisão (emenda de 2026-11-09: compra recorrente no cartão implementada; faltam a checagem em 375px e as skills do projeto; o restante já está na `main`)
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

**A pagar e receber, Visão geral e alertas** (mudança de 2026-11-06)

17. Em "A pagar e receber", cada fatura de cartão com dias e ainda com valor a pagar aparece como **uma linha própria**, como uma conta a pagar: "Fatura do <cartão>", vencimento da fatura, **valor total** (e "falta R$ X" se houve pagamento parcial) e a situação. Entram a fatura **aberta**, a **fechada** e a **vencida**; a futura não. Não é um título gravado: a linha é calculada a cada leitura, com o mesmo cálculo da tela Faturas.
18. A fatura **aberta** leva o selo "Aberta" e o texto "fecha em dd/mm, ainda recebe compras", porque o valor sobe a cada compra nova. Mostra o vencimento real da fatura.
19. A linha tem os botões **"Ver itens da fatura"** (abre a tela Faturas no cartão certo) e **"Pagar fatura"** (o mesmo diálogo de pagamento da tela Faturas, uma transferência para o cartão). Fica fora da seleção em lote e não tem editar nem cancelar.
20. As linhas de fatura ficam no topo da primeira página e só aparecem quando os filtros combinam: tipo "a pagar" (ou sem tipo); status em aberto ou todos (vencido mostra só as vencidas; baixado e cancelado não mostram); conta vazia ou igual ao cartão; intervalo de vencimento que contenha o vencimento da fatura; sem filtro de categoria. Não entram na paginação nem na ordenação da lista.
21. O quadro "A pagar" da Visão geral soma o que falta das faturas aberta, fechada e vencida (quantidade e total; as vencidas também entram no vencido).
22. Os alertas de a pagar incluem só fatura **fechada ou vencida**: a vencida entra no alerta de vencidas; a fechada com vencimento em até 7 dias, no de vencimento próximo. A fatura aberta não gera alerta.
23. "Próximos vencimentos" da Visão geral inclui as faturas aberta, fechada e vencida, misturadas aos títulos por data de vencimento (a mais próxima primeiro), com o valor total, o selo da situação e o link para a tela Faturas.
24. A previsão não muda (a fatura já entra nela pelo vencimento, e não é um título gravado, então não conta duas vezes).

**Início das faturas e tela Faturas** (mudança de 2026-11-06)

25. O pedido de conta de cartão aceita `invoicesFrom` (data, opcional): a partir de qual ciclo as faturas contam. Vale o **início do ciclo que contém a data**, calculado com os dias do cartão. Não pode ser futura (`400` com erro no campo) e só vale com fechamento e vencimento: sem eles, ou em conta que não é cartão, é `400`. Omitido na criação (ou na primeira vez que os dias aparecem), continua sendo o ciclo de hoje; omitido na edição, mantém o valor atual.
26. Com um início anterior, as compras desde esse ciclo entram nas faturas. As faturas antigas passam a existir e, se não estiverem quitadas, aparecem como vencidas na tela Faturas, em "A pagar e receber", na Visão geral e nos alertas, até serem pagas (o pagamento é a transferência para o cartão, com a data da época). Mover o início para a frente esconde de novo as compras anteriores.
27. A tela **Faturas** vira **uma lista única** com as faturas de todos os cartões com fechamento e vencimento, misturadas. Cada linha mostra o cartão, o vencimento, a situação, o total, o que falta pagar e as ações "Ver compras", "Pagar fatura". "Pagar fatura" vem com o valor até aquela fatura do mesmo cartão (o pagamento abate da mais antiga).
28. A lista tem **filtros**: cartão (padrão "Todos"), situação (aberta, fechada, vencida, paga, futura) e vencimento de/até. E **ordenação** por cabeçalho: vencimento (padrão, da mais nova para a mais antiga), total, falta pagar, cartão e situação. Filtros e ordenação são feitos no navegador, com os dados de cada cartão.
29. No topo, um resumo (quanto há a pagar, quanto está vencido, quantas faturas abertas) e, para cada cartão, a fatura atual em destaque, com "Nova compra", "Compra parcelada" e "Pagar fatura". A tela diz desde quando as faturas contam em cada cartão.
30. "Ver itens da fatura" (em A pagar e receber e em Próximos vencimentos) abre a tela Faturas com o filtro de cartão naquele cartão.

**Compra recorrente no cartão** (emenda de 2026-11-09)

31. Cartão com fechamento e vencimento aceita **compra recorrente**: descrição, categoria de despesa, valor, dia do mês (1 a 31, último dia em mês curto), início e fim opcional. Cartão sem dias, ou conta de outro tipo: `400`.
32. Em cada data de cobrança até hoje, a compra vira uma **despesa no cartão** com essa data, sem ação da pessoa. A geração é sob demanda e idempotente, como a recorrência de títulos: a recorrência guarda até que data já gerou (`generatedUntil`), então gerar duas vezes não duplica e **apagar uma cobrança gerada não a recria**.
33. A cobrança gerada é um lançamento comum: pode ser editada (o valor mudou naquele mês) ou apagada.
34. As cobranças ainda não geradas são **projetadas** (nada gravado): na fatura do ciclo em que caem e na previsão, pelo vencimento da fatura. Nunca contam duas vezes, porque a projeção só começa depois de `generatedUntil`.
35. A tela Faturas projeta até o fim do ciclo seguinte ao atual; a previsão projeta até o seu horizonte. A fatura mostra quanto do total é previsto. "A pagar e receber", a Visão geral e os alertas não incluem o previsto.
36. Editar a recorrência vale para as cobranças ainda não geradas. Desativar para de gerar e de projetar. Apagar remove a recorrência e mantém as cobranças já geradas.
37. Recorrência com início no passado gera as cobranças desde o início (respeitando `invoicesFrom` na fatura, como qualquer compra).
38. Título ou recorrência de título novo, ou editado, com conta = cartão com dias: `400` com erro no campo `accountId`, orientando usar a compra recorrente. Baixar um título **no** cartão continua permitido (pagar um boleto com o cartão é legítimo). Títulos existentes não mudam.

**Compra parcelada: valor da parcela e parcela atual** (emenda de 2026-11-09, aguardando aprovação)

39. A compra parcelada no cartão aceita **uma de duas formas** de informar o valor: o **valor total** (regra 8, com o centavo que sobra na primeira parcela) ou o **valor da parcela**. Informar as duas, ou nenhuma, é `400`.
40. Com o valor da parcela, as N parcelas saem **iguais** e o total é valor × N, sem centavo sobrando: é o que a fatura do banco mostra.
41. A **parcela atual** (opcional, de 1 a N, padrão 1) só vale com o valor da parcela. Lança apenas as parcelas da atual até a N, numeradas como na compra original (`4/10` a `10/10`): a primeira lançada cai na data informada e as outras nos meses seguintes. Serve para quem tem uma compra em andamento e não sabe a data original. Parcela atual com o valor total é `400` (o centavo que sobra fica na parcela 1, que não seria lançada).
42. Apagar uma parcela lançada remove o grupo lançado, como na regra 9. O parcelamento de títulos (A pagar e receber) não muda.

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
12. A fatura aberta, a fechada e a vencida aparecem em "A pagar e receber" com o valor total, e a futura não; a aberta vem marcada como em andamento.
13. As linhas respeitam os filtros da regra 20 e não entram na seleção em lote, na paginação nem na ordenação.
14. "Ver itens da fatura" abre a tela Faturas no cartão da linha; "Pagar fatura" registra a transferência para o cartão e a linha se atualiza.
15. O quadro "A pagar" da Visão geral soma o que falta das faturas aberta, fechada e vencida.
16. Os alertas incluem fatura fechada ou vencida e ignoram a aberta; pagar a fatura apaga o alerta.
17. A previsão e o saldo disponível não mudam com essa mudança, e a fatura nunca conta duas vezes.
18. "Próximos vencimentos" mostra as faturas junto com os títulos, ordenadas por vencimento.
19. `invoicesFrom` aceito na criação e na edição do cartão: normalizado para o início do ciclo, `400` se futuro ou sem os dias ou em conta que não é cartão, mantido quando omitido na edição.
20. Com um início anterior, as compras desde esse ciclo entram nas faturas, e a fatura antiga não quitada aparece como vencida; mover o início para a frente esconde as anteriores.
21. A tela Faturas lista as faturas de todos os cartões em uma só lista, com filtros de cartão, situação e vencimento e ordenação por vencimento, total, falta, cartão e situação.
22. A tela mostra o resumo, a fatura atual de cada cartão em destaque e desde quando as faturas contam; "Ver itens da fatura" abre a tela já filtrada no cartão.
23. Criar compra recorrente em cartão com dias funciona; em cartão sem dias ou em conta de outro tipo é `400` (regra 31).
24. A cobrança vencida vira despesa no cartão na data certa, o dia 31 vale o último dia do mês, e gerar duas vezes não duplica (regras 32 e 37).
25. A cobrança gerada pode ser editada e apagada, e apagada não volta (regras 32 e 33).
26. A cobrança futura aparece como prevista na fatura (até o fim do ciclo seguinte) e nas duas previsões, e some da projeção quando é gerada, sem contar duas vezes (regras 34 e 35).
27. Editar vale só para as futuras; desativar para de gerar e de projetar; apagar mantém as cobranças geradas (regra 36).
28. Título e recorrência de título em cartão com dias dão `400` no campo `accountId`; baixar um título no cartão continua funcionando; recurso de outro usuário dá `404`; OpenAPI e `MoneyScaleIntegrationTest` cobrem as rotas; as telas funcionam no mobile, com teclado e leitor de tela (regra 38).
29. O valor total continua funcionando como antes; o valor da parcela cria N parcelas iguais; informar os dois valores, nenhum, ou parcela atual com o valor total é `400` no campo certo (regras 39 a 41).
30. Com a parcela atual k, são lançadas N−k+1 parcelas, numeradas k a N, a primeira na data informada e as outras mensais; k fora de 1 a N é `400` (regra 41).
31. O formulário "Compra parcelada" deixa escolher entre valor total e valor da parcela, mostra a prévia das parcelas e funciona no mobile, com teclado e leitor de tela.

## Casos de borda

- Fechamento no dia 31 em meses mais curtos.
- Compra no dia do fechamento (próxima fatura).
- Estorno maior que as compras do ciclo (fatura negativa vira crédito).
- Pagamento a mais ou em atraso; fatura paga e depois alterada por compra apagada.
- Cartão marcado no disponível com dias informados.

## Fora de escopo

Limite do cartão, juros e multa, parcelar a fatura, antecipar parcelas, importar fatura, pontos e milhas, migrar lançamentos antigos, gravar a fatura como título (`Bill`).

## Migration

`V21__add_recurring_card_purchases.sql` (emenda): só adiciona a tabela `recurring_purchases` e a coluna nula `transactions.recurring_purchase_id`; não altera dado existente.

`V20__add_credit_card_invoices.sql` só adiciona colunas nulas em `accounts` (`closing_day`, `due_day`, `invoices_from`) e `transactions` (`installment_group_id`, `installment_number`, `installment_total`). Não altera dado existente; nenhum número muda no deploy.

## Decisões tomadas

- A compra é despesa na data da compra; o pagamento é transferência.
- Parcelada vira uma parcela por mês.
- O cartão existente só muda quando a pessoa informa os dias.
- A compra no dia do fechamento cai na próxima fatura.
- Faturas em tela própria no menu.
- O início das faturas passa a ser editável (`invoicesFrom`), para quem tem fatura antiga ou atrasada; o padrão continua sendo o ciclo de hoje (decidido em 2026-11-06).
- A tela Faturas vira uma lista única de todos os cartões, com filtros e ordenação no navegador (decidido em 2026-11-06).
- A fatura também aparece em "Próximos vencimentos" da Visão geral (decidido em 2026-11-06, depois da primeira versão desta mudança).
- A fatura aparece em "A pagar e receber" como uma linha calculada, aberta, fechada ou vencida, com o valor total; entra no quadro "A pagar" da Visão geral e nos alertas (só fechada ou vencida). Decidido em 2026-11-06, em lugar de ficar fora, como a versão inicial da spec previa.
- Compra recorrente no cartão (decidido em 2026-11-09; na interface o nome é "Assinatura mensal", a API segue `/recurring-purchases`): a cobrança vira despesa sozinha na data; as próximas são projetadas na fatura e na previsão; título e recorrência de título em cartão com dias passam a ser `400` (os existentes não mudam). A marca d'água `generatedUntil` (e não uma chave única) garante que cobrança apagada não volta.
- Compra parcelada com valor da parcela e parcela atual (proposto em 2026-11-09): só no cartão; parcela atual só com o valor da parcela; o parcelamento de títulos fica fora.

## Decisões a confirmar

- Limite do cartão fica fora.
- Tamanho: é a maior das três specs; revisar com calma antes da `main` pode ficar apertado.
