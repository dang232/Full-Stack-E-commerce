#!/usr/bin/env bash
# Pre-create Kafka topics that services declare as @MessagePattern consumers
# at startup. NestJS's KafkaJS client refuses to start the consumer if the
# topic is missing — even with `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true` on the
# broker, because consumer-side metadata refresh races topic auto-creation
# and the consumer crashes before the auto-create finishes.
#
# Idempotent: kafka-topics --create --if-not-exists is a no-op when the
# topic already exists.
#
# Run after `docker compose up -d` and before the first messaging request:
#   bash infra/scripts/init-kafka-topics.sh

set -euo pipefail

KAFKA_CONTAINER="${KAFKA_CONTAINER:-vnshop-kafka}"
BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"

# Topics consumed by Nest @MessagePattern handlers. The producer side will
# auto-create when it publishes (KAFKA_AUTO_CREATE_TOPICS_ENABLE=true), but
# the consumer side races that and crashes the service.
TOPICS=(
  "messaging.message.sent"
)

echo "==> ensuring kafka topics exist via ${KAFKA_CONTAINER}"
for topic in "${TOPICS[@]}"; do
  docker exec "${KAFKA_CONTAINER}" \
    kafka-topics --bootstrap-server "${BOOTSTRAP}" \
    --create --if-not-exists \
    --topic "${topic}" --partitions 1 --replication-factor 1 \
    >/dev/null
  echo "  + ${topic}"
done

echo "==> done. Existing topics:"
docker exec "${KAFKA_CONTAINER}" kafka-topics --bootstrap-server "${BOOTSTRAP}" --list
