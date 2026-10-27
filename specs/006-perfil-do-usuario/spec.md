# 006 — Perfil do usuário

**Status:** rascunho
**Branch:** feature/006-perfil-do-usuario

## Problema

Hoje o usuário não consegue ver nem alterar os próprios dados. Quem errou o nome no cadastro fica com ele errado para sempre, e quem desconfia que a senha vazou não tem como trocá-la.

## Objetivo

O usuário tem uma tela de perfil onde vê os seus dados, corrige o nome e troca a senha sem precisar de ajuda.

## Regras de negócio

- A tela de perfil mostra nome, e-mail e data de cadastro. O e-mail aparece só para leitura; a troca dele é a spec 008.
- **Nome:** obrigatório, sem espaços nas pontas, até 255 caracteres. Não existe apelido único; o login continua pelo e-mail.
- **Senha:** para trocar, a pessoa informa a senha atual e a nova. A nova segue as regras do cadastro (mínimo de 8 caracteres).
- Depois de trocar a senha, **todas as outras sessões são encerradas** e a sessão atual continua aberta.
- Senha atual errada não altera nada e mostra um erro claro.
- Cada pessoa só vê e altera o próprio perfil.

## Critérios de aceite

- [ ] **CA1** — Dado um usuário logado, quando abre o perfil, então vê o próprio nome, e-mail e data de cadastro.
- [ ] **CA2** — Dado um nome novo válido, quando salva, então o nome muda no perfil e em todo lugar do app que o exibe.
- [ ] **CA3** — Dado um nome vazio ou só com espaços, quando tenta salvar, então é recusado com erro no campo.
- [ ] **CA4** — Dada a senha atual certa e uma nova válida, quando troca a senha, então o próximo login funciona com a nova e não com a antiga.
- [ ] **CA5** — Dada a senha atual errada, quando tenta trocar, então nada muda e aparece o erro.
- [ ] **CA6** — Dada uma sessão aberta em outro aparelho, quando a senha é trocada, então essa sessão é encerrada na próxima renovação, e a sessão de onde a troca foi feita continua funcionando.

## Casos de borda

- Nova senha igual à atual
- Nova senha com menos de 8 caracteres
- Tentativas repetidas de senha atual errada (limitadas pela 005)
- Duas abas do mesmo navegador: a outra aba compartilha a sessão atual e continua logada

## Fora de escopo

- Trocar o e-mail (spec 008)
- Apelido ou nome de usuário único
- Foto de perfil
- Excluir a conta e exportar os dados (Etapa 2, C4)
- Definir senha para conta criada pelo Google (tratado na 003 e na 004)

## Perguntas em aberto

- Recusar a nova senha quando for igual à atual? (recomendado: sim, com mensagem clara)
- Onde fica o acesso ao perfil: um item no menu de navegação ou o nome do usuário no topo, clicável?
