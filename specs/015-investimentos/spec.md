# 015 — Investimentos: saldo disponível e dinheiro guardado

**Status:** em revisão
**Branch:** feature/015-investimentos
**Depende de:** nenhuma. A fatura do cartão (016) fica para depois.

## Problema

O saldo do dashboard e o ponto de partida da previsão somam todas as contas, sem olhar o tipo. Transferir dinheiro para uma conta de investimento não muda esses números, então o dinheiro guardado continua parecendo disponível.

## Objetivo

A pessoa escolhe quais contas contam no **saldo disponível**. Transferir para uma conta fora dele reduz o disponível e aparece como **Guardado**.

Exemplo: transferir R$ 1.000,00 da conta corrente para o investimento reduz o disponível em R$ 1.000,00 e mostra "Guardado: R$ 1.000,00".

## Regras de negócio

1. Cada conta tem a opção "contar no saldo disponível".
2. Padrão por tipo: `INVESTMENT` desmarcada; `CHECKING`, `SAVING`, `CASH` e `CREDIT_CARD` marcadas.
3. Saldo disponível = soma dos saldos das contas marcadas. Guardado = soma dos saldos das desmarcadas.
4. As duas previsões (fluxo de caixa e mensal) partem do saldo disponível. Todo título em aberto continua projetado como saída ou entrada do disponível.
5. Na API, o campo `countsAsAvailable` é opcional:
   - Na criação, se omitido, usa o padrão do tipo.
   - Na edição, se omitido e o tipo não mudou, mantém o valor atual.
   - Na edição, se omitido e o tipo mudou, usa o padrão do novo tipo.
6. `/accounts/{id}/balance`, extrato, transferências, relatórios e orçamento não mudam.
7. A tabela "Saldo por conta" continua mostrando todas as contas e identifica as que estão fora do disponível.
8. O cartão de crédito continua marcado por padrão até a 016: a compra reduz o disponível e dinheiro comprometido não aparece como livre.
9. O Perfil passa a ter abas (tabs), porque a tela já está cheia: os cartões atuais são redistribuídos e o novo cartão "Saldo disponível" ganha lugar próprio.
10. Um aviso em Novidades conta que o saldo disponível mudou, mostra que investimentos ficam fora dele e explica como escolher as contas no Perfil.

## Critérios de aceite

1. Contas de investimento existentes ficam desmarcadas após a migration; as demais, marcadas.
2. Transferir da corrente para o investimento reduz o `currentBalance` das duas previsões pelo valor transferido.
3. Resultado do mês, orçamento e `/accounts/{id}/balance` não mudam.
4. As três variações da regra 5 funcionam, e conta de outro usuário responde `404`.
5. O dashboard mostra "Saldo disponível" e, havendo conta desmarcada, "Guardado: R$ X".
6. O Perfil permite marcar e desmarcar cada conta; em caso de erro, a caixinha volta ao estado real e o erro fica visível.
7. O contrato OpenAPI documenta o campo novo e a mudança de significado de `currentBalance`.
8. O Perfil tem abas navegáveis por teclado e leitor de tela, e cada cartão atual continua funcionando como antes.
9. Quem já usa o app vê o aviso em Novidades, com o passo a passo da opção no Perfil.

## Casos de borda

- Todas as contas desmarcadas: disponível R$ 0,00.
- Conta mudada de tipo (regra 5).
- Conta arquivada ou excluída: segue a regra atual de saldo.

## Fora de escopo

- Valor atual da carteira, rendimento e dividendos.
- Fatura do cartão: valor, fechamento, vencimento, parcelas e saída futura (spec 016).
- Cotação, importação de corretora e imposto de renda.
- Tela de Configurações (a opção fica em um cartão no Perfil).
- Mudar o conteúdo dos cartões do Perfil além de redistribuí-los em abas.
- Alterar a edição de conta em `views/accounts.js`.

## Migration que altera dados existentes

`V19` adiciona `counts_as_available` e desmarca as contas `INVESTMENT`. Nenhum lançamento muda, mas o saldo em destaque e a previsão de quem tem conta de investimento **vão cair** no deploy.

## Decisões tomadas (2026-11-06)

- A opção fica em um cartão no Perfil, que passa a ter abas.
- O cartão de crédito continua marcado por padrão; a 016 será feita logo em seguida, antes de ir para a `main`.
- Entra um aviso em Novidades com o passo a passo.
- Migration V19 aprovada.
