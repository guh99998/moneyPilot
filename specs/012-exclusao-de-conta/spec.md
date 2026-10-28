# 012 — Exclusão de conta

**Status:** rascunho
**Branch:** feature/012-exclusao-de-conta
**Depende de:** 004 (login com Google), 006 (perfil)

## Problema

Quem não quer mais usar o app não tem como apagar a conta e os dados. Além de esperado pelo usuário, é um direito previsto na LGPD.

## Objetivo

A pessoa apaga a própria conta pelo perfil, confirmando que é ela, e todos os dados dela deixam de existir.

## Regras de negócio

- **Onde:** um cartão "Excluir conta" no fim do perfil, com o aviso de que a ação não pode ser desfeita.
- **Confirmação (decidido na 004):**
  - conta com senha: informa a senha atual;
  - conta só com Google: confirma entrando de novo com o Google (a conta Google precisa ser a vinculada).
  - Proposta: além disso, digitar uma palavra de confirmação (ex.: "EXCLUIR").
- **O que é apagado (proposta):** tudo o que é da conta: contas bancárias, categorias próprias, lançamentos, orçamentos, títulos e recorrências, alertas dispensados, sessões e todos os tokens (verificação, reset, troca de e-mail). Categorias globais ficam.
- **Quando (proposta):** na hora, sem período de arrependimento.
- **Depois:** a sessão atual e as outras são encerradas, a pessoa volta para a tela de login com a mensagem "Sua conta foi excluída". Um e-mail de aviso vai para o endereço da conta.
- O e-mail fica livre para um novo cadastro.
- **Limite:** senha atual errada conta no mesmo limite da troca de senha (5 em 15 minutos).

## Critérios de aceite

- [ ] **CA1** — Dada uma conta com senha, quando exclui informando a senha certa, então a conta e todos os dados dela deixam de existir e a pessoa sai do app.
- [ ] **CA2** — Dada a senha errada, quando tenta excluir, então nada é apagado e aparece o erro no campo.
- [ ] **CA3** — Dada uma conta só com Google, quando confirma com a conta Google vinculada, então a conta é excluída; com outra conta Google, é recusada.
- [ ] **CA4** — Dada uma conta excluída, quando alguém tenta entrar com o e-mail dela, então recebe o mesmo `401` de credenciais inválidas, e o e-mail pode ser cadastrado de novo.
- [ ] **CA5** — Dadas sessões abertas em outros aparelhos, quando a conta é excluída, então elas caem na próxima requisição.
- [ ] **CA6** — Dada a exclusão, quando concluída, então chega um aviso ao e-mail da conta.

## Casos de borda

- Falha no envio do aviso: a exclusão vale do mesmo jeito (o aviso é só informativo)
- Conta com muitos dados (milhares de lançamentos): a exclusão termina numa transação só
- Links de verificação, reset ou troca de e-mail pendentes: deixam de valer
- Access token ainda válido (até 15 minutos) depois da exclusão: recusado, porque a conta não existe mais
- Backup diário: os dados ainda aparecem nos backups até eles serem substituídos

## Fora de escopo

- Exportar os dados antes de excluir (pode ser spec própria, também da LGPD)
- Desativar a conta temporariamente
- Exclusão feita pelo suporte

## Perguntas em aberto

- Exclusão na hora ou com prazo de arrependimento (ex.: 7 dias, com link para cancelar)?
- Quer a palavra de confirmação ("EXCLUIR") além da senha ou do Google?
- Quer perguntar o motivo (opcional) antes de excluir? Se sim, para onde vai (e-mail do suporte da 010)?
- Por quanto tempo os backups guardam dados? Isso precisa aparecer na política de privacidade (C4).
