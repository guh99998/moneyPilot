# 024 — Plano: política de privacidade e termos de uso

**Status:** rascunho (aguardando a retenção no destino dos backups e nos logs sem limite)

## Abordagem

Duas páginas estáticas (`/privacidade/` e `/termos/`) no padrão visual das páginas de `ajuda/` e do site (`css/site.css`), mais três mudanças pequenas ao redor: fontes hospedadas no site, links no rodapé e aviso no cadastro. Sem backend, sem migration, sem dependência nova.

## Impacto

- **Schema e API:** nenhum.
- **Telas:** `static/privacidade/index.html`, `static/termos/index.html` (novas); rodapé de `static/index.html` e das páginas de `ajuda/`; `js/views/auth.js` (aviso no cadastro).
- **Fontes:** `static/fonts/` (arquivos `.woff2` de Manrope e Bricolage Grotesque, mais a licença OFL), `@font-face` em `css/app.css`/`site.css`, e os `<link>` do Google Fonts removidos dos 15 HTMLs.
- **SEO:** as duas páginas entram no `sitemap.xml` (o `SeoController` lista as páginas de `ajuda/`; é preciso incluir estas duas) e ganham descrição e Open Graph.
- **Operação (fora do código):** ajustar a retenção onde ela estiver aberta (ver "Retenção").

## Retenção levantada

| Item | Situação |
|---|---|
| Backup local na VPS | últimos 14 dias |
| Backup externo | enviado por `rclone` para o remoto `r2crypt` (R2, criptografado). **Prazo no destino: a confirmar** |
| Backup automático do provedor da VPS | existe ao menos um (31/10). **Frequência e prazo: a confirmar no painel** |
| Logs de containers | 3 containers com limite de 3 × 10 MB; 2 sem limite. **A resolver** |
| Logs do sistema (journal) | 13,7 MB; sem preocupação |

A política só é aprovada com todas as linhas respondidas. Linha "sem limite" vira ajuste de operação antes de publicar.

## Estrutura da política de privacidade

1. **Quem somos:** o moneyPilot é um projeto gratuito mantido por uma pessoa; o contato é o formulário de suporte do app.
2. **Dados que coletamos:** nome e e-mail do cadastro; foto e identificador da conta Google, quando a pessoa entra com o Google; senha, guardada só como hash; os dados financeiros que a pessoa lança (contas, lançamentos, categorias, orçamentos, títulos); chamados de suporte; endereço IP, usado só para limitar tentativas e que pode aparecer nos logs do servidor.
3. **Para que usamos:** fazer o app funcionar, autenticar, enviar e-mails da conta (verificação, redefinição de senha, avisos) e responder o suporte. Não vendemos dados, não há publicidade, não há análise de comportamento.
4. **Com quem compartilhamos:** Resend (envio de e-mails), Google (login, só se a pessoa usar), a empresa da VPS (hospedagem) e o armazenamento dos backups (Cloudflare R2). Cada um recebe só o necessário.
5. **Cookies:** um único cookie, o de sessão, necessário para manter a pessoa logada. Sem rastreamento e sem fontes carregadas de terceiros.
6. **Por quanto tempo guardamos:** enquanto a conta existir. Ao excluir a conta, os dados saem do banco na hora. As cópias de segurança continuam por até N dias e depois são descartadas. [N a fechar com a retenção]
7. **Seus direitos (LGPD):** acessar, corrigir (Perfil), excluir (Perfil → Excluir conta) e pedir informações pelo suporte.
8. **Segurança:** conexão HTTPS, senhas com hash, backups criptografados. Nenhum sistema é 100% seguro; em caso de incidente relevante, avisamos as pessoas afetadas.
9. **Mudanças:** a data de atualização fica no topo; mudança relevante é avisada no app.

## Estrutura dos termos de uso

1. O serviço é gratuito e oferecido "como está", sem garantia de disponibilidade.
2. A pessoa responde pelos dados que lança e pela guarda da própria senha.
3. O moneyPilot não é consultoria financeira nem contábil.
4. O apoio voluntário (019) não compra recursos nem garantias.
5. O projeto pode mudar ou acabar, com aviso prévio e tempo para a pessoa exportar ou excluir os dados.
6. Uso indevido (abuso, tentativa de invadir) pode levar ao bloqueio da conta.
7. Exclusão e privacidade seguem a política. Foro: o da pessoa consumidora.

Os textos completos saem na implementação, para revisão do Gustavo antes do merge. Não substituem orientação jurídica.

## Fontes no site

1. Baixar os `.woff2` (Latin) de Manrope (400–800) e Bricolage Grotesque (600–800) com a licença OFL em `static/fonts/`.
2. `@font-face` com `font-display: swap`.
3. `<link rel="preload">` só das fontes do primeiro texto.
4. Remover `preconnect` e `stylesheet` do Google Fonts dos 15 HTMLs.

## Aviso no cadastro

Texto: "Ao criar a conta, você concorda com os **Termos de uso** e a **Política de privacidade**." Links com `target="_blank" rel="noopener"`, em `js/views/auth.js`, só na tela de cadastro. Contraste AA e alvo de toque de 44 px.

## Testes

- `SitePagesIntegrationTest` (existente): `/privacidade/` e `/termos/` respondem `200` sem login (CA1); o sitemap lista as duas.
- `node --test`: o aviso aparece no cadastro com os dois links e `rel="noopener"` (CA3), e não aparece no login.
- Teste que garante zero referência a `fonts.googleapis`/`gstatic` nos HTMLs e no CSS.
- Manual: 390 px e 1280 px sem rolagem horizontal (CA6); DevTools sem requisições a domínios de fontes.

## Fechamento

Skills `web-design-guidelines`, `accessibility-compliance` e `responsive-design`; anotar a revisão em `tasks.md`; README (nota sobre as páginas e as fontes); merge `--no-ff` na `develop`; PR para a `main` só com ok do Gustavo, depois que a 012 estiver em produção.
