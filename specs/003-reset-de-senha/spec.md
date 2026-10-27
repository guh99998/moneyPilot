# 003 — Reset de senha

**Status:** rascunho
**Branch:** feature/003-reset-de-senha
**Roteiro:** D4 · **Depende de:** 001, 002

## Problema

Quem esquece a senha hoje perde o acesso à conta e aos dados financeiros, sem nenhum caminho de volta.

## Objetivo

A pessoa recupera o acesso pelo e-mail cadastrado, e qualquer sessão antiga deixa de valer depois da troca.

## Regras de negócio

- "Esqueci a senha" responde **sempre** a mesma mensagem, exista ou não a conta.
- O link vale por **30 minutos** e só pode ser usado uma vez. Pedir de novo invalida o anterior.
- A nova senha segue as mesmas regras do cadastro (mínimo de 8 caracteres).
- Trocar a senha **encerra todas as sessões** da conta, em todos os dispositivos.
- Concluir o reset também verifica a conta, se ela ainda não estava verificada — a pessoa acabou de provar que é dona do e-mail.
- Conta criada só pelo Google (004, sem senha) pode usar o reset para definir uma senha.

## Critérios de aceite

- [ ] **CA1** — Dado um e-mail cadastrado, quando a pessoa pede o reset, então recebe um e-mail com o link.
- [ ] **CA2** — Dado um e-mail não cadastrado, quando alguém pede o reset, então a resposta na tela é idêntica à do CA1 e nenhum e-mail é enviado.
- [ ] **CA3** — Dado um link válido, quando a pessoa define uma senha nova, então consegue logar com ela e não com a antiga.
- [ ] **CA4** — Dada uma sessão aberta em outro dispositivo, quando a senha é trocada pelo reset, então essa sessão é encerrada na próxima renovação.
- [ ] **CA5** — Dado um link expirado, já usado ou substituído, quando a pessoa tenta usar, então vê que o link não vale mais e pode pedir outro.

## Casos de borda

- Pedir o reset várias vezes seguidas (limitado pela 005)
- Abrir o link e demorar mais de 30 minutos para enviar a senha nova

## Fora de escopo

- Trocar a senha estando logado (tela de perfil)
- Perguntas de segurança, SMS ou 2FA

## Perguntas em aberto

- Depois do reset, a pessoa já entra logada ou vai para a tela de login?
- Enviar um e-mail avisando "sua senha foi alterada"? (recomendado: ajuda a perceber troca não autorizada)
