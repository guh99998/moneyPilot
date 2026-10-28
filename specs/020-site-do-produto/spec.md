# 020 — Site do produto

**Status:** rascunho (ideia para conversar, ainda sem regras)
**Branch:** feature/020-site-do-produto
**Depende de:** nenhuma (combina com a 019 e a 021)

## Problema

Quem chega ao moneyPilot pela primeira vez cai direto numa tela de login. Não há nada explicando o que o app faz, por que usar ou como começar. Isso afasta quem ainda não tem conta e não ajuda a divulgar o projeto.

## Objetivo

Quem acessa o endereço do moneyPilot sem estar logado vê uma página de apresentação do produto, com os botões "Criar conta" e "Entrar" no topo.

## Regras de negócio

Nenhuma definida ainda. Ponto de partida, a confirmar:

- **Mesmo domínio**, sem domínio novo: o site fica na raiz (`/`) e o app em outra rota (ex.: `/app`), ou a raiz decide pela sessão (quem está logado vai direto para o app).
- Topo fixo com a logo, "Entrar" e "Criar conta".
- Seções: o que é, principais recursos (com imagens do app com dados fictícios), como começar, perguntas frequentes e rodapé (suporte, "Apoie o projeto", GitHub, política de privacidade).
- Página HTML de verdade (não uma rota com `#`), para buscadores e prévias de link funcionarem.
- Mesma identidade visual do app, mobile-first, JS puro e sem build, como o resto do cliente.

## Critérios de aceite

A escrever depois da conversa.

## Casos de borda

- Quem já salvou o endereço antigo do login nos favoritos
- Links que já existem em e-mails enviados (verificação, reset, troca de e-mail) e no "Usando com IA"
- Pessoa logada que abre o site
- Login com Google, que hoje desenha o botão na tela de login

## Fora de escopo

A definir. Candidatos, a confirmar: blog, versão em inglês, domínio próprio, analytics.

## Perguntas em aberto

- Raiz: sempre o site (app em `/app`), ou site só para quem não tem sessão?
- **Política de privacidade e termos de uso**: com usuários reais e a LGPD, o site é o lugar natural para eles. Entram nesta spec ou numa própria?
- O site mostra números (quantas pessoas usam)? Proposta: não, para não expor métricas internas.
- A central de ajuda (021) fica dentro do site, em páginas públicas?
- Precisa mudar as URLs dos e-mails ou as origens autorizadas do login com Google?
