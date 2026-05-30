-- Enable TimescaleDB extension
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Health metrics hypertable
CREATE TABLE health_metrics (
  time        TIMESTAMPTZ NOT NULL,
  service_id  TEXT NOT NULL,
  status      TEXT NOT NULL,
  response_ms INTEGER,
  details     JSONB
);

SELECT create_hypertable('health_metrics', 'time');

-- Retention: auto-drop after 30 days
SELECT add_retention_policy('health_metrics', INTERVAL '30 days');

-- Continuous aggregate for hourly rollups
CREATE MATERIALIZED VIEW health_hourly
WITH (timescaledb.continuous) AS
SELECT
  time_bucket('1 hour', time) AS bucket,
  service_id,
  avg(response_ms)::INTEGER AS avg_ms,
  percentile_cont(0.95) WITHIN GROUP (ORDER BY response_ms)::INTEGER AS p95_ms,
  count(*) FILTER (WHERE status = 'up') * 100.0 / GREATEST(count(*), 1) AS uptime_pct
FROM health_metrics
GROUP BY bucket, service_id
WITH NO DATA;

SELECT add_continuous_aggregate_policy('health_hourly',
  start_offset => INTERVAL '2 hours',
  end_offset => INTERVAL '1 hour',
  schedule_interval => INTERVAL '1 hour');

-- Alerts table
CREATE TABLE alerts (
  id          SERIAL PRIMARY KEY,
  service_id  TEXT NOT NULL,
  type        TEXT NOT NULL,
  message     TEXT,
  created_at  TIMESTAMPTZ DEFAULT NOW(),
  resolved_at TIMESTAMPTZ
);

CREATE INDEX idx_alerts_service_id ON alerts(service_id);
CREATE INDEX idx_alerts_created_at ON alerts(created_at DESC);
CREATE INDEX idx_health_metrics_service_time ON health_metrics(service_id, time DESC);
