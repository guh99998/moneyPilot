# 021 — Central de ajuda

**Status:** aprovada
**Branch:** feature/021-central-de-ajuda
**Depende de:** 020 (site do produto, onde as páginas vivem); 011 (API para automação)

## Problema

Não há explicação de como usar o moneyPilot. Cada pessoa descobre sozinha o que é um título, como funciona a baixa, o parcelamento, a recorrência ou o orçamento. A única página de ajuda, "Usando com IA", acabou técnica demais: fala de refresh token e Swagger para quem só queria usar o app com um assistente.

## Objetivo

A pessoa encontra, em linguagem simples, um tutorial para cada parte do moneyPilot e chega a ele de dentro da tela em que está.

## Regras de negócio

- **Páginas públicas** do site, em `/ajuda/...`, abertas a buscadores e a quem não tem conta. Conteúdo estático, sem backend.
- **Só texto**, sem prints: imagens envelhecem a cada mudança de tela.
- Um **índice** em `/ajuda/` e um tutorial curto por área: primeiros passos, contas, categorias, lançamentos e transferências, orçamento, a pagar e receber (parcelas, baixa, recorrência), previsão, relatórios, alertas, perfil e segurança, suporte e usar com IA.
- Cada tutorial: para que serve, passo a passo e dúvidas comuns.
- **Botão "?" em cada tela do app**, que abre o tutorial daquela tela (em nova aba, para não perder o que a pessoa está fazendo).
- **"Usando com IA" vira um tutorial da ajuda**: primeiro o caminho simples (o que dá para fazer e o texto pronto para colar no assistente); a parte técnica (tokens, renovação, Swagger) fica numa seção "Para desenvolvedores" no fim. A tela `views/ai-guide.js` passa a apontar para esse tutorial.
- Os tutoriais são o destino dos passos do cartão de boas-vindas (013).
- Mesma identidade visual do site (020), mobile-first, JS puro e sem build.

## Critérios de aceite

- [ ] **CA1** — Dado um visitante sem sessão, quando abre `/ajuda/`, então vê o índice com um link para cada área listada e consegue ler qualquer tutorial sem login.
- [ ] **CA2** — Dado qualquer tutorial, então tem para que serve, passo a passo e dúvidas comuns, e não contém imagens.
- [ ] **CA3** — Dada qualquer tela do app (exceto login e cadastro), quando a pessoa aciona o "?", então abre em nova aba o tutorial daquela tela.
- [ ] **CA4** — Dado o tutorial "Usando com IA", então o caminho simples e o texto para colar vêm primeiro e a seção "Para desenvolvedores" vem por último.
- [ ] **CA5** — Dada a tela `ai-guide`, então aponta para o tutorial da ajuda em vez de repetir o conteúdo técnico.
- [ ] **CA6** — Dado o botão "?", então tem nome acessível ("Ajuda sobre esta tela"), foco visível e alvo de toque adequado no celular.
- [ ] **CA7** — Dadas as páginas da ajuda em 390px e 1280px, então não há rolagem horizontal e a leitura é confortável.
- [ ] **CA8** — Dada uma tela do app sem tutorial mapeado, então o "?" leva ao índice da ajuda.

## Casos de borda

- Tela que muda depois do tutorial escrito: por ser só texto, o custo de atualizar é baixo; mudança de tela deve revisar o tutorial
- Pessoa sem login lendo a ajuda: permitido, e o topo mostra "Entrar | Criar conta"
- Tutorial aberto no celular
- Tela nova do app sem mapeamento: cai no índice (CA8)

## Fora de escopo

- "Esta página ajudou?", busca na ajuda, vídeos, tour guiado dentro do app e ajuda em inglês
- Prints e imagens nos tutoriais

## Perguntas em aberto

- Nenhuma de negócio. O mapa tela → tutorial e a estrutura das páginas ficam no plano; os textos são propostos pelo Claude no plano e ajustados pelo Gustavo.
