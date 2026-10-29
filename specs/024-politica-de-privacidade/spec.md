# 024 — Política de privacidade e termos de uso

**Status:** rascunho (criada a partir da 020; aguardando a retenção dos backups)
**Branch:** feature/024-politica-de-privacidade
**Depende de:** 020 (site do produto, onde as páginas vivem)

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

## Casos de borda

- Retenção dos backups desconhecida: a política não pode ser aprovada sem ela (ver perguntas em aberto)
- Usuários que já têm conta: não há reaceite nesta versão; o aviso vale para novos cadastros
- Mudança futura de provedor ou de dado coletado: exige atualizar a política e a data

## Fora de escopo

- Checkbox de aceite, registro de aceite no banco e reaceite de quem já tem conta
- Banner de cookies (o app não usa cookies de rastreamento nem analytics)
- Versão em inglês
- Parecer jurídico

## Perguntas em aberto

- **Retenção dos backups:** o Gustavo vai conferir na VPS (`crontab -l` e `sudo crontab -l`, o script do backup e a regra de ciclo de vida do bucket) por quanto tempo os backups ficam guardados e onde. Sem isso, a política não é aprovada.
- Qual e-mail de contato do responsável aparece na política (fora do repositório se for pessoal)?
