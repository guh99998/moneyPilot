# 011 — Tarefas

**Plano:** [plan.md](plan.md)

- [x] **T1** — `LoginRequest.refreshTokenInBody`, `RefreshRequest`, `AuthResponse` com `refreshToken`/`expiresIn`, `JwtService.expirationSeconds`, `UserController` login/refresh/logout (CA2, CA3, CA4, CA6)
- [x] **T2** — Testes `ApiSessionIntegrationTest` (CA2, CA3, CA4, CA6)
- [x] **T3** — `@Digits(fraction = 2)` em todo campo de dinheiro e tratamento de formato ilegível no `GlobalExceptionHandler` (CA7)
- [x] **T4** — Teste `MoneyScaleIntegrationTest` (CA7)
- [x] **T5** — `OpenApiConfig`, `@Tag`/`@Operation`/`@ApiResponse` nos controllers, `@Schema` nos records e enums (CA1)
- [x] **T6** — Teste `OpenApiContractIntegrationTest` (CA1)
- [ ] **T7** — Tela `#/usando-com-ia`, rota, item no menu, link no login (CA5)
- [ ] **T8** — Testes JS (CA5)
- [ ] **T9** — README: API, decisões de domínio, regra das 2 casas e link para a página
- [ ] **T10** — Validação com as skills do projeto (`api-design-principles`, `web-design-guidelines`, `accessibility-compliance`, `responsive-design`)

## Notas de implementação

- `TransactionFilterType` e `BillStatusFilter` só são usados como parâmetro de query, então o springdoc os deixa inline (valores fechados no parâmetro) em vez de componente. O teste de contrato exige os valores fechados nesse caso; os outros sete enums são componentes.
- O teste de contrato dispensa de resposta de erro só a rota pública sem nenhuma entrada (`GET /auth/google/config`). As classes `Page*Response` do Spring ficam fora da checagem de exemplos.
- `POST /auth/logout` documenta o `400` de corpo ilegível, a única falha possível dela.
- O `application-*.properties` de teste usa `jwt.expiration-ms` de 1 hora, então os testes comparam `expiresIn` com `JwtService.expirationSeconds()`.
