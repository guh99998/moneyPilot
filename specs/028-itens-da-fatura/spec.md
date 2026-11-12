# 028 — Ajuste dos itens da fatura

**Status:** rascunho
**Branch:** feature/028-itens-da-fatura
**Depende de:** 016 (fatura do cartão). Muda a regra 9 e a regra 28 da 016.

## Problema

Os itens de uma fatura são lançamentos no cartão, mas a pessoa quase não consegue ajustá-los:

- Uma **parcela** de compra parcelada não se edita (`409`): para corrigir um valor ou uma descrição é preciso apagar o grupo inteiro e lançar de novo.
- Quando a compra é **cancelada** e o banco devolve o dinheiro, a pessoa só pode apagar o lançamento, o que reescreve o histórico e, em fatura já paga, desorganiza o que foi pago.
- Na tela Faturas, as faturas aparecem da mais nova para a mais antiga, então as atrasadas ficam escondidas embaixo.
- Pagar uma parte da fatura antes de ela fechar já funciona (o pagamento abate da fatura mais antiga), mas a tela não deixa isso claro.

## Objetivo

Nos itens da fatura do cartão, a pessoa pode **editar** uma compra (inclusive parcela, uma ou as seguintes), **estornar** ou **apagar** a compra. A tela Faturas lista da mais antiga para a mais nova, e o pagamento adiantado de um valor qualquer fica visível e explicado.

Exemplo: compra de 10 parcelas de R$ 83,33. A pessoa corrige a categoria da parcela 4/10 e das seguintes. Depois cancela a compra na loja: estorna da parcela 6/10 em diante e o cartão recebe os estornos nas faturas certas.

## Regras de negócio

**Editar um item da fatura**

1. Vale para despesa ou receita em conta cartão (com ou sem dias). Edita descrição, categoria, data e valor. A conta não muda por aqui.
2. A **parcela** deixa de dar `409` (muda a regra 9 da 016). Continua no grupo; `installmentNumber` e `installmentTotal` não mudam.
3. Em parcela, a pessoa escolhe o alcance: **só esta parcela** ou **esta e as seguintes**. No segundo caso, descrição, categoria e valor valem para todas as parcelas do grupo com número maior ou igual; a data só muda a da parcela editada (as outras mantêm a sua).
4. A parcela editada pode mudar de fatura (a data decide); o total das faturas é recalculado em leitura, como sempre.
5. Fatura já paga que muda de total não é reaberta nem corrigida: vale o fundo único da regra 6 da 016. Total menor vira crédito para as seguintes; total maior deixa o excedente a pagar.
6. Lançamento de transferência, gerado por baixa de título e cobrança de assinatura mensal continuam como hoje (transferência e baixa dão `409`; a cobrança gerada já era editável, regra 33).

**Estornar um item da fatura**

7. Estornar cria uma **receita no mesmo cartão** (regra 5 da 016: estorno é receita) ligada à compra, sem apagar a compra. A compra fica marcada como **estornada**.
8. O estorno tem a **data** (padrão hoje) e a **categoria de receita** escolhida pela pessoa. O valor é o da compra, inteiro.
9. Em compra parcelada, a pessoa escolhe: **só esta parcela**, **esta e as seguintes** (o estorno de cada parcela cai na data da própria parcela, para não adiantar estorno de parcela que ainda nem cobrou) ou **a compra inteira**. Parcela já estornada não é estornada de novo (`409`).
10. **Desfazer o estorno** apaga a receita criada e a compra volta ao normal. A receita do estorno não se edita por fora (`409`), como a da baixa de título.
11. Estornar não cria despesa nem some do relatório: despesa e receita do estorno aparecem no mês de cada data.

**Apagar um item da fatura**

12. Apagar continua como hoje (apaga o lançamento; em parcela, o grupo todo, regra 9 da 016). A tela diz isso antes de confirmar e sugere estornar quando a compra já foi cobrada. Compra estornada não se apaga sem desfazer o estorno (`409`).

**Tela Faturas**

13. A lista única de faturas passa a ordenar por **vencimento da mais antiga para a mais nova** como padrão (muda a regra 28 da 016). O cabeçalho continua ordenando por vencimento, total, falta pagar, cartão e situação, e o padrão de cada coluna é o da primeira vez que se clica.
14. A **fatura atual em destaque** no topo e o resumo não mudam.

**Pagamento adiantado**

15. Sem mudança de regra: o pagamento é a transferência para o cartão de **qualquer valor**, abate da fatura mais antiga para a mais nova (regra 6 da 016). O adiantamento de R$ 50 numa fatura de R$ 500 a deixa em R$ 450.
16. A tela deixa isso claro: o diálogo "Pagar fatura" diz qual fatura vai receber o valor, aceita valor menor que o da fatura e mostra "vai ficar em R$ 450,00" antes de confirmar. A fatura com pagamento parcial mostra "pago R$ 50,00, falta R$ 450,00".
17. Fatura aberta totalmente paga mostra o selo **"Paga adiantada"** (ainda recebe compras até o fechamento; se entrar compra nova, volta a mostrar o que falta).

**Contrato**

18. Os valores de dinheiro novos seguem `@Digits(integer = 17, fraction = 2)` e entram no `MoneyScaleIntegrationTest`. Recurso de outro usuário responde `404`. As rotas novas entram no contrato OpenAPI.

## Critérios de aceite

- [ ] **CA1** — Dado uma parcela 4/10 de uma compra no cartão, quando edito a descrição só dela, então só a parcela 4 muda e continua `4/10` no mesmo grupo.
- [ ] **CA2** — Dado o mesmo grupo, quando edito categoria e valor com alcance "esta e as seguintes", então as parcelas 4 a 10 mudam, 1 a 3 ficam iguais e a data só muda a da parcela editada.
- [ ] **CA3** — Dado uma parcela editada para uma data de outro ciclo, quando consulto as faturas, então o total de cada fatura reflete a nova data.
- [ ] **CA4** — Dado uma compra de R$ 100,00 no cartão, quando a estorno em uma data e categoria de receita, então existe uma receita de R$ 100,00 no cartão ligada à compra, a compra aparece como estornada e a fatura soma R$ 0,00.
- [ ] **CA5** — Dado uma compra parcelada, quando estorno "esta e as seguintes", então cada parcela do alcance ganha um estorno na data da própria parcela e as anteriores ficam sem estorno.
- [ ] **CA6** — Dado uma parcela já estornada, quando tento estornar de novo, então recebo `409`.
- [ ] **CA7** — Dado um estorno, quando o desfaço, então a receita some e a compra deixa de aparecer como estornada.
- [ ] **CA8** — Dado uma compra estornada, quando tento apagá-la ou editar a receita do estorno, então recebo `409`.
- [ ] **CA9** — Dado uma transferência ou lançamento de baixa de título, quando tento editar, então continua `409`.
- [ ] **CA10** — Dado uma fatura de R$ 500,00 sem faturas anteriores em aberto, quando pago R$ 50,00, então a fatura mostra pago R$ 50,00 e falta R$ 450,00.
- [ ] **CA11** — Dado uma fatura aberta paga por inteiro, quando abro a tela Faturas, então ela mostra "Paga adiantada".
- [ ] **CA12** — Dado três faturas de vencimentos diferentes, quando abro a tela Faturas sem ordenar, então a lista vem da mais antiga para a mais nova.
- [ ] **CA13** — Dado valor com mais de 2 casas ou vírgula em qualquer campo de dinheiro novo, então a resposta é `400` com erro no campo.
- [ ] **CA14** — Dado o lançamento ou o grupo de outro usuário, quando tento editar, estornar ou desfazer, então recebo `404`.

## Casos de borda

- Compra parcelada cuja primeira parcela caiu antes de `invoicesFrom`: só as parcelas visíveis nas faturas são oferecidas na tela; a API aceita qualquer parcela do grupo.
- Estorno com data anterior à da compra ou futura: aceito (o banco pode devolver depois); a data decide a fatura.
- Compra de uma assinatura mensal estornada em um mês: a cobrança gerada é lançamento comum, então estorna como qualquer outro e a assinatura segue gerando.
- Edição que deixa o valor igual a zero ou negativo: `400`, como na edição de lançamento comum.
- Estorno de compra em cartão **sem** dias (sem fatura): permitido, mesmo efeito de receita no cartão.
- Dados existentes: nenhuma compra ou parcela de hoje é alterada; o campo de ligação do estorno nasce nulo.

## Fora de escopo

- Controle de **limite** do cartão (limite, usado, disponível): o exemplo do adiantamento só pede que o pagamento parcial reduza a fatura.
- Escolher qual fatura abater no pagamento: continua da mais antiga para a mais nova.
- Estorno parcial (valor menor que o da compra).
- Apagar só uma parcela, ou "esta e as seguintes", sem apagar o grupo.
- Parcelar a fatura, antecipar ou quitar parcelas futuras, juros e multa.
- Importar fatura.
- Mudar o cartão de uma compra já lançada.

## Perguntas em aberto

1. **Categoria do estorno.** Pedir uma categoria de receita a cada estorno (como escrito na regra 8) ou criar uma categoria global "Estorno" por migration e usá-la por padrão? A migration só adicionaria uma linha, sem alterar dado existente.
2. **Estorno inteiro ou parcial.** Escrevi só inteiro (regra 8). Um estorno parcial (a loja devolveu só parte) é comum; entra agora ou fica para depois?
3. **Faturas pagas antigas no topo.** Com a ordem da mais antiga para a mais nova, as faturas pagas há meses ficam antes das em aberto. Mantenho assim, ou o filtro de situação já abre em "não pagas"?
4. **Selo "Paga adiantada"** (regra 17) está bom como texto, ou prefere outro nome?
