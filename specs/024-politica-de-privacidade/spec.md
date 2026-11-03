# 024 — Política de privacidade e termos de uso

**Status:** entregue
**Branch:** feature/024-politica-de-privacidade
**Depende de:** 020 (site do produto, onde as páginas vivem) e 012 (exclusão de conta; a política só promete a exclusão depois que a 012 estiver em produção)

## Problema

O moneyPilot guarda dados financeiros de pessoas reais e não diz, em lugar nenhum, que dados coleta, para quê, por quanto tempo e como a pessoa os apaga. Com a LGPD, isso deve estar escrito e acessível antes do cadastro.

## Objetivo

O site tem uma página de **Política de privacidade** e uma de **Termos de uso**, em português simples, linkadas no rodapé do site e na tela de criar conta.

## Regras de negócio

- **Páginas estáticas** do site (020), públicas, em `/privacidade/` e `/termos/`, sem backend.
- **Política de privacidade** descreve, de forma fiel ao que o app faz hoje: quais dados são coletados (e-mail, nome, dados financeiros lançados pela pessoa, logs de sessão), para que servem, com quem são compartilhados (provedor de e-mail transacional, login com Google quando usado, hospedagem), por quanto tempo são guardados (incluindo **backups**), os direitos da pessoa (acesso, correção, exclusão) e como exercê-los (exclusão de conta da 012 e o suporte da 010), e o contato do responsável.
- **Termos de uso** descrevem o serviço gratuito, sem garantia de disponibilidade, a responsabilidade da pessoa sobre o que lança, o apoio voluntário (019) e que o projeto pode mudar ou acabar, com aviso.
- **Sem promessa que o app não cumpre:** cada afirmação precisa ser verdadeira no código e na operação. Onde o app ainda não faz algo (ex.: exclusão de conta, até a 012), o texto não promete.
- **Data de vigência e histórico:** cada página mostra a data da última atualização.
- O texto é proposto pelo Claude no plano e revisado pelo Gustavo; não substitui orientação jurídica.
- **Aceite no cadastro:** a tela de criar conta mostra o aviso "Ao criar a conta, você concorda com os Termos e a Política de privacidade", com links. Não há checkbox nem nova coluna no banco nesta versão.

## Critérios de aceite

- [ ] **CA1** — Dado um visitante sem sessão, quando abre `/privacidade/` e `/termos/`, então lê as duas páginas sem login.
- [ ] **CA2** — Dado o rodapé do site, então tem links para as duas páginas.
- [ ] **CA3** — Dada a tela de criar conta, então mostra o aviso de concordância com links que abrem as páginas em nova aba.
- [ ] **CA4** — Dada a política, então cita os dados coletados, a finalidade, os provedores, a retenção (incluindo backups), os direitos e o contato.
- [ ] **CA5** — Dadas as duas páginas, então mostram a data da última atualização.
- [ ] **CA6** — Dadas as páginas em 390px e 1280px, então não há rolagem horizontal.

## Decisões

- **Fontes no próprio site:** Manrope e Bricolage Grotesque (licença OFL, que acompanha os arquivos) passam a ser servidas de `static/fonts/`, e os `<link>` do Google Fonts saem das 15 páginas. Assim o app e o site não enviam o IP de ninguém ao Google só para carregar fonte, e a política pode dizer isso com verdade. São arquivos estáticos, não dependência de build.
- **Provedores citados pelo nome:** e-mail transacional (Resend), login com Google (quando a pessoa usa), a hospedagem (VPS) e o armazenamento dos backups.
- **Contato:** só pelo formulário de suporte (010). Nenhum e-mail aparece nas páginas.
- **Endereço IP:** é usado só nos limites de tentativas, em memória, e não é gravado no banco. Pode aparecer nos logs de acesso da hospedagem; a retenção deles entra na política depois do levantamento.
- **Cookie:** o app usa um único cookie, o de sessão (`refresh`), necessário para manter a pessoa logada. Não há cookies de rastreamento nem analytics, então não há banner.
- **Exclusão:** a política descreve a exclusão de conta da 012 (imediata, com os chamados de suporte apagados) e a retenção residual em backups.

## Casos de borda

- Retenção de backups ou de logs desconhecida: a política não é aprovada sem ela (ver perguntas em aberto)
- Retenção sem limite (backups ou logs guardados para sempre): corrigir a operação (rotação, ciclo de vida do bucket) antes de publicar, para o texto prometer um prazo verdadeiro
- Usuários que já têm conta: não há reaceite nesta versão; o aviso vale para novos cadastros
- Mudança futura de provedor ou de dado coletado: exige atualizar a política e a data
- Fonte que falha ao carregar: o texto continua legível com a fonte de sistema

## Fora de escopo

- Checkbox de aceite, registro de aceite no banco e reaceite de quem já tem conta
- Banner de cookies (o app não usa cookies de rastreamento nem analytics)
- Versão em inglês
- Parecer jurídico

## Perguntas em aberto

- **Retenção de backups e logs (Gustavo, na VPS):** agendamento e script do backup, para onde vai e por quanto tempo fica (regra de ciclo de vida do bucket), cópias locais, e rotação dos logs dos containers e do proxy. O produto final é uma frase: "backups por N dias em <provedor/região>; logs por M dias". Sem isso, a política não é aprovada. O `docker-compose.prod.yml` não limita o tamanho dos logs; se a resposta for "sem limite", preparo um ajuste antes.
