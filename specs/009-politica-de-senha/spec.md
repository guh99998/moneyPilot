# 009 — Política de senha

**Status:** em implementação
**Branch:** feature/009-politica-de-senha
**Depende de:** 003 (reset usa as mesmas regras), fix do limite de 72 bytes (`@NewPassword`)

## Problema

A senha nova só precisa ter 8 caracteres. Senhas como `12345678`, `senha123` ou o próprio e-mail passam, e são as primeiras que alguém tenta numa conta alheia. Num app com dados financeiros, a senha é hoje o único fator de acesso.

Exigir maiúscula, número e símbolo não resolve: leva a senhas previsíveis como `Senha@123`. O que protege é tamanho e não usar senhas conhecidas.

## Objetivo

Toda senha nova tem pelo menos 10 caracteres e não é uma senha comum nem óbvia para a pessoa. Enquanto digita, ela vê o quanto a senha está forte e pode mostrar o que digitou.

## Regras de negócio

- Valem para **toda senha nova**: cadastro, troca de senha no perfil e reset. Num lugar só, para as três telas não divergirem.
- **Tamanho:** de **10** a 64 caracteres (antes 8 a 64). O limite de 72 bytes do bcrypt continua.
- **Sem regra de composição:** não se exige maiúscula, número nem símbolo. Qualquer caractere vale, inclusive espaço e acento, para permitir frases.
- **Senha vazada é recusada:** a senha, sem diferença de maiúsculas, não pode estar numa lista local de senhas de **vazamentos públicos** (as cerca de 10 mil mais usadas do SecLists, licença MIT, filtradas para 10 caracteres ou mais, mais uma lista curta em português), nem ser só uma sequência ou repetição (`1234567890`, `abcdefghij`, `aaaaaaaaaa`, `qwertyuiop`). A lista é um arquivo de texto no projeto, sem dependência, servido também ao cliente web.
- **Senha óbvia para a pessoa é recusada:** a senha não pode conter o e-mail inteiro, a parte antes do `@`, nem uma parte do nome com 4 letras ou mais, sem diferença de maiúsculas e de acentos.
- Cada recusa diz **o motivo** e o que fazer. Quando o motivo é a lista, o texto deixa claro que ela vem de **listas públicas de senhas vazadas na internet**, e não de senhas comuns no app:
  - tamanho: "A senha precisa ter de 10 a 64 caracteres."
  - vazada: "Essa senha aparece em listas públicas de senhas vazadas na internet. Escolha outra, de preferência uma frase."
  - sequência ou repetição: "Sequências e repetições, como 1234567890 ou aaaaaaaaaa, são fáceis de adivinhar. Escolha outra."
  - pessoal: "A senha não pode conter seu e-mail nem seu nome."
- **Quem já tem senha não é afetado:** login continua aceitando a senha atual, mesmo curta ou comum. A regra vale quando a pessoa cria ou troca a senha.
- **Medidor de força:** enquanto a pessoa digita, a tela mostra o nível (fraca, razoável, forte) e uma dica do que melhorar. O medidor só **informa**, nunca impede o envio: quem decide se a senha é aceita são as regras acima, conferidas no servidor. Ele avisa a senha vazada enquanto a pessoa digita, com o mesmo texto da recusa. Se a lista não puder ser baixada, o medidor segue sem essa checagem e o servidor recusa do mesmo jeito.
- **Mostrar senha:** os campos de senha (login, cadastro, reset e perfil) têm um botão de olho, dentro do campo, para mostrar e esconder o que foi digitado.

## Critérios de aceite

- [ ] **CA1** — Dada uma senha nova com menos de 10 caracteres, quando a pessoa tenta cadastrar, trocar ou redefinir, então é recusada com a explicação.
- [ ] **CA2** — Dada uma senha da lista de comuns, ou uma sequência ou repetição, com qualquer combinação de maiúsculas, então é recusada nas três telas com a explicação.
- [ ] **CA3** — Dada uma senha que contém o e-mail, a parte antes do `@` ou parte do nome da pessoa, então é recusada com a explicação.
- [ ] **CA4** — Dada uma frase de 10 caracteres ou mais, com espaços e acentos e sem maiúscula, número ou símbolo, então é aceita.
- [ ] **CA5** — Dado um usuário com senha antiga de 8 caracteres, quando ele faz login, então entra normalmente.
- [ ] **CA6** — Enquanto a pessoa digita uma senha nova, a tela mostra o nível de força e uma dica; o nível muda conforme a senha.
- [ ] **CA7** — Em qualquer campo de senha, o botão de mostrar exibe e esconde o que foi digitado, e é anunciado ao leitor de tela.

## Casos de borda

- Usuários em produção com senha de 8 ou 9 caracteres: continuam entrando (CA5).
- Troca de senha no perfil: a regra "diferente da atual" continua valendo junto com as novas.
- Reset: a senha nova pode ser igual à atual (decisão da 003), mas agora também precisa passar nas regras novas.
- Nome curto (`Ana`, `Léo`): partes com menos de 4 letras não contam, para não recusar senhas que só contêm essas letras por acaso. O mesmo vale para a parte do e-mail antes do `@` (`jo@...`); o e-mail inteiro conta sempre.
- Reset: a tela não conhece o nome nem o e-mail da conta, então o medidor não avisa senha pessoal; quem recusa é o servidor.
- Cadastro: o nome e o e-mail usados na verificação são os do próprio formulário.
- Senha com a palavra comum dentro de uma frase longa (`minha senha123 favorita`): aceita. Só a senha inteira na lista é recusada.

## Fora de escopo

- Obrigar ou sugerir que quem já tem senha fraca troque.
- Consultar serviços externos de senhas vazadas (Have I Been Pwned): exige chamada externa e aprovação à parte.
- Expiração periódica de senha.
- Autenticação em dois fatores (ideia futura, spec própria).
- Regras de composição (maiúscula, número, símbolo).

## Decisões

- Lista: as cerca de 10 mil senhas mais usadas do SecLists (MIT, origem citada no arquivo), filtradas para 10 caracteres ou mais, mais uma lista curta em português.
- O medidor só informa; quem recusa é o servidor.
