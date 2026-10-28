# 014 — Autenticação em dois fatores

**Status:** rascunho
**Branch:** feature/014-autenticacao-em-dois-fatores
**Depende de:** 003 (reset de senha), 004 (login com Google), 011 (API para automação)

## Problema

Num app de finanças, a senha é hoje o único fator. Quem tiver a senha (vazada, repetida em outro site) entra na conta.

## Objetivo

A pessoa que quiser liga um segundo fator, e quem tiver só a senha dela não consegue entrar.

## Regras de negócio

- **Opcional (proposta):** cada pessoa liga no perfil. Ninguém é obrigado.
- **Método (proposta):** aplicativo autenticador (Google Authenticator, Authy, 1Password...) com código de 6 dígitos que muda a cada 30 segundos (TOTP). Implementado sem dependência nova.
- **Ligar:** o perfil mostra um QR code e a chave em texto; a pessoa confirma com um código do app antes de ativar.
- **Códigos de recuperação:** ao ligar, a pessoa recebe 10 códigos de uso único para quando perder o celular. Só aparecem uma vez.
- **Entrar:** depois da senha certa, o app pede o código. Sem o código, não há sessão.
- **Google:** quem entra pelo Google já tem o segundo fator do próprio Google; o código do app não é pedido (proposta).
- **Reset de senha (003):** não desliga nem pula o 2FA.
- **Desligar:** pede a senha atual e um código válido.
- **Limites:** códigos errados entram num limite próprio (ex.: 5 em 15 minutos por conta).
- **API (011):** o login pela API também pede o código.

## Critérios de aceite

- [ ] **CA1** — Dada uma conta com 2FA ligado, quando entra com a senha certa e sem o código, então não recebe sessão.
- [ ] **CA2** — Dado o código certo, quando o informa depois da senha, então entra.
- [ ] **CA3** — Dado um código de recuperação, quando o usa no lugar do código, então entra, e esse código não vale de novo.
- [ ] **CA4** — Dado o 2FA desligado, quando entra com a senha, então nada muda em relação a hoje.
- [ ] **CA5** — Dada a ativação, quando a pessoa não confirma com um código válido, então o 2FA não é ligado.
- [ ] **CA6** — Dado um reset de senha, quando a pessoa entra com a senha nova, então o código continua sendo pedido.

## Casos de borda

- Relógio do celular adiantado ou atrasado: aceitar o código anterior e o seguinte (±30 s)
- O mesmo código usado duas vezes dentro da janela: recusado
- Pessoa que perdeu o celular e os códigos de recuperação: só o suporte resolve (010) — como?
- Conta só com Google que liga o 2FA e depois cria senha

## Fora de escopo

- Código por SMS (custo e provedor novo)
- Chaves de segurança físicas e passkeys
- Lembrar o aparelho por 30 dias

## Perguntas em aberto

- Só aplicativo autenticador, ou também código por e-mail? O e-mail é mais fácil para o usuário, mas é mais fraco: é o mesmo canal do reset de senha.
- Quem entra com o Google pula o código, como proposto?
- Obrigatório para alguém (ex.: você, como administrador) ou sempre opcional?
- O que fazer quando a pessoa perde o celular e os códigos de recuperação? (Sugestão: suporte confirma a identidade por e-mail e desliga o 2FA manualmente.)
- O 2FA pela API (011) dificulta a automação com IA. Aceita, ou contas com 2FA ficam sem acesso pela API?
