# 017 — Painel de chamados do suporte

**Status:** entregue
**Branch:** feature/017-painel-de-chamados
**Depende de:** 010 (falar com o suporte)

## Problema

Hoje cada chamado do "Falar com o suporte" (010) vira só um e-mail. Nada fica guardado no app: o administrador não vê quais chamados estão em aberto, não marca o que já resolveu e depende da caixa de entrada para saber o que falta atender. A 010 deixou isso fora de escopo de propósito; esta spec muda essa decisão.

## Objetivo

O administrador vê, dentro do app, a lista de chamados com o status de cada um, abre um chamado para ler e muda o status (por exemplo, de aberto para resolvido). A pessoa que abriu o chamado continua sendo respondida por e-mail, como hoje.

## Regras de negócio

- **Chamado guardado:** todo chamado enviado pelo "Falar com o suporte" passa a ser gravado no banco com: número de referência (o mesmo do e-mail), categoria, mensagem, e-mail para a resposta, conta que abriu (quando logada), data e hora, status e se o e-mail ao suporte foi enviado. O e-mail para o suporte continua saindo como hoje.
- **Falha no e-mail:** se o e-mail ao suporte falhar, o chamado fica gravado mesmo assim. A resposta é `200` com a referência, a pessoa vê "recebido" e não precisa reenviar. O painel marca o chamado como "e-mail não enviado", para o administrador saber que precisa olhar. Isso muda o CA5 da 010 (que dava erro `502` e não gravava nada).
- **Limite de envios:** como o chamado foi recebido, ele conta no limite da 010 sempre que é gravado.
- **Status:** `Aberto` → `Em andamento` → `Resolvido`. Todo chamado novo nasce `Aberto`. O administrador pode mudar para qualquer status, inclusive reabrir um resolvido. Cada mudança grava quando aconteceu.
- **Quem é administrador:** a conta cujo e-mail está na variável de ambiente `ADMIN_EMAILS`, uma lista separada por vírgula, nunca no código, porque o repositório é público. O e-mail é comparado sem diferenciar maiúsculas de minúsculas. Variável vazia ou ausente: ninguém é administrador e o painel não aparece. Não há perfis nem permissões além disso.
- **Acesso ao painel:** só o administrador logado vê o painel e as rotas dele. Sem login, as rotas respondem `401`, como toda rota protegida. Logado sem ser administrador, respondem `404`, como um recurso que não existe (segue a regra de isolamento: não revelar o que existe).
- **Painel (`#/admin/chamados`):**
  - lista dos chamados, mais recentes primeiro, com referência, categoria, status, e-mail, data e a marca "e-mail não enviado" quando for o caso;
  - filtro por status, abrindo em `Aberto` por padrão;
  - ao abrir um chamado: a mensagem completa e o botão para mudar o status;
  - item no menu só para o administrador.
- **Mensagem é texto da pessoa:** exibida como texto puro, nunca como HTML.
- **Responder:** continua pelo e-mail (o `Reply-To` já aponta para a pessoa). O painel não envia respostas.
- **Chamados antigos:** os enviados antes desta spec só existem no e-mail e não entram no painel.
- **Exclusão de conta (LGPD):** os chamados ligados a uma conta são apagados junto com ela. A 017 só cria o vínculo; quem apaga é a 012.

## Critérios de aceite

- [ ] **CA1** — Dado um chamado enviado (logado ou não), quando o envio dá certo, então ele fica gravado com status `Aberto` e a mesma referência do e-mail.
- [ ] **CA2** — Dado o administrador logado, quando abre o painel, então vê os chamados em aberto, mais recentes primeiro, e pode filtrar por status.
- [ ] **CA3** — Dado o administrador, quando muda o status de um chamado, então o novo status fica gravado e aparece na lista.
- [ ] **CA4** — Dada uma pessoa sem login, quando acessa as rotas do painel, então recebe `401`; dada uma pessoa logada que não é administradora, recebe `404` e o item do menu não aparece.
- [ ] **CA5** — Dado o app sem `ADMIN_EMAILS`, quando qualquer pessoa entra, então ninguém vê o painel e o "Falar com o suporte" continua funcionando.
- [ ] **CA6** — Dada uma mensagem com HTML ou script, quando aparece no painel, então é exibida como texto.
- [ ] **CA7** — Dado o e-mail ao suporte falhando, quando a pessoa envia, então o chamado fica gravado como `Aberto`, marcado como e-mail não enviado, e a pessoa vê a confirmação.

## Casos de borda

- Administrador troca o próprio e-mail (008): deixa de bater com `ADMIN_EMAILS` até a variável ser atualizada
- Mudar para o mesmo status que já tem: aceito, sem efeito
- Status inválido no request: `400` com erro no campo
- Chamado inexistente: `404`
- Pessoa que abriu o chamado depois apaga a conta (012): os chamados dela são apagados junto

## Fora de escopo

- Responder pelo painel ou guardar o histórico da conversa
- A pessoa ver os próprios chamados ou o status deles no app
- Avisar a pessoa por e-mail quando o status muda
- Perfis e permissões (vários administradores com papéis diferentes)
- Importar os chamados antigos do e-mail
- Anexos e capturas de tela
