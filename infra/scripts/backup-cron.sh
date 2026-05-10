#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="${LOG_DIR:-${SCRIPT_DIR}/../backups/logs}"
LOG_FILE="${LOG_DIR}/backup-$(date +%Y%m%d).log"

mkdir -p "${LOG_DIR}"

{
  echo "[$(date --iso-8601=seconds)] Starting scheduled VNShop backup"
  "${SCRIPT_DIR}/backup.sh"
  echo "[$(date --iso-8601=seconds)] Scheduled VNShop backup finished"
} >> "${LOG_FILE}" 2>&1
