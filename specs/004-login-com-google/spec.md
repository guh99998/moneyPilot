# 004 — Login com Google, vínculo no perfil e foto

**Status:** em implementação
**Branch:** feature/004-login-com-google
**Roteiro:** D5 · **Depende de:** 002, 003

## Problema

Criar conta com e-mail e senha, e depois verificar o e-mail, é atrito para quem só quer experimentar o app.

## Objetivo

A pessoa entra com a conta Google em um clique, na primeira vez e nas seguintes, e pode vincular ou desvincular o Google depois, pelo perfil. A foto do Google aparece no perfil e no menu.

## Regras de negócio

**Entrar com Google**
- E-mail do Google **ainda não cadastrado**: cria a conta já verificada, sem senha, com o nome vindo do Google.
- E-mail do Google **já cadastrado**: entra na conta existente e a vincula ao Google. Os dados continuam os mesmos.
- Só aceita contas Google com e-mail verificado pelo próprio Google.
- A conta é identificada pelo identificador do Google (`sub`), não pelo e-mail. Se a pessoa mudar o e-mail no Google depois de vincular, continua entrando na mesma conta.
- E-mail do Google que pertence a uma conta já vinculada a **outro** Google: o login é recusado (`409`).
- Conta **não verificada** (002) que entra pelo Google passa a ser verificada, e a **senha antiga é apagada**. Alguém pode ter se cadastrado com o e-mail de outra pessoa; se a senha ficasse, essa pessoa manteria o acesso.
- A sessão criada pelo Google é igual à do login por senha (mesma duração e renovação).
- O botão "Entrar com Google" aparece no login e no cadastro. Sem o recurso configurado, o botão não aparece.

**Conta sem senha**
- Login por senha numa conta só com Google responde o mesmo `401` genérico de senha errada. A tela de login sempre mostra a dica "Criou a conta com o Google? Use Entrar com Google."
- A conta sem senha passa a ter senha pelo reset (003). No perfil, o botão "Criar senha" envia o link.
- Trocar senha e trocar e-mail exigem a senha atual; quem não tem senha precisa criar uma antes.

**Vincular e desvincular (perfil)**
- Vincular pede a senha atual. O e-mail da conta Google pode ser diferente do e-mail da conta, porque o vínculo é pelo `sub`.
- Um mesmo Google não vincula a duas contas, e uma conta não vincula a dois Googles (`409`).
- Desvincular só é permitido se a conta tem senha, e pede a senha atual.

**Foto**
- Guardamos só a URL da foto do Google, atualizada a cada entrada com o Google.
- Aparece no perfil e no rodapé do menu; quem não tem foto vê as iniciais.
- A pessoa pode **remover** a foto (volta às iniciais e ela não é puxada de novo) e pedir "Usar foto do Google" para voltar.

## Critérios de aceite

- [ ] **CA1** — Dada uma conta Google com e-mail que não existe no app, quando a pessoa entra com Google, então a conta é criada, verificada, sem senha, e ela cai no painel.
- [ ] **CA2** — Dada uma conta Google com o mesmo e-mail de uma conta existente, quando a pessoa entra com Google, então acessa a conta existente com todos os dados.
- [ ] **CA3** — Dada uma conta criada pelo Google, quando a pessoa tenta logar por senha, então recebe o mesmo `401` de senha errada, e a tela orienta a usar o Google.
- [ ] **CA4** — Dado um token do Google inválido, expirado ou emitido para outro app, quando é enviado ao backend, então o login é recusado.
- [ ] **CA5** — Dada uma conta logada com senha, quando vincula o Google informando a senha atual certa, então passa a entrar também com o Google; com a senha errada, é recusada.
- [ ] **CA6** — Dada uma conta com senha e Google vinculado, quando desvincula informando a senha atual, então o vínculo e a foto do Google são removidos; sem senha na conta, é recusado.
- [ ] **CA7** — Dada uma conta com foto do Google, quando a pessoa remove a foto, então ela não volta ao entrar de novo, até pedir "Usar foto do Google".
- [ ] **CA8** — Dada uma conta só com Google, quando a pessoa cria uma senha pelo reset, então passa a entrar também por senha.

## Casos de borda

- Conta existente ainda não verificada (002) entrando pelo Google: passa a ser verificada e a senha antiga para de valer
- Pessoa que muda o e-mail da conta Google depois de vincular: entra pelo `sub`
- E-mail do Google igual ao de uma conta vinculada a outro Google: `409`
- Vincular com um Google de e-mail diferente do da conta: aceito
- Muitas tentativas por IP: `429`
- Script do Google bloqueado ou sem client ID: o botão some, o login por senha segue funcionando

## Fora de escopo

- Outros provedores (Apple, Microsoft, GitHub)
- Enviar foto própria
- Excluir a conta (spec própria; quem só tem Google vai confirmar entrando de novo com o Google)

## Notas para o plano

Decidido no roteiro: ID token validado no backend (assinatura, audience, expiração), sem dependência nova. O client ID do Google não é segredo e vem da variável `GOOGLE_CLIENT_ID`; o script oficial `accounts.google.com/gsi/client` está aprovado.
