# 010 — Falar com o suporte

**Status:** aprovada
**Branch:** feature/010-falar-com-o-suporte
**Depende de:** 001 (e-mail), 005 (limite de tentativas)

## Problema

Quem usa o app e encontra um erro, tem uma dúvida ou uma sugestão não tem por onde avisar. Quem não consegue entrar na conta está ainda mais sem saída.

## Objetivo

A pessoa abre um chamado pelo próprio app, em poucos segundos, e a mensagem chega ao e-mail do suporte, pronta para ser respondida.

## Regras de negócio

- **Onde fica:** uma tela própria (`#/suporte`), aberta por um item "Falar com o suporte" no rodapé do menu, para quem está logado, e por um link na tela de login, para quem não consegue entrar.
- **Campos:**
  - categoria: Dúvida, Problema, Sugestão ou Outro;
  - mensagem: obrigatória, de 10 a 2.000 caracteres;
  - e-mail: só para quem não está logado (obrigatório). Logado, vai o e-mail da conta, sem a pessoa digitar.
- **Entrega:** o chamado vira um e-mail para o endereço do suporte, com o e-mail da pessoa no `Reply-To`. Responder é só clicar em "Responder".
- **Conteúdo do e-mail ao suporte:** categoria, mensagem, e-mail da pessoa, se estava logada, nome da conta (quando logada), data e hora (`America/Sao_Paulo`) e um número curto do chamado para referência.
- **Destino:** o endereço do suporte vem de variável de ambiente (`SUPPORT_EMAIL`), nunca do código, porque o repositório é público. Sem a variável em `prod`, o app não sobe, como no e-mail transacional.
- **Confirmação:** mensagem na tela "Recebemos sua mensagem (chamado K7Q2M9). Respondemos no e-mail …", com o número curto do chamado. Sem e-mail de confirmação nem cópia para a pessoa.
- **Limites:** 3 chamados por hora por conta (ou por e-mail, sem login) e 10 por hora por IP. Acima disso, `429` com o tempo de espera. Só conta o chamado que chegou a ser enviado: uma falha de envio não gasta a cota.
- Falha no envio do e-mail responde erro (`502`), e a mensagem continua no formulário para a pessoa tentar de novo. Nada fica perdido em silêncio.

## Critérios de aceite

- [ ] **CA1** — Dada uma pessoa logada, quando envia um chamado válido, então o suporte recebe um e-mail com categoria, mensagem e o e-mail da conta no `Reply-To`, e a tela confirma o envio.
- [ ] **CA2** — Dada uma pessoa sem login, quando envia um chamado pela tela de login com um e-mail informado, então o suporte recebe o e-mail com esse endereço no `Reply-To`.
- [ ] **CA3** — Dada uma mensagem vazia, curta ou longa demais, quando envia, então é recusada com erro no campo e nada é enviado.
- [ ] **CA4** — Dadas mais tentativas que o limite, quando envia de novo, então recebe `429` com o tempo de espera.
- [ ] **CA5** — Dada uma falha no envio do e-mail, quando envia, então vê um erro e a mensagem continua no formulário.

## Casos de borda

- Mensagem com HTML ou script: vai como texto puro no e-mail
- Mensagem com quebras de linha e acentos: chegam como foram escritas
- Pessoa sem login informando um e-mail de outra pessoa: aceito (o suporte responde para esse e-mail; não há como provar a posse sem verificação)
- Duplo clique no "Enviar": um só chamado
- Conta só com Google: usa o e-mail da conta como as outras

## Fora de escopo

- Guardar os chamados no banco, com status (aberto, resolvido) e histórico para a pessoa
- Painel de atendimento dentro do app
- Anexos e capturas de tela
- Chat em tempo real

## Decisões

- O link na tela de login entra agora.
- As categorias são Dúvida, Problema, Sugestão e Outro.
- Os chamados vão só por e-mail, sem banco e sem migration. **Atualizado pela 017:** o chamado passa a ser gravado no banco, e a falha no envio do e-mail deixa de dar erro (o chamado fica gravado e a pessoa vê "recebido"), o que muda o CA5.
- A pessoa não recebe cópia por e-mail.
- Os limites são 3 por hora por conta (ou por e-mail, sem login) e 10 por hora por IP.
