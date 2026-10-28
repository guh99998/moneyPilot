# 009 — Plano técnico

**Spec:** [spec.md](spec.md)
**Status:** aprovado

## Abordagem

Tudo no pacote `auth` e num módulo novo do cliente, em duas partes: o que não depende de quem escolhe a senha (tamanho, lista, sequência) fica no validador `@NewPassword` que as três rotas já usam; o que depende da pessoa (e-mail e nome) fica num componente chamado pelos três services.

**Lista única** — `src/main/resources/static/data/common-passwords.txt`: uma senha por linha, em minúsculas, sem repetidas, só com 10+ caracteres; cabeçalho com `#` citando a fonte (SecLists, MIT) e a lista em português. É lida pelo Java do classpath e servida ao cliente como arquivo estático (`/data/**` com `permitAll` em `SecurityConfig`): uma fonte só.

**`CommonPasswords`** (`@Component`): carrega o arquivo uma vez, na subida (se não ler, a aplicação não sobe: sem a lista a regra ficaria desligada em silêncio).
- `isCommon(password)`: a senha inteira, em minúsculas, está na lista.
- `isSequenceOrRepetition(password)`: um caractere repetido; dígitos ou letras ASCII em sequência crescente ou decrescente (os dígitos dão a volta: `1234567890`, `7890123456`); trecho de linha de teclado (`qwertyuiop`, `asdfghjkl`, `zxcvbnm`, também ao contrário).

**`NewPasswordValidator`**: `MIN_LENGTH` 8 → 10; depois do tamanho e dos 72 bytes, recusa lista e sequência, cada um com a sua mensagem. Recebe `CommonPasswords` por injeção (o Spring autowira `ConstraintValidator`). Quem está na lista e também é sequência (`1234567890`) é recusado como vazado.

**`PasswordPolicy`** (`@Component`): `checkPersonal(field, password, email, name)` recusa se a senha contém o e-mail inteiro, a parte antes do `@` ou uma parte do nome, sem diferença de maiúsculas e acentos (`java.text.Normalizer`). Partes com menos de 4 letras não contam, no nome e na parte local do e-mail. Lança `WeakPasswordException(field, message)`, tratada no `GlobalExceptionHandler` por `fieldError(...)`: `400` com erro no campo.
- `UserService.createUser`: checa **antes** de procurar a conta, com o nome e o e-mail do request, para a resposta continuar igual exista ou não a conta (regra da 002).
- `ProfileService.changePassword` (campo `newPassword`): depois de conferir a senha atual, com nome e e-mail do usuário.
- `PasswordResetService.reset` (campo `newPassword`): depois de validar o link e antes de gravar. A exceção desfaz a transação, então o link continua valendo.

Login, senha atual e "diferente da atual" não mudam.

## Banco de dados

Nenhuma migration.

**Impacto em dados existentes:** nenhum. Os hashes atuais não são tocados e quem tem senha curta ou comum continua entrando (CA5).

## API

Nenhuma rota nova nem campo novo; só novos motivos de `400` com erro no campo (`password` no cadastro, `newPassword` na troca e no reset):

| Rota | Mudança |
|---|---|
| `POST /auth/register` | `400` também para senha curta (10), vazada, sequência ou com e-mail/nome |
| `POST /auth/password` | idem, no campo `newPassword` |
| `POST /auth/password/reset` | idem, no campo `newPassword`; o link continua valendo |
| `GET /data/common-passwords.txt` | arquivo estático público, lido pelo medidor |

Mensagens:
- tamanho: "A senha precisa ter de 10 a 64 caracteres."
- vazada: "Essa senha aparece em listas públicas de senhas vazadas na internet. Escolha outra, de preferência uma frase."
- sequência: "Sequências e repetições, como 1234567890 ou aaaaaaaaaa, são fáceis de adivinhar. Escolha outra."
- pessoal: "A senha não pode conter seu e-mail nem seu nome."

Mudança incompatível com o cliente atual: o cliente antigo (cache do navegador) ainda mostra "8 a 64", mas o servidor responde com a mensagem certa no campo.

## Cliente web

- `js/password.js` (novo):
  - `loadCommonPasswords()`: baixa `/data/common-passwords.txt` uma vez e guarda em memória; falha de rede ou resposta que não é 200 devolve lista vazia sem guardar, então o medidor segue sem essa checagem e a próxima chamada tenta de novo.
  - `assess(password, { email, name, common })`: as mesmas regras do servidor, mais o nível: `fraca` (falha alguma regra), `razoável` (passa, com menos de 16 caracteres) ou `forte` (16+, ou 14+ com espaço ou 3 tipos de caractere) e uma dica.
  - `passwordInput({ id, autocomplete, describedBy, meter, getContext, ... })`: campo + botão de olho dentro (`svgIcon`, `aria-label` "Mostrar senha"/"Esconder senha", `aria-pressed`, alterna o `type`) + medidor opcional ligado por `aria-describedby`, sem live region para o leitor de tela não anunciar a cada tecla. A lista só é baixada na primeira tecla.
- `views/auth.js` (cadastro: olho + medidor com nome e e-mail do formulário; login: só o olho), `views/reset-password.js` (olho + medidor; o nome e o e-mail não aparecem na tela, o servidor checa), `views/profile.js` (nova senha: olho + medidor com o nome e o e-mail da conta; senha atual e senha da troca de e-mail: só o olho). Dica "De 10 a 64 caracteres", `minlength` 10. No perfil os rótulos passam a apontar para os campos (`for`) e a mensagem do servidor para `newPassword` é mostrada como veio, em vez do texto fixo "mínimo 8 caracteres".
- `css/app.css`: `.password-field` (posição relativa, campo com 44px e `padding-right` para o botão), `.password-toggle` de 44×44 com o foco visível global, `.strength` com os tokens `--critical`, `--warning` e `--good`; o nível aparece também em texto, não só em cor.

## Testes

- Antes de tudo, a senha dos testes de integração (`senha12345`, que agora está na lista) virou `minha frase secreta`, e os testes de tamanho passaram a 10. `LongPasswordIntegrationTest` perdeu a repetição de `a` no limite de 64 caracteres.
- `PasswordPolicyIntegrationTest`: CA1–CA4 nas três rotas (cadastro, troca e reset), cadastro com o e-mail de conta verificada respondendo igual ao de e-mail livre, e CA5 com usuário inserido por JDBC com senha de 8 caracteres.
- `CommonPasswordsTest` (sequências, repetições, teclado, maiúsculas, lista) e `PasswordPolicyTest` (acentos, partes curtas, nulos).
- JS: `password.test.mjs` (níveis, dicas, o texto "listas públicas de senhas vazadas", falha do download, a lista real), `password-fields.test.mjs` e `profile-view.test.mjs` com o DOM falso (olho, medidor ao digitar, `aria-describedby`, rótulos).
- Revisão com as skills de acessibilidade, responsividade e Web Interface Guidelines nas telas alteradas.
- Manual (1280px e 390px): olho, medidor e mensagens no cadastro, reset e perfil.

## Riscos e alternativas descartadas

- **A lista filtrada é curta.** Das 10 mil senhas mais usadas, só umas 50 têm 10+ caracteres; o resto das senhas fracas curtas já cai no tamanho. Com o mínimo de 10, a proteção contra vazadas vem sobretudo das regras de sequência e pessoal, e de uma lista em português mantida à mão. Uma lista maior (por exemplo as 100 mil mais usadas) é uma troca de arquivo, sem mudar código.
- **Parte do e-mail antes do `@` curta**: sem mínimo, `a@x.com` recusaria toda senha com "a". Por isso o mínimo de 4 letras vale também para a parte local; o e-mail inteiro conta sempre.
- Consultar Have I Been Pwned: descartado (fora de escopo na spec).
- Medidor que bloqueia o envio: descartado (decisão da spec).

## Deploy

1. Sem migration: backup não é obrigatório, mas o padrão de backup antes de subir continua valendo.
2. Merge em `main` com ok explícito do Gustavo; `pull` + `up -d app`.
3. Teste em produção: cadastro de uma conta de teste com `1234567890` (recusada) e com uma frase (aceita); login de uma conta antiga.

Rollback: voltar a imagem anterior. Nada no banco muda.
