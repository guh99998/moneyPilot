# 005 — Limite de tentativas

**Status:** rascunho
**Branch:** feature/005-rate-limiting
**Roteiro:** D6

## Problema

Hoje dá para tentar senhas no login sem limite (força bruta). As features 002 e 003 vão abrir endpoints que disparam e-mail; sem limite, alguém pode usar o app para mandar spam e queimar a reputação do domínio.

## Objetivo

Tentativas repetidas são barradas cedo, sem atrapalhar quem usa o app normalmente.

## Regras de negócio

Limites propostos (ajustar antes de aprovar):

| Ação | Por IP | Por e-mail |
|---|---|---|
| Login | 20 por 15 min | 5 falhas por 15 min |
| Cadastro | 5 por hora | — |
| Reenvio de verificação (002) | 5 por hora | 3 por hora |
| Esqueci a senha (003) | 5 por hora | 3 por hora |

- Barrado responde "muitas tentativas, tente de novo em X minutos", no formato de erro padrão.
- Login bem-sucedido zera o contador de falhas daquele e-mail.
- A tela mostra a mensagem e quanto tempo falta, em vez de um erro genérico.

## Critérios de aceite

- [ ] **CA1** — Dado um e-mail com 5 senhas erradas em 15 minutos, quando há a 6ª tentativa, então ela é recusada com `429` e o tempo de espera, mesmo que a senha esteja certa.
- [ ] **CA2** — Dado um IP acima do limite de cadastro, quando tenta cadastrar de novo, então é recusado com `429`.
- [ ] **CA3** — Dado o tempo de espera cumprido, quando a pessoa tenta de novo, então a tentativa é processada normalmente.
- [ ] **CA4** — Dado o uso normal (errar a senha uma ou duas vezes e acertar), então nenhuma tentativa é barrada.
- [ ] **CA5** — Dado o app atrás do Caddy, então o limite usa o IP real do visitante, não o do proxy.

## Casos de borda

- Várias pessoas atrás do mesmo IP (rede de empresa ou faculdade)
- Reiniciar o app zera os contadores (aceitável com uma instância só)

## Fora de escopo

- Limite nos endpoints de dados (contas, lançamentos)
- Bloqueio permanente de conta ou de IP
- CAPTCHA

## Perguntas em aberto

- Os números da tabela fazem sentido para o seu público?
- Avisar o dono da conta por e-mail quando houver muitas tentativas erradas?

## Notas para o plano

Decidido no roteiro: contadores em memória (Bucket4j); Redis só com mais de uma instância. Pode ser implementada antes de 001–004 e já proteger o login atual; os limites de 002 e 003 entram junto com elas.
