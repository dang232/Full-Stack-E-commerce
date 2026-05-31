#!/usr/bin/env bash
# Pre-create Kafka topics that services declare as @MessagePattern consumers
# at startup. NestJS's KafkaJS client refuses to start the consumer if the
# topic is missing — even with `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true` on the
# broker, because consumer-side metadata refresh races topic auto-creation
# and the consumer crashes before the auto-create finishes.
#
# Idempotent: kafka-topics --create --if-not-exists is a no-op when the
# topic already exists. For partition bumps on existing topics, run with
# BUMP_PARTITIONS=1 to use --alter --partitions; this only ever adds
# partitions, never removes them.
#
# Run after `docker compose up -d` and before the first messaging request:
#   bash infra/scripts/init-kafka-topics.sh
#   BUMP_PARTITIONS=1 bash infra/scripts/init-kafka-topics.sh   # one-shot to widen existing topics

set -euo pipefail

KAFKA_CONTAINER="${KAFKA_CONTAINER:-vnshop-kafka}"
BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"

# Topics needed at boot. order.* topics get 3 partitions so the
# OrderProjectionListener's concurrency=3 setting actually parallelises
# within each topic, not just across topics. Each entry is "name:partitions".
TOPICS=(
  "messaging.message.sent:1"
  "order.created:3"
  "order.updated:3"
  "order.paid:3"
  "order.shipped:3"
  "order.cancelled:3"
)

echo "==> ensuring kafka topics exist via ${KAFKA_CONTAINER}"
for entry in "${TOPICS[@]}"; do
  topic="${entry%%:*}"
  partitions="${entry##*:}"
  docker exec "${KAFKA_CONTAINER}" \
    kafka-topics --bootstrap-server "${BOOTSTRAP}" \
    --create --if-not-exists \
    --topic "${topic}" --partitions "${partitions}" --replication-factor 1 \
    >/dev/null
  echo "  + ${topic} (partitions=${partitions})"

  if [[ "${BUMP_PARTITIONS:-0}" == "1" ]]; then
    current=$(docker exec "${KAFKA_CONTAINER}" \
      kafka-topics --bootstrap-server "${BOOTSTRAP}" --describe --topic "${topic}" \
      | head -1 | grep -oE 'PartitionCount: [0-9]+' | awk '{print $2}')
    if [[ -n "${current}" && "${current}" -lt "${partitions}" ]]; then
      docker exec "${KAFKA_CONTAINER}" \
        kafka-topics --bootstrap-server "${BOOTSTRAP}" \
        --alter --topic "${topic}" --partitions "${partitions}" \
        >/dev/null
      echo "    ↳ bumped ${topic} ${current} → ${partitions} partitions"
    fi
  fi
done

echo "==> done. Existing topics:"
docker exec "${KAFKA_CONTAINER}" kafka-topics --bootstrap-server "${BOOTSTRAP}" --list
