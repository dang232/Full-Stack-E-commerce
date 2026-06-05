#!/bin/bash
set -euo pipefail

TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_DIR="/tmp/backups/${TIMESTAMP}"
S3_BUCKET="${S3_BACKUP_BUCKET:-vnshop-backups}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"

DATABASES=(
  "postgres-order:order_svc"
  "postgres-payment:payment_svc"
  "postgres-user:user_svc"
  "postgres-product:product_svc"
  "postgres-inventory:inventory_svc"
  "postgres-shipping:shipping_svc"
  "postgres-search:search_svc"
  "postgres-seller-finance:seller_finance_svc"
)

mkdir -p "${BACKUP_DIR}"
echo "[$(date)] Starting backup run ${TIMESTAMP}"

for entry in "${DATABASES[@]}"; do
  HOST="${entry%%:*}"
  DB="${entry##*:}"
  DUMP_FILE="${BACKUP_DIR}/${DB}-${TIMESTAMP}.sql.gz"

  echo "  Backing up ${DB} from ${HOST}..."
  PGPASSWORD="${POSTGRES_PASSWORD}" pg_dump \
    -h "${HOST}" -U vnshop -d "${DB}" \
    --no-owner --no-acl \
    | gzip > "${DUMP_FILE}"

  echo "  Uploading to S3..."
  aws s3 cp "${DUMP_FILE}" "s3://${S3_BUCKET}/${DB}/${TIMESTAMP}.sql.gz" \
    --storage-class STANDARD_IA
done

echo "  Pruning backups older than ${RETENTION_DAYS} days..."
CUTOFF=$(date -d "-${RETENTION_DAYS} days" +%Y%m%d 2>/dev/null || date -v-${RETENTION_DAYS}d +%Y%m%d)
for entry in "${DATABASES[@]}"; do
  DB="${entry##*:}"
  aws s3 ls "s3://${S3_BUCKET}/${DB}/" 2>/dev/null | while read -r line; do
    FILE=$(echo "${line}" | awk '{print $4}')
    FILE_DATE=$(echo "${FILE}" | grep -oP '^\d{8}' || true)
    if [[ -n "${FILE_DATE}" && "${FILE_DATE}" < "${CUTOFF}" ]]; then
      aws s3 rm "s3://${S3_BUCKET}/${DB}/${FILE}"
      echo "    Pruned: ${FILE}"
    fi
  done
done

rm -rf "${BACKUP_DIR}"
echo "[$(date)] Backup run ${TIMESTAMP} complete"
