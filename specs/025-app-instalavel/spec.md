# 025 — App instalável no celular

**Status:** rascunho
**Branch:** feature/025-app-instalavel
**Depende de:** 020 (o app vive em `/app/`)

## Problema

Quem usa o moneyPilot no celular abre o navegador, digita ou procura o endereço e faz o login. Não há ícone na tela inicial e a experiência é a de um site, com a barra do navegador ocupando espaço. Criar um app nativo (Android e iOS) exigiria duas bases de código, lojas e uma manutenção que o projeto não comporta.

## Objetivo

A pessoa "instala" o moneyPilot no celular a partir do navegador: ganha um ícone na tela inicial e o app abre em tela cheia, sem a barra do navegador, usando o mesmo código web de hoje.

## Abordagem

**PWA (aplicativo web instalável)**, e não uma webview empacotada. A PWA reaproveita o cliente atual (JS puro, sem build), não passa por loja e atualiza junto com o deploy. Uma webview nativa (APK/IPA) fica fora de escopo; se um dia for preciso estar nas lojas, a PWA é a base dela.

## Regras de negócio

- **Manifesto** (`manifest.webmanifest`) com nome "moneyPilot", `start_url` e `scope` em `/app/`, `display: standalone`, `theme_color` `#13294b` (o mesmo do app) e idioma `pt-BR`.
- **Ícones** 192×192 e 512×512, mais uma versão *maskable*, gerados a partir da logo atual.
- **Service worker mínimo**, servido de dentro de `/app/` para ter o escopo certo. Ele existe para o app ser instalável e para mostrar uma página "sem conexão" amigável.
- **Dados financeiros nunca vão para o cache.** O service worker não guarda respostas de `/api/**`. Só a página "sem conexão" e, se o plano decidir, os arquivos estáticos do cliente (com versão, para um deploy novo não deixar a pessoa presa num JS antigo).
- **Como instalar:**
  - Android/Chrome: botão "Instalar o app" quando o navegador oferecer a instalação.
  - iOS/Safari não oferece o botão: mostrar o passo a passo ("Compartilhar" → "Adicionar à Tela de Início").
- **Onde fica a orientação de instalação:** no perfil, no site (020) e/ou na Central de Ajuda (021). Posição exata no plano.
- Quem já usa o app no navegador não muda nada: a instalação é opcional e o app continua funcionando como hoje.
- A sessão (token no `localStorage` e cookie de refresh) funciona igual no app instalado.

## Critérios de aceite

- [ ] **CA1** — Dado o app aberto no Chrome do Android, quando o navegador avalia a página, então ela é considerada instalável (manifesto válido, ícones, service worker, HTTPS).
- [ ] **CA2** — Dada a instalação concluída, quando a pessoa abre o ícone, então o moneyPilot abre em `/app/`, em tela cheia, com a cor de tema do app.
- [ ] **CA3** — Dado o app instalado e sem conexão, quando a pessoa o abre, então vê uma página "sem conexão" clara, e nenhum dado financeiro aparece vindo de cache.
- [ ] **CA4** — Dado o service worker, então nenhuma resposta de `/api/**` é guardada em cache.
- [ ] **CA5** — Dado um deploy novo, quando a pessoa abre o app instalado, então recebe a versão nova do cliente sem precisar reinstalar nem limpar dados.
- [ ] **CA6** — Dado o Safari no iPhone, então a orientação mostra o passo a passo de "Adicionar à Tela de Início", e o botão de instalar não aparece onde o navegador não suporta.
- [ ] **CA7** — Dada uma pessoa logada no navegador, quando instala o app, então o login (incluindo a renovação por refresh) continua funcionando no app instalado.
- [ ] **CA8** — Dado o manifesto e o service worker, então são servidos sem exigir login (rotas públicas na segurança) e com os tipos de conteúdo corretos.

## Casos de borda

- Login com Google dentro do app instalado (no iOS o fluxo em janela própria pode se comportar diferente): testar nos dois sistemas.
- Cache antigo depois de um deploy (ver CA5).
- Aparelho compartilhado: o app instalado mantém a sessão como o navegador; "Sair" continua apagando token e cookie.
- Navegadores sem suporte a service worker: o app segue funcionando normalmente, sem instalação.
- Conflito com o redirecionamento de links antigos `/#/...` da 020.

## Fora de escopo

- App nativo, webview empacotada (APK/IPA) e publicação em Play Store ou App Store.
- Uso offline com dados (ler ou lançar sem internet) e sincronização posterior.
- Notificações push.
- Atalhos de app (pressionar o ícone), compartilhar para dentro do app e outras APIs de PWA além da instalação.

## Perguntas em aberto

- A PWA atende ao que você imaginou com "webview"? Ou o objetivo inclui estar na Play Store (aí seria uma TWA em cima da PWA, outra spec)?
- Quer o app 100% online (só a página "sem conexão") ou já cachear os arquivos estáticos para abrir mais rápido?
- O ícone atual da logo serve, ou há uma versão quadrada para os ícones?
- Onde a orientação de instalação deve aparecer primeiro: perfil, site ou os dois?
