-- ChartDesk alerts schema (D1 / SQLite)

CREATE TABLE IF NOT EXISTS alerts (
  id TEXT PRIMARY KEY,
  device_token TEXT NOT NULL,
  symbol TEXT NOT NULL,
  direction TEXT NOT NULL CHECK (direction IN ('above', 'below')),
  price REAL NOT NULL,
  active INTEGER NOT NULL DEFAULT 1,
  created_at INTEGER NOT NULL,
  triggered_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_alerts_token ON alerts (device_token, active);
CREATE INDEX IF NOT EXISTS idx_alerts_active ON alerts (active, symbol);

CREATE TABLE IF NOT EXISTS alert_events (
  id TEXT PRIMARY KEY,
  alert_id TEXT NOT NULL,
  device_token TEXT NOT NULL,
  symbol TEXT NOT NULL,
  direction TEXT NOT NULL,
  target REAL NOT NULL,
  price REAL NOT NULL,
  triggered_at INTEGER NOT NULL,
  seen INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_events_token ON alert_events (device_token, triggered_at);
