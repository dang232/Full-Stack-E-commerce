#!/usr/bin/env bash
# validate-istio-call-graph.sh
#
# Checks that every service-to-service call found in application code is
# covered by an AuthorizationPolicy in authorization-policies.yaml.
#
# Usage: bash infra/scripts/validate-istio-call-graph.sh
# Exit : 0 = all calls covered, 1 = uncovered calls found
#
# Detection patterns:
#   HTTP  — @FeignClient(name/value = "some-service", ...)
#   gRPC  — ManagedChannelBuilder.forAddress("some-service", ...) or
#            forTarget("some-service:PORT")
#   Kafka — KafkaTemplate.send("topic", ...) where topic maps to a known
#            callback pattern (payment-callback -> order-service)
#
# The script does NOT do full AST parsing; it relies on consistent naming
# conventions where the service hostname matches the k8s Service name.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
POLICY_FILE="${REPO_ROOT}/infra/k8s/base/istio/authorization-policies.yaml"
SRC_DIR="${REPO_ROOT}"

# Colour helpers (disabled if not a tty)
if [ -t 1 ]; then
  RED='\033[0;31m'; YELLOW='\033[1;33m'; GREEN='\033[0;32m'; RESET='\033[0m'
else
  RED=''; YELLOW=''; GREEN=''; RESET=''
fi

echo "=== Istio call-graph validator ==="
echo "Policy file : ${POLICY_FILE}"
echo "Source root : ${SRC_DIR}"
echo ""

# ---------------------------------------------------------------------------
# 1. Extract allowed (source-service -> dest-service) pairs from policy YAML
# ---------------------------------------------------------------------------
# We read principal lines like:
#   - cluster.local/ns/vnshop/sa/vnshop-order-service
# and the selector label immediately before them:
#   matchLabels:
#     app: inventory-service
#
# Strategy: collect (selector_app, principal_suffix) pairs.

declare -A ALLOWED  # key = "SOURCE->DEST", value = 1

current_dest=""
while IFS= read -r line; do
  # Detect selector app label (destination)
  if [[ "$line" =~ app:[[:space:]]+([a-z0-9-]+) ]]; then
    current_dest="${BASH_REMATCH[1]}"
  fi
  # Detect principal (source)
  if [[ "$line" =~ cluster\.local/ns/vnshop/sa/vnshop-([a-z0-9-]+) ]]; then
    src="${BASH_REMATCH[1]}"
    if [[ -n "$current_dest" ]]; then
      ALLOWED["${src}->${current_dest}"]=1
    fi
  fi
done < "${POLICY_FILE}"

echo "Allowed pairs extracted from AuthorizationPolicy:"
for pair in "${!ALLOWED[@]}"; do
  echo "  ${pair}"
done | sort
echo ""

# ---------------------------------------------------------------------------
# 2. Scan application code for service-to-service calls
# ---------------------------------------------------------------------------

declare -A CODE_CALLS  # key = "SOURCE->DEST", value = "file:line"

# Helper: record a detected call
record_call() {
  local caller="$1" callee="$2" location="$3"
  CODE_CALLS["${caller}->${callee}"]="${location}"
}

# --- 2a. @FeignClient HTTP calls ---
# Matches: @FeignClient(name = "cart-service"  or  value = "cart-service"
# or      @FeignClient("cart-service")
# The enclosing module directory name is used as the caller service name.
while IFS= read -r match; do
  file="${match%%:*}"
  rest="${match#*:}"
  lineno="${rest%%:*}"
  content="${rest#*:}"

  # Extract the target service name
  if [[ "$content" =~ (name|value)[[:space:]]*=[[:space:]]*\"([a-z0-9-]+)\" ]]; then
    callee="${BASH_REMATCH[2]}"
  elif [[ "$content" =~ @FeignClient\(\"([a-z0-9-]+)\" ]]; then
    callee="${BASH_REMATCH[1]}"
  else
    continue
  fi

  # Infer caller from path (first segment after src root that looks like a service)
  rel="${file#${SRC_DIR}/}"
  caller=""
  for segment in $(echo "$rel" | tr '/' ' '); do
    if [[ "$segment" =~ ^(api-gateway|user-service|product-service|inventory-service|cart-service|search-service|notification-service|order-service|payment-service|shipping-service)$ ]]; then
      caller="$segment"
      break
    fi
  done
  [[ -z "$caller" ]] && caller="api-gateway"  # feign clients typically live in gateway/aggregator

  record_call "$caller" "$callee" "${file}:${lineno}"
done < <(grep -rn --include="*.java" "@FeignClient" "${SRC_DIR}" 2>/dev/null || true)

# --- 2b. gRPC ManagedChannel / forAddress / forTarget calls ---
# Matches: ManagedChannelBuilder.forAddress("inventory-service", 9093)
#          .forTarget("payment-service:9094")
while IFS= read -r match; do
  file="${match%%:*}"
  rest="${match#*:}"
  lineno="${rest%%:*}"
  content="${rest#*:}"

  if [[ "$content" =~ forAddress\(\"([a-z0-9-]+)\" ]]; then
    callee="${BASH_REMATCH[1]}"
  elif [[ "$content" =~ forTarget\(\"([a-z0-9-]+):[0-9]+ ]]; then
    callee="${BASH_REMATCH[1]}"
  else
    continue
  fi

  rel="${file#${SRC_DIR}/}"
  caller=""
  for segment in $(echo "$rel" | tr '/' ' '); do
    if [[ "$segment" =~ ^(api-gateway|user-service|product-service|inventory-service|cart-service|search-service|notification-service|order-service|payment-service|shipping-service)$ ]]; then
      caller="$segment"
      break
    fi
  done
  [[ -z "$caller" ]] && continue

  record_call "$caller" "$callee" "${file}:${lineno}"
done < <(grep -rn --include="*.java" -E "forAddress\(|forTarget\(" "${SRC_DIR}" 2>/dev/null || true)

# --- 2c. gRPC stub / channel wiring via @GrpcClient or @Value grpc.client ---
# Matches: @GrpcClient("inventory-service")
while IFS= read -r match; do
  file="${match%%:*}"
  rest="${match#*:}"
  lineno="${rest%%:*}"
  content="${rest#*:}"

  if [[ "$content" =~ @GrpcClient\(\"([a-z0-9-]+)\" ]]; then
    callee="${BASH_REMATCH[1]}"
  else
    continue
  fi

  rel="${file#${SRC_DIR}/}"
  caller=""
  for segment in $(echo "$rel" | tr '/' ' '); do
    if [[ "$segment" =~ ^(api-gateway|user-service|product-service|inventory-service|cart-service|search-service|notification-service|order-service|payment-service|shipping-service)$ ]]; then
      caller="$segment"
      break
    fi
  done
  [[ -z "$caller" ]] && continue

  record_call "$caller" "$callee" "${file}:${lineno}"
done < <(grep -rn --include="*.java" "@GrpcClient" "${SRC_DIR}" 2>/dev/null || true)

# --- 2d. KafkaTemplate.send() with payment-callback topics (async callbacks) ---
# payment-service sends to a topic consumed by order-service.
# These cross the broker, not the mesh, so we note them but do NOT fail on them.
KAFKA_CALLBACKS=()
while IFS= read -r match; do
  file="${match%%:*}"
  rest="${match#*:}"
  lineno="${rest%%:*}"
  content="${rest#*:}"
  if [[ "$content" =~ kafkaTemplate\.send\(\"([^\"]+)\" ]]; then
    topic="${BASH_REMATCH[1]}"
    KAFKA_CALLBACKS+=("${file}:${lineno} -> topic:${topic}")
  fi
done < <(grep -rn --include="*.java" "kafkaTemplate\.send\|kafkaTemplate\.sendDefault" "${SRC_DIR}" 2>/dev/null || true)

# ---------------------------------------------------------------------------
# 3. Compare detected calls against allowed pairs
# ---------------------------------------------------------------------------

UNCOVERED=()

echo "Service-to-service calls detected in code:"
if [[ ${#CODE_CALLS[@]} -eq 0 ]]; then
  echo "  (none found — @FeignClient/@GrpcClient/forAddress patterns not matched)"
else
  for pair in "${!CODE_CALLS[@]}"; do
    loc="${CODE_CALLS[$pair]}"
    if [[ -n "${ALLOWED[$pair]+_}" ]]; then
      echo -e "  ${GREEN}OK${RESET}      ${pair}  (${loc})"
    else
      echo -e "  ${RED}MISSING${RESET} ${pair}  (${loc})"
      UNCOVERED+=("$pair")
    fi
  done | sort
fi
echo ""

if [[ ${#KAFKA_CALLBACKS[@]} -gt 0 ]]; then
  echo -e "${YELLOW}NOTE${RESET}: Kafka sends detected (async — no AuthorizationPolicy required):"
  for entry in "${KAFKA_CALLBACKS[@]}"; do
    echo "  ${entry}"
  done
  echo ""
fi

# ---------------------------------------------------------------------------
# 4. Exit
# ---------------------------------------------------------------------------

if [[ ${#UNCOVERED[@]} -gt 0 ]]; then
  echo -e "${RED}FAIL${RESET}: ${#UNCOVERED[@]} service call(s) in code are not covered by an AuthorizationPolicy:"
  for pair in "${UNCOVERED[@]}"; do
    echo "  - ${pair}"
  done
  echo ""
  echo "Add the missing allow rules to infra/k8s/base/istio/authorization-policies.yaml"
  exit 1
else
  echo -e "${GREEN}PASS${RESET}: All detected service-to-service calls are covered by AuthorizationPolicy."
  exit 0
fi
