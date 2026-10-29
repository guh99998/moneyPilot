# 020 — Site do produto

**Status:** aprovada
**Branch:** feature/020-site-do-produto
**Depende de:** nenhuma (combina com a 019, a 021 e a 024)

## Problema

Quem chega ao moneyPilot pela primeira vez cai direto numa tela de login. Não há nada explicando o que o app faz, por que usar ou como começar. Isso afasta quem ainda não tem conta e não ajuda a divulgar o projeto.

## Objetivo

Quem acessa o endereço do moneyPilot vê uma página de apresentação do produto. Sem sessão, o topo traz "Entrar" e "Criar conta"; com sessão, traz "Abrir o app". O app passa a viver em `/app/`.

## Regras de negócio

- **Mesmo domínio.** A raiz `/` é **sempre o site**, com ou sem sessão. O app passa para `/app/`.
- **Topo fixo** com a logo. Sem sessão: "Entrar" e "Criar conta". Com sessão: "Abrir o app". Ambos levam a `/app/`.
- **Quem já usa não perde a sessão.** O token vive no `localStorage` (separado por domínio, não por caminho) e o cookie de refresh usa `path=/api/v1/auth`, que não muda.
- **Links antigos continuam funcionando.** Se o site é aberto com um hash `#/...` (favoritos e links de e-mails já enviados: verificação, reset, troca de e-mail, "Usando com IA"), ele redireciona para `/app/#/...` preservando o hash inteiro, inclusive a query (`?token=...`).
- **Links novos de e-mail** apontam direto para `/app/#/...` (verificação, reset de senha e troca de e-mail), e a descrição do `@Tag` de usuário que cita `#/usando-com-ia` também muda.
- **Login com Google** continua funcionando em `/app/`: as origens autorizadas dependem só do domínio e não mudam.
- **Seções do site:** o que é; recursos (3 a 4 prints com dados fictícios); como começar; perguntas frequentes; rodapé com suporte, "Apoie o projeto", GitHub e link para a política de privacidade e termos (024).
- **HTML de verdade** (não uma rota com `#`), com meta tags de título, descrição e prévia de link (Open Graph), para buscadores e compartilhamento.
- **Identidade visual do app**, mobile-first, JS puro e sem build, como o resto do cliente.
- **Sem números de uso** e sem dados reais nos prints: só dados fictícios.

## Critérios de aceite

- [ ] **CA1** — Dado um visitante sem sessão, quando abre `/`, então vê o site com "Entrar" e "Criar conta" no topo, e ambos levam a `/app/`.
- [ ] **CA2** — Dada uma pessoa com sessão ativa, quando abre `/`, então vê o mesmo site, com "Abrir o app" no lugar de "Entrar" e "Criar conta", e o botão leva a `/app/`.
- [ ] **CA3** — Dado um link antigo como `/#/transacoes` ou `/#/verificar-email?token=abc`, quando é aberto, então o navegador termina em `/app/#/transacoes` ou `/app/#/verificar-email?token=abc`, com a query intacta.
- [ ] **CA4** — Dada uma pessoa logada antes do deploy, quando abre `/app/` depois dele, então continua logada, sem pedir novo login.
- [ ] **CA5** — Dado o login com Google, quando a pessoa o usa em `/app/`, então o botão aparece e o login conclui normalmente.
- [ ] **CA6** — Dado um e-mail novo de verificação, reset de senha ou troca de e-mail, então o link gerado aponta para `/app/#/...`.
- [ ] **CA7** — Dadas as rotas `/`, `/app`, `/app/` e `/ajuda/**`, quando acessadas sem autenticação, então respondem sem exigir login (`/app` e `/app/` entregam o `index.html` do app).
- [ ] **CA8** — Dada a página do site, então tem `<title>`, `description` e meta tags de prévia de link, e as seções listadas acima, na ordem das regras.
- [ ] **CA9** — Dadas telas de 390px e 1280px, então o site não tem rolagem horizontal e o topo, os prints e o rodapé se reorganizam sem sobreposição.
- [ ] **CA10** — Dado o rodapé, então tem os links de suporte, "Apoie o projeto" (quando a 019 estiver configurada), GitHub e política de privacidade e termos.

## Casos de borda

- Favoritos do endereço antigo do login (`/` sem hash): caem no site, que é o novo comportamento esperado
- Links de e-mails já enviados, com `#/` na raiz: o redirecionamento cobre
- Pessoa logada que abre o site: vê o site com "Abrir o app", sem redirecionamento automático
- Hash desconhecido ou vazio: sem hash fica no site; hash desconhecido vai ao app, que já trata rota inexistente
- Navegador com JavaScript desligado: o site continua legível (o redirecionamento de hash é a única função que depende de JS)

## Fora de escopo

- Blog, versão em inglês, domínio próprio, analytics e números de uso
- Conteúdo da central de ajuda (021) e da política de privacidade e termos (024), que só são linkados daqui
- Qualquer mudança no fluxo de login, cadastro ou sessão

## Perguntas em aberto

- Nenhuma de negócio. Decisões técnicas (mover `static/index.html` para `static/app/index.html`, ajustar caminhos relativos de `js/` e CSS, forward de `/app` e `/app/`, configuração de segurança) ficam no plano.
