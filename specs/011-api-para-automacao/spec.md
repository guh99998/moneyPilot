# 011 — API fácil para automação com IA

**Status:** aprovado
**Branch:** feature/011-api-para-automacao

## Problema

A API já é aberta (o Swagger em `/swagger-ui.html` e o contrato em `/v3/api-docs` são públicos), mas usá-la fora do navegador é difícil. Quem quer pedir a um assistente de IA "lance R$ 50 no mercado" esbarra em três coisas:

- o contrato não tem descrições nem exemplos, e a IA tem de adivinhar os campos e os valores aceitos;
- a sessão dura 15 minutos e a renovação depende de um cookie `httpOnly` `SameSite=Strict`, pensado para o navegador;
- contas criadas só com o Google não têm senha, então não conseguem entrar pela API.

## Objetivo

Uma pessoa (ou o assistente de IA dela) entende a API lendo só a documentação e consegue lançar, consultar e manter uma sessão sem truques. Sem tokens de API pessoais: a autenticação continua sendo a do app.

## Regras de negócio

- **Documentação para máquina e para gente:**
  - cada rota com resumo, descrição, exemplo de requisição e de resposta, e os códigos de erro possíveis;
  - valores fechados documentados como enum (tipos de conta, de categoria, de lançamento, status);
  - dinheiro documentado como número com 2 casas e ponto decimal, e datas como `aaaa-mm-dd`.
- **Página "Usando com IA":** página pública no app, na rota `#/usando-com-ia`, em português, com o passo a passo (entrar, renovar, lançar uma despesa, listar contas e categorias, sair) e um texto pronto para colar no assistente. Tem item no rodapé do menu e link no login. O README só aponta para ela.
- **Sessão para clientes que não são navegador:**
  - o cliente pede o refresh token no corpo com o campo opcional `refreshTokenInBody: true` no corpo do login. Sem o campo, ou com `false`, nada muda;
  - nesse modo, a resposta traz `token`, `refreshToken` e `expiresIn` (segundos até o access token vencer) e **não** envia `Set-Cookie`;
  - a renovação e o logout aceitam `{refreshToken}` no corpo. Mandar o `refreshToken` no corpo da renovação já liga o modo corpo;
  - se vierem cookie e corpo, **vale o corpo**: o cookie é ignorado e a resposta sai sem `Set-Cookie`;
  - a rotação e o reuso são os mesmos do cookie;
  - o navegador continua só com o cookie, e a resposta dele segue sem o refresh token.
- **Contas só com Google:** para usar a API, a pessoa cria uma senha pelo "Criar senha" do perfil (003/004). A página "Usando com IA" explica isso.
- **Troca de senha:** continua como está e encerra todas as sessões, inclusive as da API. A página "Usando com IA" avisa isso.
- **Dinheiro nas bordas da API:** todo campo de dinheiro recusa mais de 2 casas decimais, com erro no campo. Hoje só as parcelas recusam; nos outros campos o banco arredonda sem avisar. Valor com vírgula (texto como `"10,50"`) também vira erro no campo.
- **Limites:** o login segue com os limites atuais (10 por IP e 3 falhas por e-mail a cada 15 minutos). As rotas de dados não ganham limite novo.
- Nada muda no isolamento: cada sessão só enxerga os dados da própria conta.

## Critérios de aceite

- [x] **CA1** — Dado o contrato `/v3/api-docs`, quando lido, então toda rota tem resumo, exemplo de requisição e resposta e os erros possíveis, e os enums aparecem como valores fechados.
- [x] **CA2** — Dado um cliente fora do navegador, quando entra pedindo o refresh token no corpo, então recebe o access token, o refresh token e o `expiresIn` na resposta, sem `Set-Cookie`.
- [x] **CA3** — Dado esse refresh token, quando o envia no corpo da renovação, então recebe um novo par, e o antigo deixa de valer (mesma rotação do cookie).
- [x] **CA4** — Dado o navegador, quando entra como hoje, então nada muda: o refresh token continua só no cookie.
- [x] **CA5** — Dada a página "Usando com IA", quando aberta sem login, então mostra o passo a passo e o texto para o assistente.
- [x] **CA6** — Dado um refresh token, quando enviado no corpo do logout, então ele é revogado e deixa de renovar a sessão.
- [x] **CA7** — Dado qualquer request com dinheiro, quando o valor tem mais de 2 casas ou vírgula, então responde `400` com erro no campo.

## Casos de borda

- Refresh token reutilizado pelo corpo: derruba todas as sessões do usuário, como no cookie
- Cliente que manda cookie e corpo ao mesmo tempo: vale o corpo
- Conta só com Google tentando entrar pela API: `401`, e a documentação aponta o "Criar senha"
- Valores de dinheiro com vírgula ou mais de 2 casas: recusados com erro no campo em todos os campos de dinheiro

## Fora de escopo

- Tokens de API pessoais (decidido: não fazer)
- OAuth para terceiros ("Entrar com moneyPilot")
- Servidor MCP
- Mudar os limites das rotas de dados
- CORS: continua fechado, sem liberar chamadas de páginas de outros domínios
- Troca de senha manter a sessão da API: ela continua encerrando as sessões
