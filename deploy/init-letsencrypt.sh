#!/usr/bin/env bash
# First certificate. Nginx will not start without one and certbot needs nginx to answer the
# challenge, so: a throwaway certificate, nginx up, the real certificate, nginx reloaded.
# Run once on the VPS, from the repository root, after filling .env.prod.
set -euo pipefail

cd "$(dirname "$0")/.."
ENV_FILE="${ENV_FILE:-.env.prod}"
set -a; source "$ENV_FILE"; set +a
read -ra COMPOSE_BASE <<< "${COMPOSE_BIN:-docker compose}"

COMPOSE=("${COMPOSE_BASE[@]}" --env-file "$ENV_FILE" -f docker-compose.prod.yml)
CERT_DIR="/etc/letsencrypt/live/${DOMAIN}"

echo ">> Throwaway certificate for ${DOMAIN}"
"${COMPOSE[@]}" run --rm --no-deps --entrypoint sh certbot -c "
  mkdir -p ${CERT_DIR} &&
  apk add --no-cache openssl >/dev/null 2>&1 || true
  openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
    -keyout ${CERT_DIR}/privkey.pem -out ${CERT_DIR}/fullchain.pem -subj '/CN=localhost'"

echo ">> Starting the stack"
"${COMPOSE[@]}" up -d --build postgres backend nginx

echo ">> Asking Let's Encrypt for the real certificate"
"${COMPOSE[@]}" run --rm --entrypoint sh certbot -c "
  rm -rf /etc/letsencrypt/live/${DOMAIN} /etc/letsencrypt/archive/${DOMAIN} /etc/letsencrypt/renewal/${DOMAIN}.conf &&
  certbot certonly --webroot -w /var/www/certbot \
    --email ${LETSENCRYPT_EMAIL} --agree-tos --no-eff-email -d ${DOMAIN}"

echo ">> Reloading nginx and starting the renewal service"
"${COMPOSE[@]}" exec nginx nginx -s reload
"${COMPOSE[@]}" up -d certbot

echo "Done. Next: deploy/bootstrap.sh creates the first administrator."
