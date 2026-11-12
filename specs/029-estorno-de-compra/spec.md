# 029 — Estorno de compra no cartão

**Status:** rascunho (ideia para depois, ainda sem regras aprovadas)
**Branch:** feature/029-estorno-de-compra

## Problema

Quando uma compra no cartão é cancelada e o banco devolve o dinheiro, a pessoa só pode apagar o lançamento. Isso reescreve o histórico e, em fatura já paga, desorganiza o que foi pago.

## Objetivo

Estornar uma compra (ou parcelas dela) no cartão sem apagar o histórico: o estorno entra como uma receita ligada à compra, e a compra fica marcada como estornada.

## Ideias já discutidas (na spec 028, retiradas do escopo em 2026-11-12)

Ainda precisam de aprovação do Gustavo; ficam aqui só para não se perder:

- Estorno é uma **receita no mesmo cartão**, de valor igual ao da compra, ligada a ela. A categoria é a global "Estorno" (receita), sem escolha da pessoa.
- Só estorno **inteiro**; o parcial fica de fora (quem precisar adianta a fatura no valor).
- Em compra parcelada, o alcance é só esta parcela, esta e as seguintes, ou a compra inteira. A data de cada estorno é a maior entre a informada e a da parcela; parcelas já estornadas são puladas.
- Desfazer o estorno apaga a receita. Compra ou parcela estornada não se edita nem se apaga (`409`), e o estorno não se edita.
- O código dessa primeira versão está guardado na branch local `feature/refund-parked` (migration, rota, tela e testes), para servir de ponto de partida.

## Regras de negócio, critérios de aceite, casos de borda

A definir quando a spec for retomada.

## Fora de escopo

A definir.

## Perguntas em aberto

- Vale a complexidade de escopo de parcelas, ou basta estornar a compra inteira?
- Como a tela mostra, para quem vê só a fatura, que a fatura antiga continua com a cobrança e o crédito entra na fatura atual?
