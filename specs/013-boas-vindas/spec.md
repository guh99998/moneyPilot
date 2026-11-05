# 013 — Boas-vindas

**Status:** rascunho (decisões fechadas com o Gustavo; aguardando aprovação)
**Branch:** feature/013-boas-vindas
**Depende de:** 001 (e-mail), 002 (verificação), 004 (login com Google)

## Problema

Quem cria a conta cai num painel vazio, sem saber por onde começar. Não há nenhum gesto de recepção.

## Objetivo

A pessoa se sente recebida e sabe quais são os primeiros passos para o app ser útil.

## Regras de negócio

- **Quando:** só depois que a conta está verificada (antes disso, o e-mail pode não ser da pessoa):
  - cadastro por e-mail: ao confirmar o link de verificação;
  - Google: ao criar a conta no primeiro login.
  - Conta não verificada que entra pelo Google (004) também conta como conta nova.
- **Os dois canais:** a pessoa recebe o e-mail de boas-vindas **e** vê o cartão de primeiros passos no painel.
- **E-mail de boas-vindas:** curto, em português, com os primeiros passos e o link do app. Enviado uma vez por conta. O texto proposto está no fim desta spec e o Gustavo ajusta antes da aprovação.
- **Cartão de primeiros passos no painel:** cada passo é marcado quando feito:
  1. criar a primeira conta bancária;
  2. lançar a primeira despesa ou receita;
  3. criar um orçamento.

  O cartão some quando os três estão feitos ou quando a pessoa clica em "Dispensar". A dispensa e o progresso valem para a conta, em qualquer aparelho.
- **Passo feito continua feito:** se a pessoa apagar depois a conta bancária (ou o lançamento, ou o orçamento) que cumpriu o passo, o passo permanece marcado.
- **Contas que já existem:** ficam de fora. Não recebem o e-mail nem o cartão.
- Falha no envio do e-mail não impede a verificação nem o login, e o e-mail não é reenviado.

## Critérios de aceite

- [ ] **CA1** — Dada uma conta nova por e-mail, quando o link de verificação é confirmado, então chega um e-mail de boas-vindas.
- [ ] **CA2** — Dada uma conta nova pelo Google, quando ela é criada, então chega o e-mail de boas-vindas.
- [ ] **CA3** — Dada uma conta que já recebeu as boas-vindas, quando é verificada de novo ou entra outra vez, então não recebe outro e-mail.
- [ ] **CA4** — Dada uma conta nova, quando abre o painel, então vê o cartão de primeiros passos, com cada passo feito marcado.
- [ ] **CA5** — Dado o cartão, quando a pessoa dispensa ou conclui os passos, então ele não aparece mais, em nenhum aparelho.
- [ ] **CA6** — Dada uma conta antiga, quando abre o painel, então não vê o cartão e não recebeu o e-mail.
- [ ] **CA7** — Dado um passo já marcado, quando a pessoa apaga o que o cumpriu, então o passo continua marcado.
- [ ] **CA8** — Dada uma falha do provedor de e-mail nas boas-vindas, quando a conta é verificada, então a verificação e o login valem e o e-mail não é reenviado.

## Casos de borda

- Clicar duas vezes no link de verificação: um só e-mail.
- Falha do provedor de e-mail: a verificação vale e o e-mail não é reenviado.
- Conta que apaga a única conta bancária depois: o passo continua marcado.
- Conta nova que dispensa o cartão antes de concluir: não volta a aparecer.
- Outra pessoa não vê nem altera o cartão de uma conta que não é a dela (`404`).

## Fora de escopo

- Tour guiado pelas telas
- Aviso de novidades e tutoriais de cada tela (spec 027)
- Sequência de e-mails (dicas no dia 3, dia 7...)
- Dados de exemplo pré-carregados
- Passos extras além dos três (categoria, conta a pagar)

## Perguntas em aberto

- Nenhuma de regra. Falta só o Gustavo aprovar o texto do e-mail abaixo.

## Texto proposto do e-mail (para ajuste)

**Assunto:** Bem-vindo ao moneyPilot

> Olá, {nome}!
>
> Sua conta no moneyPilot está pronta. Para o app ficar útil logo de cara, sugerimos três passos:
>
> 1. Crie sua primeira conta bancária.
> 2. Lance sua primeira despesa ou receita.
> 3. Crie um orçamento.
>
> [Abrir o moneyPilot]
>
> O moneyPilot é gratuito e seus dados são só seus. Se precisar de ajuda, use "Falar com o suporte" dentro do app.
