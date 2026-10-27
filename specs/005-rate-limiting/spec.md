# 005 — Limite de tentativas

**Status:** aprovada
**Branch:** feature/005-rate-limiting
**Roteiro:** D6

## Problema

Hoje dá para tentar senhas no login sem limite (força bruta). As features 002 e 003 vão abrir endpoints que disparam e-mail; sem limite, alguém pode usar o app para mandar spam e queimar a reputação do domínio.

## Objetivo

Tentativas repetidas são barradas cedo, sem atrapalhar quem usa o app normalmente.

## Regras de negócio

Limites aprovados (27/10/2026):

| Ação | Por IP | Por e-mail |
|---|---|---|
| Login | 10 por 15 min | 3 falhas por 15 min |
| Cadastro | 3 por hora | — |
| Reenvio de verificação (002) | 5 por hora | 3 por hora |
| Esqueci a senha (003) | 5 por hora | 3 por hora |

- Barrado responde "muitas tentativas, tente de novo em X minutos", no formato de erro padrão.
- Login bem-sucedido zera o contador de falhas daquele e-mail.
- A tela mostra a mensagem e quanto tempo falta, em vez de um erro genérico.

## Critérios de aceite

- [ ] **CA1** — Dado um e-mail com 3 senhas erradas em 15 minutos, quando há a 4ª tentativa, então ela é recusada com `429` e o tempo de espera, mesmo que a senha esteja certa.
- [ ] **CA2** — Dado um IP acima do limite de cadastro, quando tenta cadastrar de novo, então é recusado com `429`.
- [ ] **CA3** — Dado o tempo de espera cumprido, quando a pessoa tenta de novo, então a tentativa é processada normalmente.
- [ ] **CA4** — Dado o uso normal (errar a senha uma ou duas vezes e acertar), então nenhuma tentativa é barrada.
- [ ] **CA5** — Dado o app atrás do Caddy, então o limite usa o IP real do visitante, não o do proxy.

## Casos de borda

- Várias pessoas atrás do mesmo IP (rede de empresa ou faculdade)
- O bloqueio por e-mail vale de qualquer IP: alguém pode travar o login de outra pessoa por 15 min (risco aceito)
- Reiniciar o app zera os contadores (aceitável com uma instância só)

## Fora de escopo

- Limite nos endpoints de dados (contas, lançamentos)
- Bloqueio permanente de conta ou de IP
- CAPTCHA

## Perguntas resolvidas

- Os números foram endurecidos (tabela acima).
- Aviso por e-mail ao dono da conta fica fora da 005 (depois da 001).

## Notas para o plano

Decidido no roteiro: contadores em memória (código próprio, sem dependência nova); Redis só com mais de uma instância. Pode ser implementada antes de 001–004 e já proteger o login atual; os limites de 002 e 003 entram junto com elas.
