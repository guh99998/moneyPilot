# 008 — Troca de e-mail

**Status:** entregue
**Branch:** feature/008-troca-de-email
**Depende de:** 001 (precisa estar entregue antes do envio, T5 em diante), 006

## Problema

O e-mail é o login e vai ser o único caminho para recuperar a senha. Hoje ele não pode ser trocado: quem mudou de e-mail ou se cadastrou com um endereço que não usa mais fica preso a ele.

## Objetivo

O usuário troca o e-mail da conta com segurança, sem risco de perder o acesso por um erro de digitação e sem que outra pessoa consiga sequestrar a conta.

## Regras de negócio

- A troca começa no perfil (006): a pessoa informa o e-mail novo e a senha atual.
- O sistema manda um link de confirmação para o **e-mail novo**. A troca só acontece quando esse link é clicado.
- Até a confirmação, o e-mail antigo continua valendo para login e recuperação de senha.
- O link vale por **24 horas** e só pode ser usado uma vez. Pedir de novo invalida o anterior.
- Depois da troca, o **e-mail antigo recebe um aviso** de que o e-mail da conta foi alterado.
- O e-mail novo não pode estar em uso por outra conta.
- Depois da troca, as outras sessões são encerradas, como na troca de senha (006).
- O e-mail novo é gravado com `strip()` e em minúsculas. As checagens de "em uso" e "é o atual" ignoram maiúsculas. Nenhum dado existente muda (a normalização geral fica com a 002).
- O link confirma **sem precisar estar logado**. Se o navegador tiver uma sessão da conta, ela continua aberta e as outras são encerradas.
- O perfil mostra o pedido pendente e permite reenviar o link.
- Senha atual errada conta no mesmo limite da troca de senha (5 a cada 15 min). Fora isso, no máximo 3 pedidos por usuário por hora.
- Se o aviso ao e-mail antigo falhar, a troca continua valendo e o erro vai para o log. Se o envio do link falhar, nada é gravado e a tela mostra o erro.
- O token de acesso passa a identificar o usuário pelo id, não pelo e-mail, para que a troca não deixe tokens órfãos.

## Critérios de aceite

- [ ] **CA1** — Dados um e-mail novo livre e a senha atual certa, quando a pessoa pede a troca, então um link é enviado para o e-mail novo e o e-mail da conta ainda não muda.
- [ ] **CA2** — Dado o link válido, quando é clicado, então o e-mail da conta passa a ser o novo, o login funciona com ele e o e-mail antigo recebe o aviso.
- [ ] **CA3** — Dada a senha atual errada, quando a pessoa pede a troca, então nada é enviado e aparece o erro.
- [ ] **CA4** — Dado um e-mail novo que já pertence a outra conta, quando a pessoa pede a troca, então a troca não acontece.
- [ ] **CA5** — Dado um link expirado, já usado ou substituído, quando é clicado, então a troca não acontece e a pessoa pode pedir outro.
- [ ] **CA6** — Dado um pedido de troca ainda não confirmado, então o login e o reset de senha continuam funcionando com o e-mail antigo.

## Casos de borda

- Pedir a troca para o próprio e-mail atual → erro no campo do e-mail novo
- E-mail novo igual ao atual com maiúsculas diferentes → mesmo erro
- Outra pessoa se cadastrar com o e-mail novo entre o pedido e a confirmação → a confirmação falha com erro de e-mail em uso
- Conta vinculada ao Google (004) trocando de e-mail → nada muda na 008; a 004 deve vincular pelo `sub` do Google, não pelo e-mail

## Fora de escopo

- Ter mais de um e-mail por conta
- Desfazer a troca pelo e-mail antigo (link de "não fui eu")

## Decisões das perguntas em aberto

- E-mail novo de outra conta: a tela diz isso claramente, no campo (a pessoa está logada e informou a senha).
- Link de "não fui eu" no aviso: segue fora de escopo.
