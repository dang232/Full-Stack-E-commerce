#!/usr/bin/env bash
# check-secrets.sh — Validate that all required secret environment variables
# are set and non-empty before starting services.
#
# Usage:
#   ./scripts/check-secrets.sh
#   ./scripts/check-secrets.sh --quiet   # suppress per-var output, only final status
#
# Exit codes:
#   0 — all required secrets are present
#   1 — one or more secrets are missing or empty
#
# Docker Compose integration (add to any service that needs secrets):
#   command: ["sh", "-c", "/app/scripts/check-secrets.sh && exec java -jar app.jar"]
# Or as a dependency on an init container that runs this script.

set -euo pipefail

QUIET=false
if [[ "${1:-}" == "--quiet" ]]; then
  QUIET=true
fi

# ---------------------------------------------------------------------------
# Required secrets — add/remove entries as the project evolves
# ---------------------------------------------------------------------------
REQUIRED_VARS=(
  # Kafka broker JAAS identities
  KAFKA_ADMIN_PASSWORD
  KAFKA_ORDER_PASSWORD
  KAFKA_PAYMENT_PASSWORD
  KAFKA_INVENTORY_PASSWORD
  KAFKA_PRODUCT_PASSWORD
  KAFKA_SHIPPING_PASSWORD
  KAFKA_FINANCE_PASSWORD
  KAFKA_SEARCH_PASSWORD
  KAFKA_RECOMMENDATIONS_PASSWORD

  # Kafka service client passwords
  KAFKA_SVC_ORDER_PASSWORD
  KAFKA_SVC_PAYMENT_PASSWORD
  KAFKA_SVC_INVENTORY_PASSWORD
  KAFKA_SVC_PRODUCT_PASSWORD
  KAFKA_SVC_SHIPPING_PASSWORD
  KAFKA_SVC_FINANCE_PASSWORD
  KAFKA_SVC_SEARCH_PASSWORD
  KAFKA_SVC_RECOMMENDATIONS_PASSWORD

  # Elasticsearch
  ELASTIC_PASSWORD
)

# ---------------------------------------------------------------------------
# Validation loop
# ---------------------------------------------------------------------------
MISSING=()

for var in "${REQUIRED_VARS[@]}"; do
  value="${!var:-}"
  if [[ -z "$value" ]]; then
    MISSING+=("$var")
    if [[ "$QUIET" == false ]]; then
      echo "MISSING: $var" >&2
    fi
  else
    if [[ "$QUIET" == false ]]; then
      echo "OK:      $var"
    fi
  fi
done

# ---------------------------------------------------------------------------
# Result
# ---------------------------------------------------------------------------
if [[ ${#MISSING[@]} -gt 0 ]]; then
  echo "" >&2
  echo "ERROR: ${#MISSING[@]} required secret(s) are unset or empty:" >&2
  for var in "${MISSING[@]}"; do
    echo "  - $var" >&2
  done
  echo "" >&2
  echo "To fix for local dev:" >&2
  echo "  cp secrets.env.local.example secrets.env.local   # edit with real values" >&2
  echo "  set -a && source secrets.env.local && set +a" >&2
  echo "  docker compose up" >&2
  echo "" >&2
  echo "For staging/production inject secrets via your platform secret manager" >&2
  echo "and ensure they are exported into the process environment before startup." >&2
  exit 1
fi

echo ""
echo "All ${#REQUIRED_VARS[@]} required secrets are present."
exit 0
