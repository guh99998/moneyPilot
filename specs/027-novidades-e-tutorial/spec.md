# 027 — Pop-up de primeiro contato

**Status:** rascunho (regras fechadas em 2026-11-05, aguardando aprovação)
**Branch:** feature/027-novidades-e-tutorial
**Depende de:** 013 (boas-vindas), 017 (painel de chamados)

## Problema

O cartão de primeiros passos da 013 fica no painel e é fácil de ignorar. A pessoa nova cai num app vazio sem um guia que chame a atenção na hora certa.

## Objetivo

Ao entrar no app, a pessoa nova vê um pop-up com os primeiros passos, pode navegar entre eles e escolher se quer vê-lo nas próximas entradas. O aviso de novidades e os tutoriais de cada tela ficam para uma spec separada.

## Regras de negócio

- **Pop-up (modal) de primeiro contato** com os botões **Avançar**, **Voltar** e **Dispensar**. Os passos são os da 013: criar a primeira conta bancária, lançar a primeira despesa ou receita, criar um orçamento.
- **Passo da conta bancária:** o pop-up explica e leva a pessoa para a tela de contas; o formulário não é duplicado dentro do pop-up. O mesmo vale para os outros passos.
- **Caixinha "Mostrar ao iniciar":** começa **marcada**. Marcada, o pop-up aparece toda vez que a pessoa entra; desmarcada, não volta sozinho. O valor é guardado no servidor, então vale em todos os aparelhos.
- **Dispensar** fecha o pop-up **só desta vez**: não altera a caixinha. Para não ver mais, a pessoa desmarca "Mostrar ao iniciar".
- **Fim automático:** quando os três passos estão feitos, o pop-up deixa de aparecer sozinho, mesmo com a caixinha marcada. O progresso dos passos vem da 013 e passo feito continua feito.
- **Quem dispensou o cartão da 013 não vê o pop-up.** Conta antiga (sem registro da 013) também não.
- **Reabrir:** a pessoa pode abrir o pop-up de novo pela ajuda, a qualquer momento, inclusive depois de desmarcar a caixinha. Aberto assim, mostra o estado atual dos passos.
- **Cartão de primeiros passos da 013:** sai do painel; o pop-up o substitui e usa o mesmo progresso.
- Estado e preferência valem para a conta, não para o aparelho; recurso de outra pessoa responde `404`.

## Critérios de aceite

- [ ] **CA1** — Dada uma conta nova com a caixinha marcada e passos pendentes, quando a pessoa entra no app, então vê o pop-up com o primeiro passo.
- [ ] **CA2** — Dado o pop-up, quando a pessoa usa Avançar e Voltar, então navega entre os três passos sem sair do pop-up, e Voltar não existe no primeiro passo.
- [ ] **CA3** — Dado o pop-up, quando a pessoa clica em Dispensar, então ele fecha e volta a aparecer na próxima entrada, enquanto a caixinha estiver marcada.
- [ ] **CA4** — Dado o pop-up, quando a pessoa desmarca "Mostrar ao iniciar", então ele não aparece mais nas próximas entradas, em nenhum aparelho.
- [ ] **CA5** — Dada uma conta com os três passos feitos, quando entra no app, então o pop-up não aparece, mesmo com a caixinha marcada.
- [ ] **CA6** — Dada uma conta que dispensou o cartão da 013, ou uma conta antiga, quando entra no app, então não vê o pop-up.
- [ ] **CA7** — Dado qualquer estado, quando a pessoa abre o pop-up pela ajuda, então ele abre com o estado atual dos passos, mesmo com a caixinha desmarcada ou os passos feitos.
- [ ] **CA8** — Dado o painel de uma conta nova, quando ele carrega, então o cartão de primeiros passos da 013 não aparece mais.
- [ ] **CA9** — Dado o pop-up aberto, quando a pessoa usa só o teclado ou um leitor de tela, então o foco fica dentro do pop-up, Esc o fecha como Dispensar e o foco volta para onde estava.

## Casos de borda

- Duas abas abertas: desmarcar a caixinha em uma vale na próxima entrada da outra.
- Falha de rede ao salvar a caixinha: a pessoa vê o erro e o valor anterior continua valendo.
- Conta nova que conclui o terceiro passo com o pop-up aberto: o pop-up mostra os três passos feitos e não volta sozinho depois.

## Fora de escopo

- Aviso de novidades por funcionalidade nova e tutorial de cada tela (spec futura, separada)
- Tour guiado destacando elementos das telas
- Formulários dentro do pop-up
- Passos além dos três da 013
- Painel para editar o texto dos passos

## Perguntas em aberto

- Nenhuma de regra. Ao aprovar, confirmar a decisão de que reabrir pela ajuda ignora o fim automático (CA7).
