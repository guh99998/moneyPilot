# 028 — Ajuste dos itens da fatura

**Status:** em implementação (estorno retirado do escopo em 2026-11-12)
**Branch:** feature/028-itens-da-fatura
**Depende de:** 016 (fatura do cartão). Muda a regra 9 e a regra 28 da 016.

## Problema

Os itens de uma fatura são lançamentos no cartão, mas a pessoa quase não consegue ajustá-los:

- Uma **parcela** de compra parcelada não se edita (`409`): para corrigir um valor ou uma descrição é preciso apagar o grupo inteiro e lançar de novo.
- Na tela Faturas, as faturas aparecem da mais nova para a mais antiga, então as atrasadas ficam escondidas embaixo.
- Pagar uma parte da fatura antes de ela fechar já funciona (o pagamento abate da fatura mais antiga), mas a tela não deixa isso claro.

## Objetivo

Nos itens da fatura do cartão, a pessoa pode **editar** uma compra (inclusive parcela, uma ou as seguintes) e **apagar** a compra com um aviso claro. A tela Faturas lista da mais antiga para a mais nova, e o pagamento adiantado de um valor qualquer fica visível e explicado.

Exemplo: compra de 10 parcelas de R$ 83,33. A pessoa corrige a categoria da parcela 4/10 e das seguintes. Depois adianta R$ 50,00 da fatura aberta e vê "pago R$ 50,00, falta R$ 33,33".

## Regras de negócio

**Editar um item da fatura**

1. Vale para despesa ou receita em conta cartão (com ou sem dias). Edita descrição, categoria, data e valor. A conta não muda por aqui.
2. A **parcela** deixa de dar `409` (muda a regra 9 da 016). Continua no grupo; `installmentNumber` e `installmentTotal` não mudam.
3. Em parcela, a pessoa escolhe o alcance: **só esta parcela** ou **esta e as seguintes**. No segundo caso, descrição, categoria e valor valem para todas as parcelas do grupo com número maior ou igual; a data só muda a da parcela editada (as outras mantêm a sua).
4. A parcela editada pode mudar de fatura (a data decide); o total das faturas é recalculado em leitura, como sempre.
5. Fatura já paga que muda de total não é reaberta nem corrigida: vale o fundo único da regra 6 da 016. Total menor vira crédito para as seguintes; total maior deixa o excedente a pagar.
6. Lançamento de transferência, gerado por baixa de título e cobrança de assinatura mensal continuam como hoje (transferência e baixa dão `409`; a cobrança gerada já era editável, regra 33).

**Apagar um item da fatura**

7. Apagar continua como hoje (apaga o lançamento; em parcela, o grupo todo, regra 9 da 016). A tela diz isso antes de confirmar.

**Tela Faturas**

8. A lista única de faturas passa a ordenar por **vencimento da mais antiga para a mais nova** como padrão (muda a regra 28 da 016) e **abre filtrada em "Não pagas"** (todas menos as pagas; a aberta paga adiantada continua aparecendo). O cabeçalho continua ordenando por vencimento, total, falta pagar, cartão e situação, e o padrão de cada coluna é o da primeira vez que se clica.
9. A **fatura atual em destaque** mostra como valor principal o que **falta pagar** quando já houve pagamento (com o total da fatura e o já pago ao lado); sem pagamento, mostra o total. O resumo não muda.

**Pagamento adiantado**

10. Sem mudança de regra: o pagamento é a transferência para o cartão de **qualquer valor**, abate da fatura mais antiga para a mais nova (regra 6 da 016). O adiantamento de R$ 50 numa fatura de R$ 500 a deixa em R$ 450.
11. A tela deixa isso claro: o diálogo "Pagar fatura" diz qual fatura vai receber o valor, aceita valor menor que o da fatura e mostra "vai ficar em R$ 450,00" antes de confirmar. A fatura com pagamento parcial mostra "pago R$ 50,00, falta R$ 450,00".
12. Fatura aberta totalmente paga mostra o selo **"Paga adiantada"** (ainda recebe compras até o fechamento; se entrar compra nova, volta a mostrar o que falta).

**Contrato**

13. Os valores de dinheiro seguem `@Digits(integer = 17, fraction = 2)` e entram no `MoneyScaleIntegrationTest`. Recurso de outro usuário responde `404`. A mudança de rota entra no contrato OpenAPI.

## Critérios de aceite

- [ ] **CA1** — Dado uma parcela 4/10 de uma compra no cartão, quando edito a descrição só dela, então só a parcela 4 muda e continua `4/10` no mesmo grupo.
- [ ] **CA2** — Dado o mesmo grupo, quando edito categoria e valor com alcance "esta e as seguintes", então as parcelas 4 a 10 mudam, 1 a 3 ficam iguais e a data só muda a da parcela editada.
- [ ] **CA3** — Dado uma parcela editada para uma data de outro ciclo, quando consulto as faturas, então o total de cada fatura reflete a nova data.
- [ ] **CA4** — Dado uma transferência ou lançamento de baixa de título, quando tento editar, então continua `409`.
- [ ] **CA5** — Dado uma fatura de R$ 500,00 sem faturas anteriores em aberto, quando pago R$ 50,00, então a fatura mostra pago R$ 50,00 e falta R$ 450,00.
- [ ] **CA6** — Dado uma fatura aberta paga por inteiro, quando abro a tela Faturas, então ela mostra "Paga adiantada".
- [ ] **CA7** — Dado três faturas de vencimentos diferentes, quando abro a tela Faturas sem ordenar, então a lista vem da mais antiga para a mais nova.
- [ ] **CA8** — Dado valor com mais de 2 casas ou vírgula na edição, então a resposta é `400` com erro no campo.
- [ ] **CA9** — Dado o lançamento ou o grupo de outro usuário, quando tento editar, então recebo `404`.
- [ ] **CA10** — Dado uma fatura aberta com pagamento parcial, quando abro a tela Faturas, então a fatura atual mostra o que falta pagar como valor principal, com o total e o já pago ao lado.

## Casos de borda

- Compra parcelada cuja primeira parcela caiu antes de `invoicesFrom`: só as parcelas visíveis nas faturas são oferecidas na tela; a API aceita qualquer parcela do grupo.
- Edição que deixa o valor igual a zero ou negativo: `400`, como na edição de lançamento comum.
- Dados existentes: nenhuma compra ou parcela de hoje é alterada; não há migration.

## Fora de escopo

- **Estorno de compra no cartão** (receita ligada à compra, escopos, desfazer): retirado em 2026-11-12 por ser complexo; vira spec própria no futuro. O código já feito está guardado na branch `feature/refund-parked`. Até lá, compra cancelada continua só podendo ser apagada.
- Controle de **limite** do cartão (limite, usado, disponível): o exemplo do adiantamento só pede que o pagamento parcial reduza a fatura.
- Escolher qual fatura abater no pagamento: continua da mais antiga para a mais nova.
- Apagar só uma parcela, ou "esta e as seguintes", sem apagar o grupo.
- Parcelar a fatura, antecipar ou quitar parcelas futuras, juros e multa.
- Importar fatura.
- Mudar o cartão de uma compra já lançada.

## Decisões

1. **Faturas pagas antigas:** a lista abre filtrada em "Não pagas".
2. **Selo:** "Paga adiantada".
3. **Estorno:** fora desta spec; decisões já tomadas para a futura (categoria global "Estorno", só estorno inteiro, data pela maior entre a informada e a da parcela, desfazer apagando a receita) ficam registradas na branch guardada.
