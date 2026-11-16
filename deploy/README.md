# Colocando o Castel em produção (VPS única)

Pilha: **Postgres** (sem porta aberta), **backend** Spring Boot, **Nginx** (serve o front, faz proxy da
API e termina TLS) e **certbot** (renova o certificado). Tudo em `docker-compose.prod.yml`.

## Antes de começar

1. **Um domínio** com um registro `A` apontando para o IP da VPS. Sem nome não há HTTPS, e o celular do
   cliente precisa dele. Sem domínio ainda, um nome temporário serve: `203-0-113-10.nip.io` (troque pelo
   IP da VPS, com hífens).
2. Na VPS: Docker e o plugin Compose (`curl -fsSL https://get.docker.com | sh`).
3. Firewall liberando só `22`, `80` e `443` (`ufw allow 22,80,443/tcp && ufw enable`). **A 5432 não abre.**
4. Se a VPS tiver só 1 vCPU, crie 2 GB de swap para o build não derrubar a máquina:
   `fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile`

## Primeira instalação

```bash
git clone <repositório> castel && cd castel
cp .env.prod.example .env.prod
nano .env.prod                 # DOMAIN, e-mail, senha do banco, JWT_SECRET
./deploy/init-letsencrypt.sh   # certificado + sobe a pilha (o primeiro build leva alguns minutos)
./deploy/bootstrap.sh          # propriedade, configurações e o primeiro ADMIN
```

Depois, abra `https://SEU-DOMINIO`, entre com o administrador e crie os usuários (garçom, caixa, cozinha)
pela tela de administração. As mesas e o cardápio também são cadastrados lá.

Gerar os valores secretos:

```bash
openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 24   # POSTGRES_PASSWORD
openssl rand -hex 32                                        # JWT_SECRET
```

## Atualizar

```bash
git pull
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

As migrations do Flyway rodam sozinhas na subida do backend. Elas só andam para frente: nunca edite uma
migration já aplicada.

## Backup

```bash
crontab -e
30 3 * * * /home/USUARIO/castel/deploy/backup.sh >> /home/USUARIO/backup.log 2>&1
```

Guarda 14 dias em `~/castel-backups`. **Copie essa pasta para fora da VPS** (rsync, rclone ou download),
senão perder a máquina perde os backups junto. O backup diário da hospedagem é a segunda camada.

Restaurar:

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml exec -T postgres \
  pg_restore -U castel -d castel --clean --if-exists < ~/castel-backups/castel_AAAAMMDD_HHMMSS.dump
```

## Conferir se está de pé

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml ps          # todos "healthy" ou "running"
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f backend
curl -I https://SEU-DOMINIO                                                # 200
```

## O que esta instalação ainda não faz

- **Nota fiscal e cobrança no cartão são simuladas** (`fake`). O Pix funciona como no teste: o garçom confere
  o comprovante no celular do cliente.
- Uma VPS só: se ela cair, o sistema cai até voltar.
