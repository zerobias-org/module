-- Durable message buffer schema (DESIGN §8). One SQLite file at
-- /var/lib/module/buffer.db. WAL mode handles concurrent reader/writer; the
-- single Java process owns one writer thread and pooled reader connections.
--
-- synchronous defaults to NORMAL (fsync at WAL checkpoints). Operators with
-- clinical feeds set connectionProfile.ackDurability=full -> synchronous=FULL
-- (fsync per row) for a zero-loss ack path (DESIGN §8.1); the listener applies
-- that PRAGMA at startup, so it is intentionally NOT pinned here.

CREATE TABLE IF NOT EXISTS messages (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  received_at       TIMESTAMP NOT NULL,
  control_id        TEXT NOT NULL UNIQUE,
  message_structure TEXT NOT NULL,     -- ADT_A01, ORU_R01, ...
  message_code      TEXT NOT NULL,     -- ADT, ORU, ...
  trigger_event     TEXT NOT NULL,     -- A01, R01, ...
  sending_app       TEXT,
  sending_facility  TEXT,
  hl7_version       TEXT,
  schema_id         TEXT NOT NULL,     -- which collection schema this row claims
  raw_er7           BLOB NOT NULL,     -- canonical ER7 for audit/replay
  mapped_json       TEXT NOT NULL,     -- typed JSON per DESIGN §5
  status            TEXT NOT NULL DEFAULT 'new',  -- new | in_flight | acked
  lease_id          TEXT,              -- non-null when in_flight
  in_flight_until   TIMESTAMP,
  acked_at          TIMESTAMP
);

CREATE INDEX IF NOT EXISTS messages_drain ON messages(schema_id, status, received_at);
CREATE INDEX IF NOT EXISTS messages_lease ON messages(lease_id) WHERE lease_id IS NOT NULL;

PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
