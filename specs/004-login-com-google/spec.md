# 004 — Login com Google

**Status:** rascunho
**Branch:** feature/004-login-com-google
**Roteiro:** D5 · **Depende de:** 002

## Problema

Criar conta com e-mail e senha, e depois verificar o e-mail, é atrito para quem só quer experimentar o app.

## Objetivo

A pessoa entra com a conta Google em um clique, na primeira vez e nas seguintes.

## Regras de negócio

- E-mail do Google **ainda não cadastrado**: cria a conta já verificada, sem senha, com o nome vindo do Google.
- E-mail do Google **já cadastrado**: entra na conta existente e a vincula ao Google. Os dados continuam os mesmos.
- Só aceita contas Google com e-mail verificado pelo próprio Google.
- Conta sem senha que tenta o login por senha recebe mensagem dizendo para usar "Entrar com Google" ou definir uma senha pelo reset (003).
- A sessão criada pelo Google é igual à do login por senha (mesma duração e renovação).

## Critérios de aceite

- [ ] **CA1** — Dada uma conta Google com e-mail que não existe no app, quando a pessoa entra com Google, então a conta é criada, verificada, e ela cai no painel.
- [ ] **CA2** — Dada uma conta Google com o mesmo e-mail de uma conta existente, quando a pessoa entra com Google, então acessa a conta existente com todos os dados.
- [ ] **CA3** — Dada uma conta criada pelo Google, quando a pessoa tenta logar por senha, então recebe a orientação correta.
- [ ] **CA4** — Dado um token do Google inválido, expirado ou emitido para outro app, quando é enviado ao backend, então o login é recusado.

## Casos de borda

- Conta existente ainda não verificada (002) entrando pelo Google: passa a ser verificada
- Pessoa que muda o e-mail da conta Google depois de vincular

## Fora de escopo

- Outros provedores (Apple, Microsoft, GitHub)
- Desvincular o Google da conta
- Importar foto de perfil

## Perguntas em aberto

- Vincular automaticamente pelo e-mail (proposto acima) ou pedir a senha atual na primeira vez, por segurança extra?
- O botão aparece no cadastro e no login, ou só no login?

## Notas para o plano

Decidido no roteiro: ID token validado no backend (assinatura, audience, expiração). O client ID do Google é tarefa sua e não é segredo.
