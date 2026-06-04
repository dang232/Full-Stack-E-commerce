#!/bin/bash
# Scale partitions on existing Kafka cluster
# Usage: ./kafka-partition-scale.sh
# NOTE: Partition increase is a one-way operation. Key-based partitioning is maintained.

set -euo pipefail

BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"
COMMAND_CONFIG="${KAFKA_COMMAND_CONFIG:-/etc/kafka/client.properties}"

echo "=== VNShop Kafka Partition Scaling ==="
echo "Bootstrap: $BOOTSTRAP_SERVER"
echo ""

# Topics to scale
declare -A TOPICS=(
  ["product-events"]=12
  ["order.created"]=6
  ["order.updated"]=6
  ["order.paid"]=6
  ["order.shipped"]=6
  ["order.cancelled"]=6
  ["payment.completed"]=6
  ["payment.refund.requested"]=6
  ["payment.refunded"]=6
  ["inventory.released"]=6
  ["shipping.cancelled"]=6
)

for TOPIC in "${!TOPICS[@]}"; do
  PARTITIONS=${TOPICS[$TOPIC]}
  echo "Scaling $TOPIC to $PARTITIONS partitions..."
  kafka-topics --alter --bootstrap-server "$BOOTSTRAP_SERVER" \
    --command-config "$COMMAND_CONFIG" \
    --topic "$TOPIC" --partitions "$PARTITIONS" || echo "  WARN: Failed to scale $TOPIC (may already have >= $PARTITIONS partitions)"
done

echo ""
echo "=== Partition scaling complete ==="
echo "IMPORTANT: Update consumer concurrency to match new partition counts."
echo "  - product-events consumers: concurrency=12"
echo "  - order.* consumers: concurrency=6"
echo "  - payment.* consumers: concurrency=6"
echo "  - inventory/shipping consumers: concurrency=6"
echo ""
echo "K8s HPA targets: scale consumer pods based on kafka_consumer_fetch_manager_records_lag_max > 1000"
