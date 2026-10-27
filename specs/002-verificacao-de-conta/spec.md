# 002 — Verificação de conta

**Status:** rascunho
**Branch:** feature/002-verificacao-de-conta
**Roteiro:** D3 · **Depende de:** 001

## Problema

Hoje qualquer pessoa cria conta com um e-mail que não é dela. Isso abre espaço para contas falsas e torna o reset de senha (003) inseguro, porque o e-mail cadastrado pode nem ser do usuário.

## Objetivo

Só entra no app quem provou ser dono do e-mail cadastrado.

## Regras de negócio

- Conta nova nasce **não verificada** e recebe um e-mail com link de verificação.
- Conta não verificada **não loga**: o login é recusado com mensagem que explica o motivo e oferece reenviar o link.
- O link vale por **24 horas** e só pode ser usado uma vez. Reenviar gera um link novo e invalida o anterior.
- **Usuários que já existem em produção entram como verificados.** Ninguém que usa o app hoje pode ficar trancado para fora.
- O reenvio do link responde sempre a mesma mensagem, exista ou não a conta, para não revelar quais e-mails estão cadastrados.

## Critérios de aceite

- [ ] **CA1** — Dado um cadastro novo, quando ele é concluído, então a conta fica não verificada e um e-mail com o link é enviado.
- [ ] **CA2** — Dada uma conta não verificada, quando a pessoa tenta logar com a senha certa, então o login é recusado com a explicação e a opção de reenviar.
- [ ] **CA3** — Dado um link válido, quando a pessoa clica, então a conta é verificada e ela consegue logar.
- [ ] **CA4** — Dado um link expirado, já usado ou substituído por um reenvio, quando a pessoa clica, então vê que o link não vale mais e pode pedir outro.
- [ ] **CA5** — Dados os usuários cadastrados antes desta feature, quando ela entra no ar, então todos continuam logando normalmente.
- [ ] **CA6** — Dado um e-mail não cadastrado, quando alguém pede reenvio, então a resposta é idêntica à de um e-mail cadastrado.

## Casos de borda

- Clicar no link duas vezes (a segunda mostra "conta já verificada", não um erro)
- Clicar no link estando logado em outra conta
- E-mail cadastrado com maiúsculas e minúsculas diferentes

## Fora de escopo

- Trocar o e-mail da conta
- Excluir automaticamente contas que nunca verificaram
- Verificação por código numérico em vez de link

## Perguntas em aberto

- O cadastro com e-mail já existente hoje responde "e-mail já cadastrado", o que revela quem tem conta. Manter assim ou passar a responder sempre "verifique seu e-mail"?
- Depois de verificar, a pessoa já entra logada ou vai para a tela de login?
- Hoje `Fulano@x.com` e `fulano@x.com` seriam contas diferentes. Normalizar o e-mail para minúsculas entra nesta spec?
