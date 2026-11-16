#!/usr/bin/env bash
# Daily dump of the database, kept for 14 days. The VPS backup protects the whole machine;
# this one is the copy that can be read on its own. Move the folder off the VPS too
# (rclone, rsync or a download) so a lost VPS does not take the backups with it.
#
# Cron, on the VPS (crontab -e):
#   30 3 * * * /home/castel/castel/deploy/backup.sh >> /home/castel/backup.log 2>&1
set -euo pipefail

cd "$(dirname "$0")/.."
ENV_FILE="${ENV_FILE:-.env.prod}"
set -a; source "$ENV_FILE"; set +a
read -ra COMPOSE_BASE <<< "${COMPOSE_BIN:-docker compose}"

BACKUP_DIR="${BACKUP_DIR:-$HOME/castel-backups}"
mkdir -p "$BACKUP_DIR"
FILE="$BACKUP_DIR/castel_$(date +%Y%m%d_%H%M%S).dump"

"${COMPOSE_BASE[@]}" --env-file "$ENV_FILE" -f docker-compose.prod.yml exec -T postgres \
  pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > "$FILE"

# An empty file is a failed backup that looks like a good one.
[ -s "$FILE" ] || { echo "backup vazio: $FILE" >&2; exit 1; }

find "$BACKUP_DIR" -name 'castel_*.dump' -mtime +14 -delete
echo "ok $FILE ($(du -h "$FILE" | cut -f1))"
