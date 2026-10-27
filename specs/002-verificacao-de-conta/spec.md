# 002 — Verificação de conta

**Status:** rascunho
**Branch:** feature/002-verificacao-de-conta
**Roteiro:** D3 · **Depende de:** 001

## Problema

Hoje qualquer pessoa cria conta com um e-mail que não é dela. Isso abre espaço para contas falsas e torna o reset de senha (003) inseguro, porque o e-mail cadastrado pode nem ser do usuário. Além disso, `Fulano@x.com` e `fulano@x.com` contam como e-mails diferentes, e quem digita o e-mail com outra caixa não consegue entrar.

## Objetivo

Só entra no app quem provou ser dono do e-mail cadastrado, e o e-mail vale igual com qualquer combinação de maiúsculas.

## Regras de negócio

- Conta nova nasce **não verificada** e recebe um e-mail com link de verificação.
- Conta não verificada **não loga**: com a senha certa, o login é recusado com mensagem que explica o motivo e oferece reenviar o link. Com a senha errada, a resposta é a de sempre.
- O link vale por **24 horas** e só pode ser usado uma vez. Reenviar gera um link novo e invalida o anterior.
- Depois de verificar, a pessoa vê "Conta verificada" e vai para o login. O link **não** abre sessão.
- **Usuários que já existem em produção entram como verificados.** Ninguém que usa o app hoje pode ficar trancado para fora.
- O cadastro responde sempre a mesma mensagem ("enviamos um link para o seu e-mail"), exista ou não a conta:
  - e-mail livre: cria a conta não verificada e envia o link;
  - e-mail de conta **verificada**: nada é criado e o dono recebe um aviso de que tentaram criar uma conta com o e-mail dele;
  - e-mail de conta **não verificada**: a conta é reaproveitada com o nome e a senha do novo cadastro e um link novo é enviado. Quem não verificou nunca entrou, então não há dados a perder, e assim ninguém "segura" um e-mail alheio sem verificá-lo.
- O reenvio do link responde sempre a mesma mensagem, exista ou não a conta, para não revelar quais e-mails estão cadastrados. Para conta já verificada, o reenvio manda um e-mail dizendo que a conta já está ativa.
- **E-mail sem distinção de maiúsculas:** cadastro, login, reenvio e troca de e-mail gravam e buscam o e-mail sem espaços nas pontas e em minúsculas. Os e-mails já cadastrados são convertidos para minúsculas.
- Limites: cadastro segue o limite atual por IP; reenvio no máximo 3 por e-mail por hora e 10 por IP por hora; o aviso de "tentaram criar uma conta" no máximo 1 por e-mail por hora.

## Critérios de aceite

- [ ] **CA1** — Dado um cadastro novo, quando ele é concluído, então a conta fica não verificada e um e-mail com o link é enviado.
- [ ] **CA2** — Dada uma conta não verificada, quando a pessoa tenta logar com a senha certa, então o login é recusado com a explicação e a opção de reenviar.
- [ ] **CA3** — Dado um link válido, quando a pessoa clica, então a conta é verificada e ela consegue logar.
- [ ] **CA4** — Dado um link expirado ou substituído por um reenvio, quando a pessoa clica, então vê que o link não vale mais e pode pedir outro.
- [ ] **CA5** — Dados os usuários cadastrados antes desta feature, quando ela entra no ar, então todos continuam logando normalmente.
- [ ] **CA6** — Dado um e-mail não cadastrado, quando alguém pede reenvio, então a resposta é idêntica à de um e-mail cadastrado.
- [ ] **CA7** — Dado um cadastro com e-mail que já tem conta verificada, então a resposta é idêntica à de um cadastro novo, nenhuma conta é criada e o dono recebe o aviso.
- [ ] **CA8** — Dada uma conta com `Fulano@x.com`, quando a pessoa entra com `fulano@x.com` (ou o contrário), então o login funciona, e cadastrar `FULANO@x.com` é tratado como o mesmo e-mail.

## Casos de borda

- Clicar no link duas vezes: a segunda mostra "conta já verificada", não um erro.
- Clicar no link estando logado em outra conta: a verificação vale para a conta do link e a sessão aberta não muda.
- E-mail cadastrado com maiúsculas e minúsculas diferentes: tratado como o mesmo (CA8).
- Cadastro repetido para e-mail de conta não verificada: reaproveita a conta e o link anterior deixa de valer.
- Provedor de e-mail fora do ar no cadastro: o cadastro falha com o erro de envio e nada é gravado.

## Fora de escopo

- Trocar o e-mail da conta (spec 008)
- Excluir automaticamente contas que nunca verificaram
- Verificação por código numérico em vez de link
- Entrar logado direto pelo link de verificação

## Decisões das perguntas em aberto

- Cadastro com e-mail já existente: resposta genérica, com aviso ao dono (CA7).
- Depois de verificar: vai para o login, sem sessão.
- Normalizar para minúsculas: entra nesta spec, com migration que converte os e-mails existentes (CA8).
