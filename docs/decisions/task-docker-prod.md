# Decisões — Task docker-prod · Configuração de produção em Docker

> Registro acumulado das decisões desta task. Atualizado a cada rodada.

---

## Estado

| | |
|---|---|
| Branch | `task/docker-prod` |
| Rodada atual | 1 |
| Build | implementado |
| Testes | Sem teste automatizado (é infraestrutura). Validado subindo a pilha inteira na máquina do Ruan com um certificado de teste: backend `healthy` no perfil `prod` (Flyway aplicou todas as migrations em banco vazio), `bootstrap.sh` cria o admin e roda de novo sem duplicar, login por HTTPS, SPA e link direto, redirecionamento 80→443, WebSocket pelo Nginx (101), backup gerando `.dump`. **Não validado**: a emissão real do certificado Let's Encrypt (precisa de domínio e da VPS) |

---

## Origem

Ruan vai contratar uma VPS (1 vCPU, 4 GB, 40 GB NVMe, Brasil) e pediu "o que falta de configuração do Docker"
(2026-11-16). Hoje só existe `docker-compose.dev.yml` e o `Dockerfile` do backend.

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 1 | Segue o `docs/plano-tecnico.md` §10: **Nginx serve o front, faz proxy reverso da API e termina TLS**. Isso fecha o ponto "como o front é servido em produção" que o `frontend/CLAUDE.md` deixava aberto (Ruan, 2026-11-16, "faça o que falta") | implementado |
| 2 | 1 | TLS por Let's Encrypt (certbot em contêiner, renovação automática). Exige um domínio apontando para a VPS | implementado |
| 3 | 1 | O Postgres **não publica porta** no host; só o Nginx expõe 80 e 443 | implementado |
| 4 | 1 | `TRUSTED_PROXIES` é a sub-rede fixa da rede do compose, para o limite de tentativa de login enxergar o IP real do cliente (o guard de `prod` exige o valor) | implementado |
| 5 | 1 | Em produção não há seed. Os dados mínimos (propriedade, configurações e o primeiro `ADMIN`) entram por `deploy/bootstrap.sh`, rodado uma vez, com SQL puro. Nenhum código Java muda | implementado |
| 6 | 1 | Os valores iniciais das configurações são os mesmos do seed de dev (taxa de serviço 10%, caixa obrigatório = falso, limites da cozinha). **Premissa minha, a confirmar**: ver Pontos em aberto | implementado |
| 7 | 1 | Backup: `deploy/backup.sh` (`pg_dump` diário, retenção de 14 dias) agendado no cron da VPS | implementado |
| 8 | 1 | Memória da JVM limitada por `JAVA_TOOL_OPTIONS` e rotação de log por contêiner, para a VPS de 4 GB e 40 GB não encher | implementado |
| 9 | 1 | DEV: o `Dockerfile` do backend **nunca copiou o módulo `finance`**, então a imagem não construía (`Child module /workspace/finance does not exist`). Acrescentado o `COPY` do `pom.xml` e do módulo | implementado |
| 10 | 1 | DEV: o jar do `app` não era executável (`no main manifest attribute`): o `spring-boot-maven-plugin` estava declarado sem a execução `repackage`, porque o pai é um BOM e não o `spring-boot-starter-parent`. Acrescentada a execução em `app/pom.xml`. Única mudança fora de `deploy/` e do compose | implementado |
| 11 | 1 | DEV: com `command` próprio, o Nginx da imagem não renderiza o template do `DOMAIN` (só renderiza se o comando começa por `nginx`). O compose chama `20-envsubst-on-templates.sh` e filtra só `DOMAIN` | implementado |
| 12 | 1 | DEV: no `.env.prod`, valor com espaço ou barra invertida vai entre aspas simples, para o `source` dos scripts e o compose lerem a mesma coisa | implementado |
| 13 | 1 | Os scripts de `deploy/` aceitam `ENV_FILE` e `COMPOSE_BIN`, só para testar a pilha na máquina de desenvolvimento (que tem `docker-compose` e não `docker compose`) | implementado |

---

## Escopo desta task

- [x] `docker-compose.prod.yml` (postgres, backend, nginx, certbot)
- [x] Imagem do Nginx com o build do front (`deploy/nginx/Dockerfile`)
- [x] `deploy/nginx/default.conf.template` (SPA, `/api`, `/public`, `/ws`, ACME, HTTPS)
- [x] `.env.prod.example` e `.dockerignore`
- [x] `deploy/init-letsencrypt.sh`, `deploy/bootstrap.sh`, `deploy/backup.sh`
- [x] `deploy/README.md` com o passo a passo na VPS
- [x] Validar subindo a pilha localmente

### Movido para outra task

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| CI de build e push da imagem | a definir | Não foi pedido; a imagem pode ser gerada na máquina do Ruan | — |

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| Emissor fiscal e processador de pagamento seguem **fake** em produção | É a v1 do plano (`castel.payment.processor: fake`) | Adaptadores reais nas v1.1 e v1.2 |
| Uma VPS só, sem alta disponibilidade | O plano diz que basta para o porte | — |

## Pontos em aberto

| # | Pergunta | Desde a rodada |
|---|---|---|
| 1 | Os valores iniciais das configurações (decisão #6) são os do restaurante? Em especial: taxa de serviço 10% e se o caixa aberto é obrigatório para receber dinheiro | 1 |
| 2 | Qual é o domínio que vai apontar para a VPS? | 1 |
