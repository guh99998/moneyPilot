# 013 — Boas-vindas

**Status:** rascunho
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
- **E-mail de boas-vindas (proposta):** curto, em português, com os primeiros passos e o link do app. Enviado uma vez por conta.
- **Primeiro acesso no app (proposta):** um cartão no painel com os primeiros passos, cada um marcado quando feito:
  1. criar a primeira conta bancária;
  2. lançar a primeira despesa ou receita;
  3. criar um orçamento.
  
  O cartão some quando os três estão feitos ou quando a pessoa clica em "Dispensar".
- **Contas que já existem:** não recebem o e-mail nem o cartão.
- Falha no envio do e-mail não impede a verificação nem o login.

## Critérios de aceite

- [ ] **CA1** — Dada uma conta nova por e-mail, quando o link de verificação é confirmado, então chega um e-mail de boas-vindas.
- [ ] **CA2** — Dada uma conta nova pelo Google, quando ela é criada, então chega o e-mail de boas-vindas.
- [ ] **CA3** — Dada uma conta que já recebeu as boas-vindas, quando é verificada de novo ou entra outra vez, então não recebe outro e-mail.
- [ ] **CA4** — Dada uma conta nova, quando abre o painel, então vê o cartão de primeiros passos, com cada passo feito marcado.
- [ ] **CA5** — Dado o cartão, quando a pessoa dispensa ou conclui os passos, então ele não aparece mais, em nenhum aparelho.
- [ ] **CA6** — Dada uma conta antiga, quando abre o painel, então não vê o cartão.

## Casos de borda

- Clicar duas vezes no link de verificação: um só e-mail
- Falha do provedor de e-mail: a verificação vale e o e-mail não é reenviado
- Conta que apaga a única conta bancária depois: o passo continua marcado (proposta) ou volta a aparecer?

## Fora de escopo

- Tour guiado pelas telas
- Sequência de e-mails (dicas no dia 3, dia 7...)
- Dados de exemplo pré-carregados

## Perguntas em aberto

- E-mail, cartão no app ou os dois?
- Os três primeiros passos propostos servem? Quer incluir "criar uma categoria" ou "cadastrar uma conta a pagar"?
- O texto do e-mail: você quer escrever, ou eu proponho e você ajusta?
- Contas criadas antes desta spec ficam mesmo de fora?
