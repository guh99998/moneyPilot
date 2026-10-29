# 022 — Marcos de uso

**Status:** rascunho (ideia para depois, ainda sem regras)
**Branch:** feature/022-marcos-de-uso
**Depende de:** 007 (notificações); talvez 018 (meus chamados)

## Problema

O app não reconhece quem o usa há tempo, e também não pergunta o que falta. A opinião de quem já usa há um mês ou um ano é a mais útil para decidir os próximos passos, e hoje ela só chega se a pessoa resolver abrir um chamado.

## Objetivo

Ao completar marcos de uso (1 mês, 1 ano...), a pessoa recebe um agradecimento e um convite para dizer o que está faltando, e o marco fica registrado como uma conquista no perfil.

## Regras de negócio

Nenhuma definida ainda. Ponto de partida, a confirmar:

- Marcos iniciais: 1 mês e 1 ano. Cada marco é mostrado uma vez por conta.
- Mensagem de exemplo: "Obrigado por usar o moneyPilot há 1 mês! Ficamos muito felizes em ajudar com suas finanças. Acha que algo está faltando?", com um botão para responder.
- O perfil mostra as conquistas já alcançadas.
- "Hoje" vem do `Clock`.

## Critérios de aceite

A escrever depois da conversa.

## Casos de borda

- Contas que já passaram de 1 mês ou 1 ano no dia do deploy (recebem todos os marcos de uma vez, só o maior ou nenhum?)
- Conta criada num dia 31 (quando faz "1 mês"?)
- Conta criada e esquecida, que só volta meses depois
- Conta excluída (012) apaga as conquistas

## Fora de escopo

A definir. Candidatos, a confirmar: ranking entre pessoas, conquistas por comportamento (ex.: "3 meses dentro do orçamento"), compartilhar em rede social.

## Perguntas em aberto

- O tempo conta desde a criação da conta ou desde o primeiro lançamento? Conta parada há meses também ganha o marco?
- Onde aparece: notificação no app (007), e-mail ou os dois?
- **Para onde vai a resposta?** Um formulário externo (Google Forms) manda os dados da pessoa para fora do app e não se liga à conta. Proposta: usar o próprio "Falar com o suporte" com uma categoria "Sugestão", que já cai no painel de chamados (017) e na lista "Meus chamados" (018).
- "Presentinho": só a mensagem e o selo, ou algo mais (ex.: um tema, uma imagem para baixar)?
- Quais marcos além de 1 mês e 1 ano?
