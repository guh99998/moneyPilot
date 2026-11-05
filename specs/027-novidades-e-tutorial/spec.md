# 027 — Novidades e tutorial

**Status:** rascunho (ideia para depois, ainda sem regras)
**Branch:** feature/027-novidades-e-tutorial
**Depende de:** 013 (boas-vindas), 017 (painel de chamados)

## Problema

Quando uma funcionalidade nova sai, quem já usa o app só descobre se for procurar. Não há aviso nem explicação de como usar.

## Objetivo

Ao entrar no app, a pessoa vê um aviso das novidades que ainda não viu e pode abrir um tutorial básico de cada uma. Os tutoriais ficam disponíveis para consulta depois.

## Regras de negócio

- Ideia inicial, a definir na conversa da spec:
  - aviso mostrado uma vez por pessoa e por novidade, com o "já vi" guardado no servidor (vale em todos os aparelhos, como a dispensa de alertas);
  - as novidades vêm de um arquivo versionado no repositório, sem painel de edição;
  - o cartão de primeiros passos da 013 pode virar o primeiro tutorial.

## Critérios de aceite

- A definir.

## Casos de borda

- Conta nova: não deve receber um aviso de tudo que já existia antes dela (a 013 cuida do primeiro contato).

## Fora de escopo

- A definir.

## Perguntas em aberto

- Formato do tutorial: passos destacando a tela ou um texto com imagens?
- Onde reabrir os tutoriais depois (por exemplo, um item de ajuda)?
- Aviso a cada entrada ou só quando há novidade não vista?
- Quem cadastra as novidades e com que texto?
