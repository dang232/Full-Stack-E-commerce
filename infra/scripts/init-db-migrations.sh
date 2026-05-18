#!/usr/bin/env bash
# Apply raw SQL migrations for the NestJS services (notification, messaging)
# that ship migration files under src/db/migration/ but never wire them into
# a runtime migration runner. The Java services use Flyway, which auto-runs
# on startup; the NestJS services rely on this script.
#
# Idempotent — every migration uses CREATE TABLE IF NOT EXISTS / ADD COLUMN
# IF NOT EXISTS so reruns are safe.
#
# Run after `docker compose up -d` and before the first request hits the
# affected services:
#   bash infra/scripts/init-db-migrations.sh

set -euo pipefail

POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-vnshop-postgres-legacy}"
DB_USER="${DB_USER:-vnshop}"
DB_NAME="${DB_NAME:-vnshop}"

apply() {
  local label="$1"
  local file="$2"
  echo "  + ${label}"
  docker exec -i "${POSTGRES_CONTAINER}" psql -U "${DB_USER}" -d "${DB_NAME}" -v ON_ERROR_STOP=1 < "${file}" >/dev/null
}

echo "==> applying nestjs-service migrations via ${POSTGRES_CONTAINER}"

# notification-service — schema + read-state. Without these, every
# /notifications/* endpoint 500s with `relation "notification_svc.notifications"
# does not exist`.
apply "notification-service V1__notification_schema.sql" \
  "services/notification-service/src/db/migration/V1__notification_schema.sql"
apply "notification-service V2__notification_read_state.sql" \
  "services/notification-service/src/db/migration/V2__notification_read_state.sql"

# messaging-service — threads + messages + indices. Without these, REST
# endpoints succeed but the underlying ORM blows up on first thread create.
apply "messaging-service V1__messaging_schema.sql" \
  "services/messaging-service/src/db/migration/V1__messaging_schema.sql"

echo "==> done. Schemas now present:"
docker exec "${POSTGRES_CONTAINER}" psql -U "${DB_USER}" -d "${DB_NAME}" -c '\dn' \
  | grep -E "notification_svc|messaging_svc" || true
