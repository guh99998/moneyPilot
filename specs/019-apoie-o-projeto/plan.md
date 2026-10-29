# 019 — Plano técnico

**Spec:** [spec.md](spec.md)
**Status:** proposto

## Abordagem

### A. Configuração (fora do git)

- `config/PixProperties` (`app.pix`), no padrão de `AdminProperties`: `key` (chave Pix) e `qrFile` (caminho de um arquivo de imagem no servidor). Registrada em `PropertiesConfig`. Os dois vêm do ambiente (`PIX_KEY`, `PIX_QR_FILE`), nunca do código; vazios por padrão.
- `PixProperties.enabled()` é verdadeiro só com `key` preenchida (sem espaços nas pontas). Sem chave, o apoio inteiro some.

### B. Domínio `contribution/`

- `ContributionService.pix()`:
  1. se `!enabled()`, lança `ContributionNotConfiguredException` (`404` no `GlobalExceptionHandler`, no formato `ErrorResponse`);
  2. lê o arquivo do QR code a cada chamada, para trocar a imagem sem reiniciar. Aceita `png` e `jpg`/`jpeg`, até 512 KB. Se não houver `qrFile`, o arquivo não existir, não puder ser lido ou passar do limite, registra um aviso no log e devolve `qrImage = null`. O erro não chega à pessoa;
  3. devolve `PixResponse(key, qrImage)`, em que `qrImage` é um data URI (`data:image/png;base64,...`).
- Data URI em vez de rota de imagem: a rota é protegida por Bearer, e uma tag `<img>` não envia o cabeçalho. O app não define Content-Security-Policy, então `data:` carrega.
- `ContributionController`: `GET /api/v1/contribution/pix`, protegido por Bearer, com `@Tag`, `@Operation`, `@ApiResponse` (200, 401, 404) e `@Schema` com exemplo em cada campo do record (padrão da 011).
- `ProfileResponse` ganha `boolean contributionEnabled`, como já tem `admin`, para o cliente decidir se mostra o link sem uma chamada a mais. Quem protege a rota é o servidor.

## Banco de dados

Nenhuma migration. O app não guarda nada sobre quem contribui.

## API

| Método | Rota | Mudança |
|---|---|---|
| GET | `/contribution/pix` | Novo. Devolve `{key, qrImage}`; `qrImage` é `null` sem imagem. `401` sem login; `404` sem chave configurada. |
| GET | `/profile` | Novo campo `contributionEnabled` (boolean), opcional para clientes antigos. |

Sem mudança incompatível. Não há campo de dinheiro (nada entra no `MoneyScaleIntegrationTest`).

## Cliente web

- `session` (`js/api.js`) guarda `contribution`, preenchido pelo `api.profile.get()` junto com `admin`; `session.clear()` limpa. O `render()` de `app.js` busca o perfil também quando `contribution` ainda é desconhecido, no mesmo bloco que hoje busca nome, foto e `admin`.
- `api.contribution.pix()`.
- `js/views/contribution.js` (novo), `renderContribution()`, mobile-first:
  - primeiro, em destaque, o texto de que o moneyPilot é um projeto gratuito e sempre vai ser e que apoiar é só um agradecimento opcional (CA2);
  - depois a chave Pix em um campo somente leitura e selecionável, com o botão "Copiar chave";
  - o QR code com `alt="QR code da chave Pix"`, só se `qrImage` vier;
  - "Copiar chave" usa `navigator.clipboard.writeText`. Dá certo: confirma com um aviso ("Chave copiada.") anunciado por `aria-live`. Falha ou área de transferência indisponível: seleciona o campo e avisa "Não foi possível copiar. Selecione a chave e copie.";
  - texto sempre por `text`, nunca `html`.
- `app.js`:
  - rota `#/apoie`, que só existe se `session.contribution` (senão cai na Visão geral);
  - o link "Pagar um cafezinho?" é um `<a href="#/apoie">` de texto pequeno, sem ícone, logo abaixo do "Sair", só se `session.contribution`. Estilo novo `.foot-link` em `app.css`, discreto, com foco visível e alvo de toque confortável no celular.

## Testes

- **`ContributionIntegrationTest`** (novo, estende `AbstractIntegrationTest`, `@TestPropertySource` com chave fictícia e um PNG pequeno de teste em `src/test/resources`):
  - CA1: `/profile` traz `contributionEnabled = true` e `GET /contribution/pix` devolve a chave e o `qrImage`;
  - CA4: sem chave configurada, o endpoint responde `404` e `/profile` traz `false`;
  - sem login, `401`;
  - bordas: sem `qrFile`, arquivo inexistente ou grande demais devolve `qrImage = null` e `200`.
- **`OpenApiContractIntegrationTest`**: cobre a rota nova e os campos sozinho.
- **JS** (`contribution-view.test.mjs`):
  - CA2: o texto de gratuito vem antes da chave e do QR code;
  - CA3: "Copiar chave" copia e confirma; com a área de transferência recusando, avisa e mantém a chave selecionável;
  - borda: sem `qrImage`, nenhuma imagem quebrada;
  - CA4: `session.contribution` preenchida pelo perfil, em `api.test.mjs`.
- **CA5 e CA6** não têm teste automático próprio. CA5: o apoio não cria nenhum pop-up nem chamada fora da tela `#/apoie`, e isso é conferido na revisão. CA6: `grep` no repositório por chave Pix e arquivos de QR code, registrado em `tasks.md`.

## Riscos e alternativas descartadas

- Descartado: gerar o QR code no front. Exigiria um codificador de QR em JS puro, grande para manter e testar, e a imagem do banco já resolve.
- Descartado: imagem do QR dentro do repositório. A chave ficaria pública no git e no histórico.
- Descartado: rota de imagem protegida por Bearer. A tag `<img>` não envia o cabeçalho, então a tela teria que baixar como blob. O data URI é mais simples.
- Risco: a chave Pix aparece para qualquer pessoa logada. É o propósito dela (receber Pix), e o app não registra nada, então não há dado pessoal envolvido.
- Risco: `qrFile` aponta para um arquivo grande ou estranho. Mitigação: limite de 512 KB e só `png`/`jpg`.

## Deploy

- Sem migration. Mesmo assim, o `pg_dump` antes do deploy segue como manda o README.
- Variáveis novas, ambas opcionais:
  - `application-prod.properties`: `app.pix.key=${PIX_KEY:}` e `app.pix.qr-file=${PIX_QR_FILE:}`;
  - `.env.production.example` e `docker-compose.prod.yml`, sem `:?`, porque não são obrigatórias, e sem valor real no exemplo.
- Para o QR code funcionar, você coloca o arquivo no servidor e o monta como volume no container, apontando `PIX_QR_FILE` para ele. O Claude não mexe em segredos nem faz deploy: deixo o trecho do compose e o passo a passo no `tasks.md` para você executar.
- Rollback: imagem anterior. As variáveis ficam sem uso e não atrapalham.

## Verificação (ao implementar)

- Rodar `./mvnw verify` e `node --test "src/test/js/*.test.mjs"`.
- Checagem manual com o app local:
  - sem `PIX_KEY`: o link não aparece e `#/apoie` cai na Visão geral;
  - com `PIX_KEY` e `PIX_QR_FILE` apontando para uma imagem de teste: o link aparece sob o "Sair", a tela abre com o aviso de projeto gratuito primeiro, "Copiar chave" copia e confirma, e o QR code aparece;
  - sem a imagem: aparece só a chave;
  - conferir no celular (estreito) e no desktop.
- `grep` no repositório por chave Pix e imagem de QR code (CA6).
- Validar com as skills: `api-design-principles` na rota nova; `web-design-guidelines`, `accessibility-compliance` e `responsive-design` na tela e no link. Anotar em `tasks.md`.
- README: tabela da API, decisão de domínio (apoio voluntário por Pix, nada guardado), variáveis `PIX_KEY` e `PIX_QR_FILE` e o cliente web.
