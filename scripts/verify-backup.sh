#!/usr/bin/env bash
# verify-backup.sh — dumps each vnshop Postgres DB from a running container,
# restores it to a throwaway postgres:17.9 container, and asserts that the
# core smoke-query tables contain at least one row.
#
# Exit 0 : all verified databases are healthy
# Exit 1 : dump/restore failed, a table is empty, or no containers found
#
# Usage (local):
#   POSTGRES_PASSWORD=mysecret ./scripts/verify-backup.sh
#
# POSTGRES_PASSWORD defaults to "vnshop" (matches docker-compose.yml default).

set -euo pipefail

POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-vnshop}"
PG_IMAGE="postgres:17.9"
TIMESTAMP=$(date +%s)
THROWAWAY_CONTAINERS=()
VERIFIED=0
FAILED=0

cleanup() {
  local code=$?
  if [ ${#THROWAWAY_CONTAINERS[@]} -gt 0 ]; then
    echo "[verify-backup] Tearing down throwaway containers..."
    for cname in "${THROWAWAY_CONTAINERS[@]}"; do
      docker rm -f "$cname" >/dev/null 2>&1 || true
    done
  fi
  exit "$code"
}
trap cleanup EXIT

# Format: "container_name:db_name:schema:smoke_table"
# Covers the three tables required by the acceptance criteria.
TARGETS=(
  "vnshop-postgres-user:vnshop_user:user_svc:users"
  "vnshop-postgres-order:vnshop_order:order_svc:orders"
  "vnshop-postgres-product:vnshop_product:product_svc:products"
)

for entry in "${TARGETS[@]}"; do
  IFS=: read -r container dbname schema table <<< "$entry"

  running=$(docker inspect --format '{{.State.Running}}' "$container" 2>/dev/null || echo 'false')
  if [ "$running" != 'true' ]; then
    echo "[verify-backup] SKIP: $container is not running"
    continue
  fi

  echo "[verify-backup] === $container  db=$dbname  table=${schema}.${table} ==="

  THROWAWAY="vnshop-verify-${TIMESTAMP}-${dbname//_/-}"
  THROWAWAY_CONTAINERS+=("$THROWAWAY")

  # Start throwaway container — no network needed; data flows via docker exec pipe.
  docker run -d \
    --name "$THROWAWAY" \
    -e POSTGRES_USER=vnshop \
    -e POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
    -e POSTGRES_DB="$dbname" \
    "$PG_IMAGE" >/dev/null

  echo "[verify-backup] Waiting for $THROWAWAY to accept connections..."
  ready=0
  for i in $(seq 1 30); do
    if docker exec "$THROWAWAY" pg_isready -U vnshop -d "$dbname" -q 2>/dev/null; then
      ready=1
      break
    fi
    sleep 1
  done

  if [ "$ready" -eq 0 ]; then
    echo "[verify-backup] ERROR: $THROWAWAY did not become ready within 30 s"
    FAILED=$((FAILED + 1))
    continue
  fi

  # Dump the live DB and restore it directly into the throwaway container.
  echo "[verify-backup] Dumping $dbname -> restoring to $THROWAWAY..."
  if ! docker exec \
        -e PGPASSWORD="$POSTGRES_PASSWORD" \
        "$container" \
        pg_dump -U vnshop -d "$dbname" \
      | docker exec -i \
        -e PGPASSWORD="$POSTGRES_PASSWORD" \
        "$THROWAWAY" \
        psql -U vnshop -d "$dbname" -q; then
    echo "[verify-backup] ERROR: dump/restore pipeline failed for $dbname"
    FAILED=$((FAILED + 1))
    continue
  fi

  # Smoke query — assert rows > 0.
  echo "[verify-backup] Smoke query: SELECT COUNT(*) FROM \"${schema}\".\"${table}\"..."
  ROW_COUNT=$(docker exec \
    -e PGPASSWORD="$POSTGRES_PASSWORD" \
    "$THROWAWAY" \
    psql -U vnshop -d "$dbname" -t -A \
    -c "SELECT COUNT(*) FROM \"${schema}\".\"${table}\";" 2>&1 || echo "ERROR")

  if ! [[ "$ROW_COUNT" =~ ^[0-9]+$ ]]; then
    echo "[verify-backup] ERROR: unexpected result from smoke query: '${ROW_COUNT}'"
    FAILED=$((FAILED + 1))
    continue
  fi

  if [ "$ROW_COUNT" -eq 0 ]; then
    echo "[verify-backup] FAIL: ${schema}.${table} has 0 rows in restored backup"
    FAILED=$((FAILED + 1))
  else
    echo "[verify-backup] OK: ${schema}.${table} has ${ROW_COUNT} row(s)"
    VERIFIED=$((VERIFIED + 1))
  fi
done

echo ""
echo "[verify-backup] Results: verified=${VERIFIED}  failed=${FAILED}"

if [ "$FAILED" -gt 0 ]; then
  echo "[verify-backup] FAIL: ${FAILED} database(s) did not pass verification"
  exit 1
fi

if [ "$VERIFIED" -eq 0 ]; then
  echo "[verify-backup] FAIL: No databases were verified (are the containers running?)"
  exit 1
fi

echo "[verify-backup] All ${VERIFIED} database(s) verified successfully"
exit 0
