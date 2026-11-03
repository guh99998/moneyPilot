# 012 — Tarefas

**Plano:** [plan.md](plan.md)

- [x] **T1** — Exceção `GoogleAccountMismatchException` + `GlobalExceptionHandler` (CA3)
- [x] **T2** — Queries `@Modifying` de remoção por usuário nos repositórios de bills, recorrências, transações, orçamentos, alertas dispensados, chamados, categorias, contas e tokens (CA1)
- [x] **T3** — `DeleteAccountRequest` e `AccountDeletionService` (senha, Google, transação, aviso depois do commit) (CA1–CA6)
- [x] **T4** — Rota `POST /api/v1/profile/deletion` com OpenAPI completo e limpeza do cookie de refresh (CA1, CA2, CA5)
- [x] **T5** — Testes de integração (CA1–CA6, isolamento) e `OpenApiContractIntegrationTest`
- [x] **T6** — `api.profile.deleteAccount` e cartão "Excluir conta" em `profile.js` com despedida, aviso e campo "EXCLUIR" (CA7)
- [x] **T7** — Saída da sessão e mensagem "Sua conta foi excluída" no login (CA1)
- [x] **T8** — Testes JS (CA7 e cliente da API)
- [x] **T9** — Atualizar `ajuda/perfil-e-seguranca.html`
- [x] **T10** — README: API e decisão de domínio
- [x] **T11** — Validação com as skills do projeto (`api-design-principles`, `web-design-guidelines`, `accessibility-compliance`, `responsive-design`)

## Notas de implementação

Nenhuma descoberta alterou a spec. Detalhes:

- A confirmação segue o tipo da conta: conta com senha exige `currentPassword` e recusa `credential`; conta só com Google faz o contrário. Campo ausente ou do tipo errado é `400` no campo.
- A exclusão usa `TransactionTemplate` (e não `@Transactional` no método) para o e-mail de aviso sair só depois do commit.
- O cartão do perfil está num módulo próprio, `js/views/profile-delete.js`. O login recebe o aviso "Sua conta foi excluída" pelo evento `account:deleted`, mostrado uma vez.
- Fora da spec, a pedido, em outra branch (`fix/seo-public-pages`): `robots.txt`, `sitemap.xml`, Open Graph da ajuda e contraste do `--text-muted`.

**Revisão com as skills do projeto**

- `api-design-principles`: rota de ação sobre o perfil, no padrão de `/profile/google/unlink` (POST com corpo, porque a confirmação vai junto e `DELETE` com corpo não é confiável). Status corretos (`204`, `400`, `401`, `429`), erro no formato `ErrorResponse`, limite de tentativas e OpenAPI completo, verificado pelo `OpenApiContractIntegrationTest`. Sem campo de dinheiro.
- `web-design-guidelines` e `accessibility-compliance`: rótulos em todos os campos, `autocomplete="off"` e `spellcheck` desligado na palavra, placeholder com reticências, erro de campo com foco no campo e alerta geral com `role="alert"`, estado de envio ("Excluindo…"), emoji com `aria-hidden`, aviso do login com `role="status"` e foco. Corrigido: contraste do texto de apoio do cartão (abaixo de 4,5:1). Conferido que o botão desabilitado até digitar EXCLUIR é exigência da spec.
- `responsive-design`: mobile-first, botão e campos com 44px, botão em largura total no celular e automático a partir de 600px, texto que quebra sem estourar, sem larguras fixas.
- SEO: `app/index.html` com `noindex` e descrição; ajuda do perfil atualizada.
