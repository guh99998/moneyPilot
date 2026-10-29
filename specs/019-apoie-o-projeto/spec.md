# 019 — Apoie o projeto

**Status:** aprovada
**Branch:** feature/019-apoie-o-projeto
**Depende de:** nenhuma (combina com a 020, site do produto)

## Problema

O moneyPilot é gratuito e não tem planos de cobrança. Quem gosta do app e quer ajudar a manter o projeto (servidor, domínio, tempo de desenvolvimento) não tem como fazer isso.

## Objetivo

Quem quiser contribui com o valor que escolher, por Pix, de forma voluntária, sem que o app cobre, insista ou libere algo em troca. A tela deixa claro que o projeto é gratuito e que o apoio é só um agradecimento.

## Regras de negócio

- **Voluntário:** nada no app depende da contribuição, e quem contribui não ganha recurso extra. O app não cobra, não insiste e não lembra: sem pop-up, sem banner, sem aviso.
- **Só Pix.** O pagamento acontece todo fora do app. O app só mostra a chave e o QR code; não processa pagamento, não confirma e não guarda nada sobre quem contribuiu.
- **Onde aparece:** um link de texto pequeno no fim do rodapé do menu, logo abaixo do "Sair", para quem está logado, sem ícone e sem cara de item de navegação, com o texto "Pagar um cafezinho?". Ele abre a tela "Apoie o projeto" (`#/apoie`). Na primeira versão não há link no perfil nem no site; o site entra com a 020.
- **A tela mostra, nesta ordem:** primeiro, em destaque, que o moneyPilot é um projeto gratuito e sempre vai ser, e que apoiar é só um agradecimento opcional; depois a chave Pix e o QR code. O botão "Copiar chave" fica junto da chave. Valor livre, sem quantia sugerida (texto proposto: "O moneyPilot é gratuito e sempre vai ser. Se ele te ajuda e você quiser pagar um cafezinho para ajudar a manter o projeto, qualquer valor é bem-vindo. Totalmente opcional.").
- **Configuração fora do repositório:** a chave Pix é uma **chave aleatória** (nunca CPF, telefone ou e-mail pessoal) e vem de variável de ambiente, nunca do código, porque o repositório é público. A imagem do QR code também fica fora do git, no servidor. O valor é livre: nenhum valor é pré-preenchido.
- **Sem configuração, sem apoio:** se a chave não estiver configurada, o item do menu não aparece e a tela `#/apoie` cai na Visão geral. O resto do app não muda.
- **Apoio aberto (aditivo):** a tela e a rota não exigem login. Quem chega pelo site (020) vê a mesma tela, fora do menu do app, com um link para voltar. A chave Pix já é feita para ser mostrada a quem quer pagar, então expô-la não revela nada que o apoio não precise mostrar. Sem login, a tela só mostra a chave se ela estiver configurada; se não estiver, diz que o apoio não está disponível.
- **Sem registro:** o app não sabe quem contribuiu, quando nem quanto.

## Critérios de aceite

- [ ] **CA1** — Dado o app com a chave Pix configurada, quando uma pessoa logada abre o menu, então vê o link "Pagar um cafezinho?" sob o "Sair", e ele abre a tela "Apoie o projeto".
- [ ] **CA2** — Dada a tela aberta, então a primeira coisa que ela diz, antes da chave e do QR code, é que o projeto é gratuito e que o apoio é opcional; em seguida mostra a chave Pix e o QR code.
- [ ] **CA3** — Dado o botão "Copiar chave", quando a pessoa o aciona, então a chave vai para a área de transferência e a tela confirma que foi copiada.
- [ ] **CA4** — Dado o app sem a chave configurada, quando uma pessoa entra, então o link não aparece, `#/apoie` cai na Visão geral e o resto do app funciona igual.
- [ ] **CA5** — Dado qualquer uso do app, então nenhuma tela é bloqueada, limitada ou interrompida por causa do apoio (sem pop-up, banner ou lembrete).
- [ ] **CA7** — Dada uma pessoa sem login, quando abre `/app/#/apoie` (por exemplo, pelo rodapé do site), então vê a mesma tela, com a chave e o QR code e um link para voltar; `GET /contribution/pix` responde `200` sem token e `404` sem a chave configurada.
- [ ] **CA6** — Dado o repositório, então nenhum arquivo versionado contém a chave Pix nem a imagem do QR code; só o nome da variável de ambiente, com placeholder no exemplo.

## Casos de borda

- Imagem do QR code ausente com a chave configurada: a tela mostra só a chave e o botão, sem imagem quebrada
- Navegador que bloqueia a área de transferência: a chave fica visível e selecionável, e a tela avisa que a cópia não funcionou
- Celular: o botão "Copiar chave" é o caminho principal; o QR code serve para escanear de outro aparelho
- Pessoa sem login: não vê o item no menu (ela nem tem menu), mas a tela abre pelo link do site (aditivo, CA7)
- Chave Pix trocada no servidor: vale depois de reiniciar o app, sem novo build

## Fora de escopo

- Pagamento dentro do app, ou qualquer plataforma além do Pix (Buy Me a Coffee, Ko-fi, GitHub Sponsors)
- Saber, registrar ou confirmar quem contribuiu, quanto e quando
- Selo ou badge de contribuidor, data da contribuição no perfil e qualquer recompensa para quem apoiou
- Recibo e valor sugerido
- Link no perfil (o site 020 já aponta para a tela)

## Perguntas em aberto

- **Selo de contribuidor no perfil:** a ideia fica de fora desta spec. O motivo: o pagamento é Pix, fora do app, então o app não tem como saber quem pagou. Seria preciso você conferir cada pagamento e marcar a conta à mão, ligando o nome de quem pagou a uma conta, e isso guarda um dado financeiro de cada pessoa (LGPD) e quebra a regra de que apoiar não dá nada em troca. Se ainda quiser, vale como spec própria, depois da 022 (marcos de uso), que já cria as conquistas do perfil.
