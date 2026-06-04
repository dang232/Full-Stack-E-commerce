#!/bin/bash
set -e

BROKER="kafka:9092"
ADMIN_CONFIG="/tmp/admin.properties"

# Create admin client config for SASL authentication
cat > $ADMIN_CONFIG <<EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="admin" password="${KAFKA_ADMIN_PASSWORD:-admin-secret-change-me}";
EOF

echo "Waiting for Kafka to be ready..."
until kafka-broker-api-versions --bootstrap-server $BROKER --command-config $ADMIN_CONFIG > /dev/null 2>&1; do
  sleep 2
done
echo "Kafka is ready."

# --- Create Topics ---
TOPICS=(
  "messaging.message.sent:1"
  "product-events:12"
  "order.created:6"
  "order.updated:6"
  "order.paid:6"
  "order.shipped:6"
  "order.cancelled:6"
  "payment.completed:6"
  "payment.refund.requested:6"
  "payment.refunded:6"
  "inventory.released:6"
  "shipping.cancelled:6"
)

for entry in "${TOPICS[@]}"; do
  IFS=':' read -r topic partitions <<< "$entry"
  kafka-topics --bootstrap-server $BROKER --command-config $ADMIN_CONFIG \
    --create --if-not-exists --topic "$topic" --partitions "$partitions" --replication-factor 1
done
echo "All topics created."

# --- Configure ACLs ---
ACL="kafka-acls --bootstrap-server $BROKER --command-config $ADMIN_CONFIG"

# order-service (svc-order): produces order.*, payment.refund.requested
$ACL --add --allow-principal User:svc-order --operation Write --topic order --resource-pattern-type prefixed
$ACL --add --allow-principal User:svc-order --operation Write --topic payment.refund.requested
# order-service: consumes payment.completed, payment.refunded, inventory.released, shipping.cancelled
$ACL --add --allow-principal User:svc-order --operation Read --topic payment.completed
$ACL --add --allow-principal User:svc-order --operation Read --topic payment.refunded
$ACL --add --allow-principal User:svc-order --operation Read --topic inventory.released
$ACL --add --allow-principal User:svc-order --operation Read --topic shipping.cancelled
# order-service: also reads its own order.* topics for projection
$ACL --add --allow-principal User:svc-order --operation Read --topic order --resource-pattern-type prefixed
# order-service consumer groups
$ACL --add --allow-principal User:svc-order --operation Read --group order-service-payment
$ACL --add --allow-principal User:svc-order --operation Read --group order-service-refund
$ACL --add --allow-principal User:svc-order --operation Read --group order-service-projection
$ACL --add --allow-principal User:svc-order --operation Read --group order-service-finance
$ACL --add --allow-principal User:svc-order --operation Read --group order-service-saga-compensation

# payment-service (svc-payment): produces payment.completed, payment.refunded
$ACL --add --allow-principal User:svc-payment --operation Write --topic payment.completed
$ACL --add --allow-principal User:svc-payment --operation Write --topic payment.refunded
# payment-service: consumes payment.refund.requested
$ACL --add --allow-principal User:svc-payment --operation Read --topic payment.refund.requested
$ACL --add --allow-principal User:svc-payment --operation Read --group payment-service-paypal-refund

# inventory-service (svc-inventory): produces inventory.released
$ACL --add --allow-principal User:svc-inventory --operation Write --topic inventory.released

# product-service (svc-product): produces product-events
$ACL --add --allow-principal User:svc-product --operation Write --topic product-events

# shipping-service (svc-shipping): produces shipping.cancelled
$ACL --add --allow-principal User:svc-shipping --operation Write --topic shipping.cancelled

# seller-finance-service (svc-finance): consumes order.created, order.paid, payment.refunded
$ACL --add --allow-principal User:svc-finance --operation Read --topic order.created
$ACL --add --allow-principal User:svc-finance --operation Read --topic order.paid
$ACL --add --allow-principal User:svc-finance --operation Read --topic payment.refunded
$ACL --add --allow-principal User:svc-finance --operation Read --group seller-finance-service
$ACL --add --allow-principal User:svc-finance --operation Read --group seller-finance-service-refund

# search-service (svc-search): consumes product-events
$ACL --add --allow-principal User:svc-search --operation Read --topic product-events
$ACL --add --allow-principal User:svc-search --operation Read --group search-service

# recommendations-service (svc-recommendations): consumes order.created
$ACL --add --allow-principal User:svc-recommendations --operation Read --topic order.created
$ACL --add --allow-principal User:svc-recommendations --operation Read --group recommendations-service

echo "All ACLs configured."
rm -f $ADMIN_CONFIG
