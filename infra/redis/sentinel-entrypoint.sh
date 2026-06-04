#!/bin/sh
set -e
sed "s|\$REDIS_PASSWORD|${REDIS_PASSWORD}|g" \
  /etc/redis/sentinel.conf.template > /tmp/sentinel.conf
exec redis-sentinel /tmp/sentinel.conf
