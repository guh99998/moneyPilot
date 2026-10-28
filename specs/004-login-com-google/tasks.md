# 004 — Tarefas

**Plano:** [plan.md](plan.md)

- [x] **T1** — Spec com as decisões, plano e tarefas
- [x] **T2** — Migration V14, campos novos em `User` e `ProfileResponse`
- [x] **T3** — Validação do ID token: `GoogleTokenVerifier`, `JjwtGoogleTokenVerifier`, `GoogleKeySource`, configuração (CA4)
- [x] **T4** — `GoogleSignInService.signIn` e `POST /auth/google` + `GET /auth/google/config` (CA1, CA2, CA3)
- [x] **T5** — Vincular, desvincular e foto no perfil; ajustes para conta sem senha (CA5, CA6, CA7, CA8)
- [x] **T6** — Testes de integração e do verificador (CA1–CA8 e bordas)
- [x] **T7** — `api.js`, `google.js` + testes JS
- [x] **T8** — "Entrar com Google" em `auth.js` + testes de tela (CA1, CA3)
- [x] **T9** — Cartão Google, "Criar senha" e foto em `profile.js`; avatar no menu em `app.js`; CSS + testes de tela (CA5–CA8)
- [x] **T10** — Revisão com as skills do projeto
- [x] **T11** — README (API, decisões de domínio, V14, `GOOGLE_CLIENT_ID`)
- [ ] **T12** — Client OAuth, `GOOGLE_CLIENT_ID` e backup antes do deploy (Gustavo)

## Notas de implementação
- T3/T6: `HttpGoogleKeySource` (cache pelo `max-age`, nova busca no máximo uma por minuto para `kid` desconhecido) tem teste próprio com `MockRestServiceServer`; o verificador tem teste com chave RSA gerada e `Clock` fixo.
- T4: enquanto a foto está oculta, a URL guardada não é atualizada pelas entradas (regra do plano); "Usar foto do Google" volta a mostrar a última guardada. `ProfileResponse` ganhou também `avatarHidden`, para a tela saber quando oferecer "Usar foto do Google".
- T5: ID token recusado em rota autenticada (`/profile/google`) responde `400` no campo `credential`, não `401`, porque o cliente trata `401` como sessão expirada.
- T9: o cartão Google de quem não vinculou fica escondido até o botão do Google estar pronto; o de quem já vinculou aparece sempre, para dar para desvincular.
- T10: revisão com `web-design-guidelines`, `accessibility-compliance`, `responsive-design` e `api-design-principles`: avatar decorativo (`alt=""`, `aria-hidden`, `width`/`height`, `referrerpolicy`), separador "ou" escondido de leitores de tela, erros em `role=alert`, foco levado ao título do cartão depois de vincular/desvincular e à resposta de "Criar senha", estado "Enviando…", alvos de 44px nos botões novos (o botão do Google é um iframe de 40px, fora do nosso controle), conferido a 390px e 1280px. API: recursos como substantivos, `POST` com corpo para desvincular (evita `DELETE` com corpo), `204`/`409`/`429` coerentes, erros no `ErrorResponse`.
- Verificação manual: o fluxo real com o Google (CA1/CA2 no navegador, vincular com outro e-mail) precisa de um client ID de desenvolvimento e fica para o Gustavo; as telas foram conferidas localmente a 390px e 1280px nos estados sem vínculo, vinculada com foto e só com Google.
