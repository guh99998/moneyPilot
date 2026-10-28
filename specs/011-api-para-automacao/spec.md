# 011 — API fácil para automação com IA

**Status:** rascunho
**Branch:** feature/011-api-para-automacao

## Problema

A API já é aberta (o Swagger em `/swagger-ui.html` e o contrato em `/v3/api-docs` são públicos), mas usá-la fora do navegador é difícil. Quem quer pedir a um assistente de IA "lance R$ 50 no mercado" esbarra em três coisas:

- o contrato não tem descrições nem exemplos, e a IA tem de adivinhar os campos e os valores aceitos;
- a sessão dura 15 minutos e a renovação depende de um cookie `httpOnly` `SameSite=Strict`, pensado para o navegador;
- contas criadas só com o Google não têm senha, então não conseguem entrar pela API.

## Objetivo

Uma pessoa (ou o assistente de IA dela) entende a API lendo só a documentação e consegue lançar, consultar e manter uma sessão sem truques. Sem tokens de API pessoais: a autenticação continua sendo a do app.

## Regras de negócio

- **Documentação para máquina e para gente (proposta):**
  - cada rota com resumo, descrição, exemplo de requisição e de resposta, e os códigos de erro possíveis;
  - valores fechados documentados como enum (tipos de conta, de categoria, de lançamento, status);
  - dinheiro documentado como texto/número com 2 casas e datas como `aaaa-mm-dd`.
- **Página "Usando com IA" (proposta):** página pública no app, em português, com o passo a passo (entrar, renovar, lançar uma despesa, listar contas e categorias) e um texto pronto para colar no assistente.
- **Sessão para clientes que não são navegador (proposta):** o login e a renovação passam a aceitar, quando o cliente pede (ex.: cabeçalho ou campo próprio), o refresh token **no corpo** da resposta e na requisição, além do cookie. O navegador continua só com o cookie.
- **Contas só com Google:** para usar a API, a pessoa cria uma senha pelo "Criar senha" do perfil (003/004). A página "Usando com IA" explica isso.
- **Limites:** o login segue com os limites atuais (10 por IP e 3 falhas por e-mail a cada 15 minutos). As rotas de dados não têm limite hoje; a spec não muda isso, a menos que você queira.
- Nada muda no isolamento: cada sessão só enxerga os dados da própria conta.

## Critérios de aceite

- [ ] **CA1** — Dado o contrato `/v3/api-docs`, quando lido, então toda rota tem resumo, exemplo de requisição e resposta e os erros possíveis, e os enums aparecem como valores fechados.
- [ ] **CA2** — Dado um cliente fora do navegador, quando entra pedindo o refresh token no corpo, então recebe o access token e o refresh token na resposta.
- [ ] **CA3** — Dado esse refresh token, quando o envia no corpo da renovação, então recebe um novo par, e o antigo deixa de valer (mesma rotação do cookie).
- [ ] **CA4** — Dado o navegador, quando entra como hoje, então nada muda: o refresh token continua só no cookie.
- [ ] **CA5** — Dada a página "Usando com IA", quando aberta sem login, então mostra o passo a passo e o texto para o assistente.

## Casos de borda

- Refresh token reutilizado pelo corpo: derruba a família inteira, como no cookie
- Cliente que manda cookie e corpo ao mesmo tempo
- Conta só com Google tentando entrar pela API: `401`, e a documentação aponta o "Criar senha"
- Valores de dinheiro com vírgula ou mais de 2 casas: recusados com erro no campo, como hoje

## Fora de escopo

- Tokens de API pessoais (decidido: não fazer)
- OAuth para terceiros ("Entrar com moneyPilot")
- Servidor MCP
- Mudar os limites das rotas de dados

## Perguntas em aberto

- Aceita devolver o refresh token no corpo para clientes fora do navegador? É a mudança que mais facilita, mas expõe ao cliente um token de 30 dias. Sem ela, a IA precisa da senha a cada 15 minutos.
- CORS: quer liberar chamadas de páginas de outros domínios (ferramentas de IA que rodam no navegador)? Se sim, para quais origens, ou todas?
- A página "Usando com IA" fica dentro do app (rota pública) ou só no README?
- Quer um limite próprio para uso programático nas rotas de dados (ex.: X lançamentos por minuto), para proteger a VPS?
