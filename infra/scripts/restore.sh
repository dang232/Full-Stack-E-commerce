#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROJECT_ROOT="$(cd "${INFRA_DIR}/.." && pwd)"
BACKUP_DIR="${INFRA_DIR}/backups"
BACKUP_FILE="${1:-}"

cd "${PROJECT_ROOT}"

if [[ -z "${BACKUP_FILE}" ]]; then
  LATEST="$(find "${BACKUP_DIR}" -maxdepth 1 -type f -name 'vnshop-*.sql.gz' -printf '%T@ %p\n' | sort -nr | awk 'NR==1 {print $2}')"
else
  LATEST="${BACKUP_FILE}"
fi

if [[ -z "${LATEST}" || ! -f "${LATEST}" ]]; then
  echo "No PostgreSQL backup found in ${BACKUP_DIR}" >&2
  exit 1
fi

echo "Restoring PostgreSQL backup: ${LATEST}"
gunzip -c "${LATEST}" | docker compose exec -T postgres psql -U vnshop -d vnshop

echo "Verifying row counts"
docker compose exec -T postgres psql -U vnshop -d vnshop -v ON_ERROR_STOP=1 -c "
SELECT schemaname, relname, n_live_tup::bigint AS estimated_rows
FROM pg_stat_user_tables
ORDER BY schemaname, relname;
"

echo "Restore complete"
