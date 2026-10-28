# 018 — Meus chamados

**Status:** rascunho (ideia para conversar, ainda sem regras)
**Branch:** feature/018-meus-chamados
**Depende de:** 010 (falar com o suporte), 017 (painel de chamados)

## Problema

Desde a 017, todo chamado fica gravado com status, mas só o administrador enxerga. Quem abriu o chamado não sabe se ele foi recebido, se alguém está olhando ou se já foi resolvido, e só descobre quando chega um e-mail.

## Objetivo

A pessoa logada vê, na própria tela "Falar com o suporte", os chamados que abriu e o andamento de cada um.

## Regras de negócio

Nenhuma definida ainda. Ponto de partida, a confirmar:

- A lista fica na tela `#/suporte`, junto do formulário, só para quem está logado.
- Cada chamado mostra referência, categoria, data, status (`Aberto`, `Em andamento`, `Resolvido`) e quando o status mudou pela última vez.
- A pessoa vê só os chamados da própria conta. Chamado de outra conta não aparece, e a rota responde `404`.

## Critérios de aceite

A escrever depois da conversa.

## Casos de borda

- Chamado aberto sem login, com o mesmo e-mail da conta
- Chamado aberto antes da 017 (só existe no e-mail)
- Chamado com "e-mail não enviado" (a pessoa deve ver isso?)
- Conta que trocou de e-mail (008) depois de abrir o chamado
- Administrador abrindo os próprios chamados

## Fora de escopo

A definir. Candidatos, a confirmar: conversa dentro do app (respostas e réplicas), anexos, avaliação do atendimento.

## Perguntas em aberto

- Chamados abertos **sem login** entram na lista? Mostrar pelo e-mail é arriscado: qualquer um pode abrir um chamado com o e-mail de outra pessoa. Proposta: só os chamados ligados à conta, ou também os sem login cujo e-mail bate com o e-mail **verificado** da conta.
- A pessoa vê a mensagem que enviou, ou só o resumo?
- Mudança de status gera aviso (notificação da 007 ou e-mail)? Sem aviso, a pessoa precisa entrar para conferir.
- A resposta do suporte continua só por e-mail? Se sim, a tela deve dizer "respondemos no seu e-mail".
- A pessoa pode reabrir um chamado resolvido, ou abre um novo?
