# 019 — Tarefas

**Plano:** [plan.md](plan.md)

Cada tarefa vira um commit (ou poucos) e aponta para os critérios que atende.

- [x] **T1** — `PixProperties` (`app.pix.key`, `app.pix.qr-file`) registrada em `PropertiesConfig`, com `enabled()` (CA4, CA6)
- [x] **T2** — `ContributionNotConfiguredException` (`404` no `GlobalExceptionHandler`), `PixResponse` e `ContributionService` (chave e QR code como data URI, com limite de 512 KB e `null` se faltar) (CA2, CA4)
- [x] **T3** — `ContributionController`: `GET /contribution/pix` com OpenAPI no padrão da 011, e `contributionEnabled` no `ProfileResponse` (CA1, CA4)
- [x] **T4** — Configuração: `PIX_KEY` e `PIX_QR_FILE` em `application-prod.properties`, `.env.production.example` e `docker-compose.prod.yml`, sem `:?` e sem valor real (CA4, CA6)
- [x] **T5** — `ContributionIntegrationTest` e a rota nova no `OpenApiContractIntegrationTest` (CA1, CA2, CA4)
- [x] **T6** — Cliente: `session.contribution`, `api.contribution.pix` e a busca do perfil em `app.js` (CA1, CA4)
- [x] **T7** — Tela `js/views/contribution.js` (gratuito primeiro, chave, "Copiar chave", QR code) e rota `#/apoie` que cai na Visão geral sem a configuração (CA2, CA3, CA4)
- [x] **T8** — Link "Pagar um cafezinho?" sob o "Sair" e estilo `.foot-link`, só com a configuração (CA1, CA4, CA5)
- [x] **T9** — Testes JS: `contribution-view.test.mjs` e `session.contribution` em `api.test.mjs` (CA2, CA3, CA4)
- [x] **T10** — README: tabela da API, decisão de domínio (apoio voluntário por Pix, nada guardado), `PIX_KEY`, `PIX_QR_FILE` e cliente web (CA1–CA6)
- [x] **T11** — Conferir que nenhuma chave Pix nem imagem de QR code entrou no repositório (CA6)
- [ ] **T12** — Rodar `./mvnw verify` e `node --test "src/test/js/*.test.mjs"`, e a checagem manual do plano (CA1–CA5) (suítes rodadas; falta a checagem manual no navegador)
- [x] **T13** — Validação com as skills do projeto (`api-design-principles`, `web-design-guidelines`, `accessibility-compliance`, `responsive-design`) e anotar a revisão aqui
- [x] **T14** — `PIX_KEY` e `PIX_QR_FILE` já definidas no servidor — Gustavo
- [ ] **T15** — Refazer a imagem do QR code: a atual veio com recorte ruim e quebrou o layout do cartão. Gerar de novo no app do banco, só o QR (sem a tela em volta), e conferir que a leitura pelo celular funciona. Enquanto isso, `PIX_QR_FILE` pode ficar vazio e a tela mostra só a chave
- [x] **T16** — Melhorar a tela `#/apoie` no desktop e no celular: logo menor, ícone em destaque, chave em fonte monoespaçada, botão em linha inteira no celular, link de volta legível e cartão limitado a 40rem dentro do app (conferido a 1280 e 500px). **Pendente (Gustavo):** conferir qual chave está no servidor — hoje é o Pix "copia e cola" (BR Code), e a spec pede a chave aleatória

- [x] **T17** — Apoio aberto (aditivo, CA7): `GET /contribution/pix` público (`SecurityConfig`, `@SecurityRequirements`, `PUBLIC_OPERATIONS` do contrato), tela `#/apoie` sem login com link para o site, README e testes

## Verificação

- `./mvnw verify`: 455 testes, nenhuma falha. `node --test "src/test/js/*.test.mjs"`: 161 testes, nenhuma falha.
- **CA6:** `git ls-files` e `git grep` só encontram valores fictícios (o exemplo da documentação, zeros no teste e um PNG de teste de 70 bytes). Nenhuma chave Pix real nem QR code.
- **CA5:** o apoio só existe no link do rodapé e na tela `#/apoie`; não há pop-up, banner nem chamada fora dessa tela.
- **Pendente:** a checagem manual do plano no navegador (sem `PIX_KEY` o link some; com `PIX_KEY` e `PIX_QR_FILE` o link aparece, a tela abre com o aviso de projeto gratuito primeiro, "Copiar chave" copia e confirma, e o QR code aparece; celular e desktop).

## Notas de implementação

- A tela segue a ordem da spec: título e o aviso de gratuito vêm antes da chave e do QR code (CA2).
- O cliente descobre se o apoio existe pelo `contributionEnabled` do `/profile`, como já faz com `admin`; `session.contribution` é limpo com a sessão.
- Testes de integração em duas classes (com e sem `PIX_KEY`), porque `@TestPropertySource` vale por classe; as bordas da imagem ficaram em teste unitário do serviço.

**Revisão com as skills do projeto**

- `api-design-principles`: recurso `GET /contribution/pix` protegido por Bearer, `404` documentado para "não configurado" (não revela o que existe), `401` sem login, campos com `@Schema` e exemplo fictício, campo novo do `/profile` opcional (sem quebra).
- `web-design-guidelines` e `accessibility-compliance`: `h1` e `h2` em ordem, `label` ligado ao campo da chave, campo `readonly` com `name` e `autocomplete="off"`, `alt` e dimensões no QR code, aviso de cópia em região `role="status"` já no DOM, link com foco visível (regra global) e `aria-current` na página atual. Ajustado: o alvo de toque do link no celular.
- `responsive-design`: campo e botão quebram linha no celular (`flex-wrap`), QR code com `width: min(220px, 100%)`, card de até 560px, sem largura fixa. Falta conferir visualmente.

## Passo a passo do deploy (para o Gustavo executar)

1. No app do banco, gere uma **chave Pix aleatória** e baixe a imagem do QR code dela (png ou jpg, até 512 KB).
2. Copie a imagem para uma pasta do servidor, fora do repositório (por padrão `/opt/moneypilot-pix`, com permissão de leitura para todos).
3. O `docker-compose.prod.yml` já monta essa pasta como `/pix` (somente leitura); para outra pasta, defina `PIX_QR_DIR` no `.env`.
4. No `.env` do servidor: `PIX_KEY=<a chave aleatória>` e `PIX_QR_FILE=/pix/<nome do arquivo>` (o caminho é o de dentro do container). Mudou o `.env` ou o compose: `up -d app` recria o container, e `restart` não relê as variáveis.
5. Faça o `pg_dump` de sempre, suba a versão e reinicie. Para trocar a chave depois, basta mudar o `.env` e reiniciar o app.
