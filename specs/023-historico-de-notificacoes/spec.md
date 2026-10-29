# 023 — Histórico de notificações

**Status:** rascunho (ideia para depois, ainda sem regras)
**Branch:** feature/023-historico-de-notificacoes
**Depende de:** 007 (notificações), 018 (meus chamados)

## Problema

O sino da 007 só mostra alertas calculados a partir da situação atual. Um aviso de evento, como "seu chamado foi resolvido" (018), some sozinho depois de um prazo ou ao ser dispensado, e a pessoa que não entrou no app a tempo não tem como saber que ele existiu.

## Objetivo

As notificações ficam gravadas, com lida/não lida, e a pessoa consulta o histórico de tudo o que o sino já mostrou.

## Regras de negócio

Nenhuma definida ainda. Ponto de partida, a confirmar:

- Notificações de evento (como a mudança de status de um chamado) passam a ser gravadas no banco, por conta.
- Cada notificação tem lida/não lida, e o contador do sino conta só as não lidas.
- Existe uma tela de histórico com as notificações antigas.
- **Isto muda a regra da 007**, que diz que "não há histórico nem lida/não lida". A 007 continua como registro histórico; a mudança é registrada aqui e no README.

## Critérios de aceite

A escrever depois da conversa.

## Casos de borda

- Alertas calculados da 007 (vencimentos, orçamento): entram no histórico ou continuam só calculados?
- Notificações do 018 que já existem como alerta calculado quando esta spec entrar
- Conta excluída (012) apaga o histórico
- Pessoa com muitas notificações: paginação e limite de retenção

## Fora de escopo

A definir. Candidatos, a confirmar: notificação por e-mail ou push, preferências por tipo de aviso.

## Perguntas em aberto

- Os alertas calculados da 007 também são gravados, ou só os de evento?
- Por quanto tempo o histórico é guardado?
- "Marcar tudo como lida" entra na primeira versão?
