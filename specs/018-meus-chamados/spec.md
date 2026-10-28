# 018 — Meus chamados

**Status:** proposta (aguardando aprovação)
**Branch:** feature/018-meus-chamados
**Depende de:** 007 (notificações), 010 (falar com o suporte), 017 (painel de chamados)

## Problema

Desde a 017, todo chamado fica gravado com status, mas só o administrador enxerga. Quem abriu o chamado não sabe se ele foi recebido, se alguém está olhando ou se já foi resolvido, e só descobre quando chega um e-mail.

## Objetivo

A pessoa logada vê, na própria tela "Falar com o suporte", os chamados que abriu e o andamento de cada um, e recebe um alerta no sino quando o status de um deles muda.

## Regras de negócio

- **Quais chamados:** só os ligados à conta (`user_id`). Chamados abertos sem login, mesmo com o e-mail da conta, e os anteriores à 017 não entram na lista. A tela avisa isso. Mostrar pelo e-mail seria arriscado: qualquer pessoa pode abrir um chamado informando o e-mail de outra.
- **Lista:** na tela `#/suporte`, abaixo do formulário, só para quem está logado. Cada chamado mostra referência, categoria, data de abertura, status (`Aberto`, `Em andamento`, `Resolvido`) e a data da última mudança de status. Mais recentes primeiro, paginada, 10 por página.
- **Detalhe:** ao abrir um chamado, a pessoa vê a mensagem que enviou, o e-mail que recebe a resposta e o texto "respondemos no seu e-mail". A resposta continua só por e-mail.
- **Mensagem é texto da pessoa:** exibida como texto puro, nunca como HTML.
- **E-mail não enviado:** a pessoa não vê. Só o painel do administrador mostra (017).
- **Reabrir:** não existe. `Resolvido` é final para a pessoa; o detalhe sugere abrir um chamado novo citando a referência. O administrador continua podendo reabrir pelo painel (017).
- **Alerta no sino (007):** qualquer mudança de status gera o alerta "Seu chamado K7P3XN está Em andamento" (ou "foi resolvido"). O alerta:
  - é dispensável e volta se o status mudar de novo;
  - some ao abrir o chamado no detalhe;
  - some sozinho 30 dias depois da mudança de status;
  - nunca existe para chamado que não mudou de status desde a abertura;
  - ao ser clicado, abre o detalhe do chamado em `#/suporte`.
- **Isolamento:** chamado de outra conta responde `404`, e sem login `401`. O administrador vê os próprios chamados com as mesmas regras; o painel dele (017) não muda.
- **Sem aviso por e-mail:** a mudança de status só avisa pelo sino.

## Critérios de aceite

- [ ] **CA1** — Dada uma pessoa logada, quando abre `#/suporte`, então vê só os chamados abertos com a conta dela, mais recentes primeiro, 10 por página.
- [ ] **CA2** — Dado um chamado aberto sem login com o e-mail da conta, quando a pessoa vê a lista, então ele não aparece.
- [ ] **CA3** — Dado um chamado da conta, quando a pessoa abre o detalhe, então vê a mensagem, o e-mail da resposta, o status e quando ele mudou, e não vê se o e-mail ao suporte falhou.
- [ ] **CA4** — Dado um chamado de outra conta, quando é pedido, então a resposta é `404`; sem login, `401`.
- [ ] **CA5** — Dado que o administrador muda o status de um chamado, quando a pessoa abre o app, então o sino mostra o alerta do chamado com o status novo.
- [ ] **CA6** — Dado um alerta de chamado, quando a pessoa o dispensa ou abre o chamado, então o alerta some; se o status mudar de novo, ele volta.
- [ ] **CA7** — Dado um alerta de chamado, quando passam 30 dias da mudança de status, então ele some sozinho. Chamado que nunca mudou de status não gera alerta.
- [ ] **CA8** — Dado um alerta de chamado, quando a pessoa clica nele, então o app abre o detalhe do chamado em `#/suporte`.

## Casos de borda

- Chamado aberto sem login com o mesmo e-mail da conta: não entra (CA2)
- Chamado anterior à 017 (só existe no e-mail): não entra
- Chamado com "e-mail não enviado": a pessoa vê como qualquer outro
- Conta que trocou de e-mail (008) depois de abrir o chamado: não afeta a lista, que usa o vínculo com a conta; o detalhe mostra o e-mail que foi informado na abertura
- Administrador abrindo os próprios chamados: mesmas regras de qualquer conta
- Status mudado para o mesmo valor que já tinha: não é mudança, não gera alerta nem renova os 30 dias (comportamento da 017)
- Administrador reabre um chamado resolvido: é uma mudança de status, gera alerta novo
- Conta excluída (012): os chamados e as dispensas somem junto

## Fora de escopo

- Conversa dentro do app (respostas e réplicas)
- Anexos
- Avaliação do atendimento
- Reabrir um chamado pela pessoa
- E-mail ao mudar o status
- Histórico de notificações e "lida/não lida" no sino (vai para a 023)
- Mostrar chamados sem login ou anteriores à 017
