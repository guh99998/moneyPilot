# 027 — Pop-up de primeiro contato

**Status:** em revisão (implementada; falta a checagem manual do Gustavo, T10)
**Branch:** feature/027-novidades-e-tutorial
**Depende de:** 013 (boas-vindas), 017 (painel de chamados)

## Problema

O cartão de primeiros passos da 013 fica no painel e é fácil de ignorar. A pessoa nova cai num app vazio sem um guia que chame a atenção na hora certa.

## Objetivo

Ao fazer login, a pessoa nova vê um pop-up com os primeiros passos, pode navegar entre eles e escolher se quer vê-lo nos próximos logins. O aviso de novidades e os tutoriais de cada tela ficam para uma spec separada.

## Regras de negócio

- **Pop-up (modal) de primeiro contato** com os botões **Avançar**, **Voltar** e **Dispensar**. Os passos são os da 013: criar a primeira conta bancária, lançar a primeira despesa ou receita, criar um orçamento.
- **Passo da conta bancária:** o pop-up explica e leva a pessoa para a tela de contas; o formulário não é duplicado dentro do pop-up. O mesmo vale para os outros passos.
- **Guia depois do pop-up (decidido em 2026-11-05):** ao seguir o link de um passo, começa um guia. Nas telas Contas, Lançamentos e Orçamentos aparece uma **faixa** no topo com "Passo N de 3", o que fazer e o nome do botão que abre o formulário. A faixa mostra sempre o **primeiro passo pendente**; se a pessoa estiver em outra tela, traz o link para a do passo.
- **Avanço automático:** quando a pessoa conclui o passo (cria a conta, o lançamento ou o orçamento), o app a leva sozinho para a tela do próximo passo pendente, com um aviso. Concluídos os três, o guia termina com um aviso e a faixa some.
- **Dispensar tutorial:** botão da faixa. Encerra o guia **só desta vez**: não grava nada no servidor, não altera a caixinha, e o pop-up volta no próximo login se a caixinha estiver marcada. O guia vale só na sessão do navegador e não sobrevive a um novo login.
- **"Entrar" é fazer login**, com senha ou Google. F5, aba nova e sessão renovada sozinha não reabrem o pop-up; quem fica logado só o vê de novo quando precisar entrar outra vez, inclusive depois de a sessão expirar.
- **O pop-up abre no primeiro passo pendente.** Sem pendência (reabertura pela ajuda), abre no passo 1.
- **Caixinha "Mostrar ao iniciar":** começa **marcada**. Marcada, o pop-up aparece a cada login; desmarcada, não volta sozinho. O valor é guardado no servidor, então vale em todos os aparelhos.
- **Dispensar** fecha o pop-up **só desta vez**: não altera a caixinha. Para não ver mais, a pessoa desmarca "Mostrar ao iniciar".
- **Fim automático:** quando os três passos estão feitos, o pop-up deixa de aparecer sozinho, mesmo com a caixinha marcada. O progresso dos passos vem da 013 e passo feito continua feito.
- **Quem dispensou o cartão da 013 não vê o pop-up.** Conta antiga (sem registro da 013) também não.
- **Reabrir:** a pessoa pode abrir o pop-up de novo pela ajuda, a qualquer momento, inclusive depois de desmarcar a caixinha. Aberto assim, mostra o estado atual dos passos. Em conta antiga, ou de quem dispensou o cartão ou concluiu os passos, os passos são calculados na hora, sem gravar nada, e a caixinha não aparece, porque não teria efeito.
- **Cartão de primeiros passos da 013:** sai do painel; o pop-up o substitui e usa o mesmo progresso.
- Estado e preferência valem para a conta, não para o aparelho. As rotas não têm id: sempre valem para a conta autenticada.

## Critérios de aceite

- [ ] **CA1** — Dada uma conta nova com a caixinha marcada e passos pendentes, quando a pessoa faz login, então vê o pop-up no primeiro passo pendente.
- [ ] **CA2** — Dado o pop-up, quando a pessoa usa Avançar e Voltar, então navega entre os três passos sem sair do pop-up, e Voltar não existe no primeiro passo.
- [ ] **CA3** — Dado o pop-up, quando a pessoa clica em Dispensar, então ele fecha e volta a aparecer no próximo login, enquanto a caixinha estiver marcada.
- [ ] **CA4** — Dado o pop-up, quando a pessoa desmarca "Mostrar ao iniciar", então ele não aparece mais nos próximos logins, em nenhum aparelho.
- [ ] **CA5** — Dada uma conta com os três passos feitos, quando faz login, então o pop-up não aparece, mesmo com a caixinha marcada.
- [ ] **CA6** — Dada uma conta que dispensou o cartão da 013, ou uma conta antiga, quando faz login, então não vê o pop-up.
- [ ] **CA7** — Dado qualquer estado, quando a pessoa abre o pop-up pela ajuda, então ele abre com o estado atual dos passos, mesmo com a caixinha desmarcada ou os passos feitos, e sem a caixinha quando ela não teria efeito (conta antiga, cartão dispensado ou passos concluídos).
- [ ] **CA8** — Dado o painel de uma conta nova, quando ele carrega, então o cartão de primeiros passos da 013 não aparece mais.
- [ ] **CA10** — Dada uma pessoa que seguiu o link de um passo no pop-up, quando abre Contas, Lançamentos ou Orçamentos, então vê a faixa com o primeiro passo pendente, o que fazer e o botão "Dispensar tutorial".
- [ ] **CA11** — Dada a faixa de um passo, quando a pessoa conclui esse passo, então vai sozinha para a tela do próximo passo pendente; ao concluir o terceiro, o guia termina e a faixa some.
- [ ] **CA12** — Dada a faixa, quando a pessoa clica em "Dispensar tutorial", então a faixa some e não volta nas outras telas, nada é gravado no servidor, e o pop-up volta no próximo login (caixinha marcada).
- [ ] **CA9** — Dado o pop-up aberto, quando a pessoa usa só o teclado ou um leitor de tela, então o foco fica dentro do pop-up, Esc o fecha como Dispensar e o foco volta para onde estava.

## Casos de borda

- Duas abas abertas: desmarcar a caixinha em uma vale no próximo login da outra.
- Falha de rede ao salvar a caixinha: a pessoa vê o erro e o valor anterior continua valendo.
- Conclui o terceiro passo em outra aba com o pop-up aberto: ele mostra o estado de quando abriu e não volta no próximo login.
- Edição ou exclusão durante o guia não avança nem encerra: só concluir um passo novo avança.
- Link da ajuda aberto sem sessão: depois do login a pessoa vai para a Visão geral, sem reabrir o pop-up sozinho.

## Fora de escopo

- Aviso de novidades por funcionalidade nova e tutorial de cada tela (spec futura, separada)
- Tour guiado que escurece a tela e destaca elementos (o guia é só a faixa de instrução)
- Formulários dentro do pop-up ou da faixa (o botão abre o formulário que a tela já tem)
- Passos além dos três da 013
- Painel para editar o texto dos passos

## Perguntas em aberto

- Nenhuma de regra. Ao aprovar, confirmar a decisão de que reabrir pela ajuda ignora o fim automático (CA7).
