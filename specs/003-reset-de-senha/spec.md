# 003 — Reset de senha

**Status:** aprovada
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
- Depois do reset a pessoa vai para a tela de login: o link **não** abre sessão.
- Concluído o reset, o e-mail da conta recebe o aviso "sua senha foi alterada". Se o aviso não puder ser enviado, a troca vale mesmo assim.
- Concluir o reset zera o bloqueio de login por falhas daquele e-mail: quem errou a senha 3 vezes e redefiniu consegue entrar na hora.
- Limites (spec 005): no máximo 3 pedidos por e-mail e 5 por IP por hora.

## Critérios de aceite

- [ ] **CA1** — Dado um e-mail cadastrado, quando a pessoa pede o reset, então recebe um e-mail com o link.
- [ ] **CA2** — Dado um e-mail não cadastrado, quando alguém pede o reset, então a resposta na tela é idêntica à do CA1 e nenhum e-mail é enviado.
- [ ] **CA3** — Dado um link válido, quando a pessoa define uma senha nova, então consegue logar com ela e não com a antiga.
- [ ] **CA4** — Dada uma sessão aberta em outro dispositivo, quando a senha é trocada pelo reset, então essa sessão é encerrada na próxima renovação.
- [ ] **CA5** — Dado um link expirado, já usado ou substituído, quando a pessoa tenta usar, então vê que o link não vale mais e pode pedir outro.
- [ ] **CA6** — Dada uma conta ainda não verificada, quando a pessoa conclui o reset, então a conta fica verificada e ela consegue logar.
- [ ] **CA7** — Dado um reset concluído, então o e-mail da conta recebe o aviso de que a senha foi alterada.

## Casos de borda

- Pedir o reset várias vezes seguidas: limitado pela 005, e só o último link vale.
- Abrir o link e demorar mais de 30 minutos para enviar a senha nova: a senha não é trocada e a tela oferece pedir outro link.
- Login bloqueado por 3 senhas erradas: o reset desbloqueia.
- Provedor de e-mail fora do ar no pedido: a resposta continua a mesma, para não revelar a conta, e a pessoa pode pedir de novo.

## Fora de escopo

- Trocar a senha estando logado (spec 006)
- Perguntas de segurança, SMS ou 2FA
- Entrar logado direto pelo link de reset
- Login com Google (spec 004): esta spec não cria contas sem senha, só não impede que a 004 use o reset

## Decisões das perguntas em aberto

- Depois do reset: vai para o login, sem sessão.
- Aviso "sua senha foi alterada": enviado (CA7).
