# 021 — Central de ajuda

**Status:** rascunho (ideia para conversar, ainda sem regras)
**Branch:** feature/021-central-de-ajuda
**Depende de:** 011 (API para automação); combina com a 020 (site do produto)

## Problema

Não há explicação de como usar o moneyPilot. Cada pessoa descobre sozinha o que é um título, como funciona a baixa, o parcelamento, a recorrência ou o orçamento. A única página de ajuda, "Usando com IA", acabou técnica demais: fala de refresh token e Swagger para quem só queria usar o app com um assistente.

## Objetivo

A pessoa encontra, em linguagem simples, um tutorial para cada parte do moneyPilot e chega a ele de dentro da tela em que está.

## Regras de negócio

Nenhuma definida ainda. Ponto de partida, a confirmar:

- Uma página "Ajuda" com um tutorial curto por área: primeiros passos, contas, categorias, lançamentos e transferências, orçamento, a pagar e receber (parcelas, baixa, recorrência), previsão, relatórios, alertas, perfil e segurança, suporte, usar com IA.
- Cada tutorial: para que serve, passo a passo com imagens (dados fictícios) e dúvidas comuns.
- Um botão de ajuda ("?") em cada tela abre o tutorial daquela tela.
- **"Usando com IA" reescrito**: primeiro o caminho simples (o que dá para fazer, o texto pronto para colar no assistente); a parte técnica (tokens, renovação, Swagger) fica numa seção "Para desenvolvedores".
- Conteúdo estático, sem backend.

## Critérios de aceite

A escrever depois da conversa.

## Casos de borda

- Tela que muda depois que o tutorial foi escrito (imagens e textos desatualizados)
- Pessoa sem login lendo a ajuda
- Tutorial aberto no celular

## Fora de escopo

A definir. Candidatos, a confirmar: busca na ajuda, vídeos, tour guiado dentro do app, ajuda em inglês.

## Perguntas em aberto

- A ajuda é pública (no site da 020, aberta a buscadores) ou só dentro do app?
- Imagens (prints) ou só texto? Prints envelhecem a cada mudança de tela.
- O cartão de primeiros passos da 013 aponta para estes tutoriais?
- "Usando com IA" continua uma página própria ou vira um tutorial da ajuda?
- Tem um "Esta página ajudou?" no fim de cada tutorial?
