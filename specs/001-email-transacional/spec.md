# 001 — E-mail transacional

**Status:** rascunho

**Branch:** feature/001-email-transacional

**Roteiro:** D2

## Problema

O app não envia e-mail nenhum. Verificação de conta (002) e reset de senha (003) dependem disso, e sem e-mail um usuário que esquece a senha perde o acesso.

## Objetivo

O sistema consegue enviar e-mails transacionais a partir do domínio do app, de forma confiável, e as features seguintes só precisam pedir "envie este e-mail para esta pessoa".

## Regras de negócio

- Remetente fixo do domínio do app (ex.: `nao-responda@<dominio>`), com nome "Money Pilot".
- E-mails em português, com versão em texto simples legível mesmo sem HTML.
- Falha no envio não passa em silêncio: quem pediu o envio fica sabendo e a tela oferece tentar de novo.
- Em produção, sem configuração de envio o app não sobe (mesma regra do `JWT_SECRET`).
- Em desenvolvimento local, o e-mail não é enviado de verdade: o conteúdo aparece no log.

## Critérios de aceite

- [ ] **CA1** — Dado o app em produção configurado, quando um e-mail é solicitado, então ele chega na caixa de entrada (não no spam) com o remetente do domínio.
- [ ] **CA2** — Dado o profile `prod` sem a configuração de envio, quando o app sobe, então o boot falha com mensagem clara.
- [ ] **CA3** — Dado o profile `local`, quando um e-mail é solicitado, então nada é enviado e o conteúdo aparece no log.
- [ ] **CA4** — Dado que o provedor está fora do ar, quando um envio falha, então o erro vai para o log sem expor a chave do provedor, e quem pediu o envio recebe a falha.

## Casos de borda

- Endereço inválido ou que rejeita a mensagem
- Provedor lento: o envio não pode travar a requisição por muito tempo

## Fora de escopo

- Newsletter, e-mail de marketing ou resumo mensal
- Fila de reenvio automático
- Templates editáveis pelo usuário

## Perguntas em aberto

- Qual domínio e endereço remetente? (precisa estar verificado no provedor — tarefa sua)
- O envio deve ser síncrono (a tela mostra o erro na hora) ou assíncrono (o usuário segue e o e-mail é tentado depois)? A proposta acima é síncrona, por simplicidade.

## Notas para o plano

Decidido no roteiro: Resend via API HTTP (`RestClient`), chave em variável de ambiente.
