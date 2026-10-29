# 021 — Tarefas

**Plano:** [plan.md](plan.md)

Cada tarefa aponta para os critérios que atende. Mudança de tela revisa o tutorial dela em `static/ajuda/`.

- [x] **T1** — `WebConfig`: `/ajuda` redireciona para `/ajuda/`, que entrega o índice (CA1)
- [x] **T2** — `SitePagesIntegrationTest`: redirect, índice e tutorial sem login (CA1)
- [x] **T3** — `static/ajuda/`: índice e 12 tutoriais em texto, com os três `h2`, trilha e sem imagens (CA1, CA2, CA4)
- [x] **T4** — `css/site.css` (bloco `.help-*`) e `js/site.js` com o topo por sessão e o "Copiar texto" na ajuda (CA4, CA7)
- [x] **T5** — Link "Ajuda" de volta ao rodapé do site (CA1)
- [x] **T6** — `js/help.js`, o "?" no `pageHead` e o CSS do botão (CA3, CA6, CA8)
- [x] **T7** — `views/ai-guide.js` aponta para o tutorial e perde os passos técnicos (CA5)
- [x] **T8** — Textos do OpenAPI passam a citar `/ajuda/usando-com-ia.html` (CA4)
- [x] **T9** — Testes JS: `help.test.mjs`, `help-pages.test.mjs`, `ai-guide-view.test.mjs`, `static-pages.test.mjs` (CA1–CA6, CA8)
- [x] **T10** — README: a central de ajuda, o `/ajuda/` e o "?"
- [x] **T11** — Revisão com as skills do projeto (`web-design-guidelines`, `accessibility-compliance`, `responsive-design`), anotada em "Revisão"

## Verificação

- `./mvnw verify`: 465 testes, sem falhas.
- `node --test "src/test/js/*.test.mjs"`: 185 testes, sem falhas.
- Manual (Edge headless, servidor estático): as 13 páginas da ajuda sem rolagem horizontal em 390px e 1280px (CA7); o "?" com 44px no celular e área de toque de 44px no desktop (CA6). Falta olhar o "?" em cada tela com o app rodando e login.

## Revisão

- **Corrigido:** aspas retas no texto dos tutoriais viraram aspas curvas (web-design-guidelines); o `summary` das dúvidas ganhou respiro vertical para o alvo de toque (accessibility-compliance, responsive-design).
- **Já conforme:** link para pular ao conteúdo, hierarquia h1 > h2 > h3, `lang`, foco visível (`:focus-visible` global e no "?"), `aria-hidden` no "?" decorativo, o `<a>` com nome acessível e aviso de nova aba, blocos de código com `tabindex="0"` e rolagem própria, `<label>` no texto para colar, `role="status"` no aviso de cópia, `translate="no"` nos exemplos, imagens com largura e altura, hover nos links e botões, sem `transition: all`, sem rolagem horizontal em 390px e 1280px.
- **Não feito:** o "?" em cada tela do app logado (o app em execução na máquina é uma versão anterior). O "?" por rota está coberto pelos testes e o botão foi visto isolado em 500px e 1280px.

## Notas

- Os HTMLs foram gerados por um script a partir de um único conteúdo, para o topo e o rodapé não divergirem; o teste `help-pages.test.mjs` confere que são iguais e que o texto para colar é o de `assistantPrompt`.
- O logo do topo é a única `<img>` das páginas; o `main` de cada tutorial não tem imagens (CA2).
- `admin-tickets-view.test.mjs` passou a procurar o link `mailto:` em vez do primeiro link da tela, porque o "?" agora vem antes.
