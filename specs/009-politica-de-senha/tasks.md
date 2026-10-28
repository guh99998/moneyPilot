# 009 — Tarefas

**Plano:** [plan.md](plan.md)

- [x] **T1** — Spec com as decisões, plano e tarefas
- [x] **T2** — Senha dos testes de integração trocada por uma frase fora da lista; testes de tamanho em 10
- [x] **T3** — Lista `common-passwords.txt`, `CommonPasswords`, `NewPasswordValidator` com mínimo 10, lista e sequência, `/data/**` público (CA1, CA2, CA4)
- [x] **T4** — `PasswordPolicy` e `WeakPasswordException` no cadastro, na troca e no reset (CA3)
- [x] **T5** — Testes: `PasswordPolicyIntegrationTest` (CA1–CA5), `CommonPasswordsTest`, `PasswordPolicyTest`
- [x] **T6** — `js/password.js` (`assess`, `loadCommonPasswords`, `passwordInput`) + `password.test.mjs` (CA6, CA7)
- [x] **T7** — Olho e medidor em `auth.js`, `reset-password.js` e `profile.js`, CSS, + testes de tela (CA6, CA7)
- [x] **T8** — README (regra de senha, lista e fonte, `/data/common-passwords.txt`, campo de senha)
- [ ] **T9** — Conferir no navegador (1280px e 390px) o olho, o medidor e as mensagens no cadastro, no reset e no perfil (Gustavo)
- [ ] **T10** — Backup antes do deploy (Gustavo), mesmo sem migration

## Notas de implementação
- T3: das 10 mil senhas mais usadas do SecLists, só 51 têm 10 ou mais caracteres; com a lista em português o arquivo tem 137 senhas. Ver "Riscos" no plano.
- T4: a parte do e-mail antes do `@` também só conta com 4+ caracteres (a spec só falava do nome); o e-mail inteiro conta sempre.
- Revisão com as skills do projeto (`web-design-guidelines`, `accessibility-compliance`, `responsive-design`, `api-design-principles`): ícone do olho com `aria-hidden`, campo sem corretor nem maiúscula automática (a senha pode estar à mostra), medidor sem animação de `width`, alvo de toque de 44px, texto do medidor quebrando em telas estreitas. O medidor **não** é `aria-live` de propósito (não anunciar a cada tecla); a dica chega pelo `aria-describedby`. Rotas: nenhuma nova, só novos motivos de `400` no formato `ErrorResponse` com erro no campo. Pendente: o botão de olho muda o `aria-label` e também o `aria-pressed`, como no plano; vale conferir com leitor de tela se a dupla informação confunde.
- T7: `fieldRow` do perfil passou a ligar o rótulo ao campo (`for`), e a mensagem de erro de `newPassword` é a do servidor.
