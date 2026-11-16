#!/usr/bin/env bash
# The first install: the property, the settings and the first ADMIN. Production has no seed
# (the seeder is dev-only), so without this nobody can sign in. Safe to run again: it creates
# only what is missing and never touches an existing user or setting.
# Run once, from the repository root, after the backend has started (Flyway has made the tables).
set -euo pipefail

cd "$(dirname "$0")/.."
ENV_FILE="${ENV_FILE:-.env.prod}"
set -a; source "$ENV_FILE"; set +a
read -ra COMPOSE_BASE <<< "${COMPOSE_BIN:-docker compose}"

COMPOSE=("${COMPOSE_BASE[@]}" --env-file "$ENV_FILE" -f docker-compose.prod.yml)

if [ -z "${ADMIN_PASSWORD:-}" ]; then
  read -rsp "Senha do administrador (mínimo 12 caracteres): " ADMIN_PASSWORD; echo
fi
if [ "${#ADMIN_PASSWORD}" -lt 12 ]; then
  echo "A senha precisa de pelo menos 12 caracteres." >&2
  exit 1
fi

# BCrypt cost 12, the same the application uses; $2y$ and $2a$ are the same algorithm.
HASH=$(docker run --rm httpd:2.4-alpine htpasswd -nbBC 12 "" "$ADMIN_PASSWORD" | tr -d ':\n' | sed 's/^\$2y/$2a/')

"${COMPOSE[@]}" exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -v ON_ERROR_STOP=1 \
  -v legal_name="$LEGAL_NAME" -v time_zone="$TIME_ZONE" \
  -v admin_username="$ADMIN_USERNAME" -v admin_full_name="$ADMIN_FULL_NAME" -v admin_hash="$HASH" <<'SQL'
BEGIN;

INSERT INTO property (id, legal_name, time_zone)
SELECT gen_random_uuid(), :'legal_name', :'time_zone'
WHERE NOT EXISTS (SELECT 1 FROM property);

-- The defaults of the dev seed (task docker-prod, decision #6): confirm them with the owner.
INSERT INTO setting (id, property_id, setting_key, setting_value, value_type, description)
SELECT gen_random_uuid(), (SELECT id FROM property LIMIT 1), v.k, v.v, v.t, v.d
FROM (VALUES
  ('billing.cash-drawer.required', 'false', 'BOOLEAN', 'CASH payments require an open cash drawer session'),
  ('restaurant.service-charge-percent', '10.00', 'DECIMAL', 'Service charge on table-service tabs, in percent points'),
  ('restaurant.kitchen-display.kitchen.warning-minutes', '15', 'INTEGER', 'Minutes after the order when a KITCHEN ticket calls for attention'),
  ('restaurant.kitchen-display.kitchen.late-minutes', '25', 'INTEGER', 'Minutes after the order when a KITCHEN ticket is late'),
  ('restaurant.kitchen-display.pizza.warning-minutes', '20', 'INTEGER', 'Minutes after the order when a PIZZA ticket calls for attention'),
  ('restaurant.kitchen-display.pizza.late-minutes', '30', 'INTEGER', 'Minutes after the order when a PIZZA ticket is late'),
  ('restaurant.kitchen-display.bar.warning-minutes', '5', 'INTEGER', 'Minutes after the order when a BAR ticket calls for attention'),
  ('restaurant.kitchen-display.bar.late-minutes', '10', 'INTEGER', 'Minutes after the order when a BAR ticket is late')
) AS v(k, v, t, d)
ON CONFLICT (property_id, setting_key) DO NOTHING;

WITH admin AS (
  INSERT INTO app_user (id, property_id, full_name, password_hash, is_active, username)
  SELECT gen_random_uuid(), (SELECT id FROM property LIMIT 1), :'admin_full_name', :'admin_hash', true, :'admin_username'
  WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE lower(username) = lower(:'admin_username'))
  RETURNING id
)
INSERT INTO user_role (app_user_id, role) SELECT id, 'ADMIN' FROM admin;

COMMIT;

SELECT username, is_active FROM app_user ORDER BY username;
SQL

echo "Pronto. Entre em https://${DOMAIN} com o usuário ${ADMIN_USERNAME}."
echo "Os demais usuários (garçom, caixa, cozinha) o administrador cria pela tela de administração."
