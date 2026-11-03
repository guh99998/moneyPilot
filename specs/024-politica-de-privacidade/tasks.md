# 024 — Tarefas

**Plano:** [plan.md](plan.md)

- [x] **T1** — Fontes no próprio site: `static/fonts/` (Manrope e Bricolage Grotesque, latin, com a licença OFL), `@font-face` em `app.css`, preload da Manrope e remoção do Google Fonts dos 15 HTMLs
- [x] **T2** — Páginas `/privacidade/` e `/termos/` com data de atualização, rotas no `WebConfig` e entrada no sitemap (CA1, CA4, CA5)
- [x] **T3** — Links "Privacidade" e "Termos de uso" no rodapé do site e das páginas de ajuda (CA2)
- [x] **T4** — Aviso de concordância na tela de cadastro, com links em nova aba (CA3)
- [x] **T5** — Testes: JS (`legal-pages`, `auth-view`) e integração (`SitePagesIntegrationTest`, `SeoIntegrationTest`)
- [ ] **T6** — Revisão do texto pelo Gustavo (privacidade e termos)
- [ ] **T7** — Operação, fora do código (Gustavo): regra de ciclo de vida de 30 dias no bucket R2 e limite de log do container do Caddy
- [ ] **T8** — Revisão com as skills de UI e largura de 390px e 1280px (CA6), anotada abaixo
- [ ] **T9** — Merge na `develop`; PR para a `main` só depois da 012 em produção e da T7 feita

## Revisão com as skills (parcial)

- **Estrutura e acessibilidade:** um `h1` por página, `h2` por seção, link "Pular para o conteúdo", `main` e `nav` com rótulo, `time` com `datetime`, `lang="pt-BR"`. Links com texto próprio, sem "clique aqui".
- **Responsividade:** mesma coluna de 46 rem das páginas de ajuda, com `overflow-wrap: anywhere`; o aviso do cadastro quebra linha sem estourar o cartão.
- **Fontes:** `font-display: swap`, preload só da Manrope, zero requisições a domínios de fonte (teste).
- **Promessas:** o texto só afirma o que o app e a operação fazem; o texto não promete e-mail de novidades nem aviso dentro do app; o aviso de encerramento é no site.
- **Falta (T8):** conferir a 390 px e 1280 px num navegador, o que a Claude não consegue fazer neste ambiente.
