-- Durable buffer schema (DESIGN §5). One SQLite file at /var/lib/module/buffer.db.
-- WAL mode handles concurrent reader/writer; the single Java process owns one
-- writer connection. Timestamps are epoch-millis INTEGERs (see BufferStore).
--
-- The synchronous PRAGMA at the end is only a placeholder: BufferStore applies
-- config.ackDurability at open, overriding it.
--
-- CREATE ... IF NOT EXISTS never alters an existing table: a column change needs a
-- BufferStore.migrate step (this file is not a migration).

-- One row per event FILE discovered in an inbox. Rows are never evicted by retention —
-- they are the audit trail, and the checksum index keeps a re-dropped file from being
-- consumed twice.
CREATE TABLE IF NOT EXISTS files (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  file_id           TEXT NOT NULL UNIQUE,     -- <file_path>@<first 12 hex of checksum>
  file_path         TEXT NOT NULL,            -- absolute path at discovery (before the .done rename)
  file_name         TEXT NOT NULL,
  source_name       TEXT NOT NULL,            -- config.sources[].name (provenance)
  current_path      TEXT NOT NULL,            -- after rename (.done/.error); == file_path when rename failed
  size_bytes        INTEGER NOT NULL,
  checksum          TEXT NOT NULL,            -- sha256 hex of the bytes
  file_mtime        INTEGER NOT NULL,
  discovered_at     INTEGER NOT NULL,
  consumed_at       INTEGER,
  status            TEXT NOT NULL,            -- consumed | partial | error | duplicate
  event_count       INTEGER,                  -- events stored from this file
  rejected_count    INTEGER,                  -- events rejected (reasons in error_message)
  duplicate_count   INTEGER,                  -- events already stored with the same key and bytes
  error_message     TEXT,
  rename_failed     INTEGER DEFAULT 0,
  redelivery_count  INTEGER DEFAULT 0         -- same bytes re-landed at the same path
);

CREATE INDEX IF NOT EXISTS files_checksum ON files(checksum);
CREATE INDEX IF NOT EXISTS files_path ON files(file_path);
CREATE INDEX IF NOT EXISTS files_source ON files(source_name, status);

-- One row per OCSF EVENT, stored as written (body). The other columns are copies of
-- top-level event fields, extracted at ingest for the OCSF interface operations.
CREATE TABLE IF NOT EXISTS events (
  seq               INTEGER PRIMARY KEY AUTOINCREMENT,
  event_key         TEXT NOT NULL,            -- metadata.uid, else <fileId>#<line> (DESIGN §2)
  class_uid         INTEGER NOT NULL,
  category_uid      INTEGER,
  time              INTEGER,                  -- OCSF time, epoch-millis; NULL when absent
  severity_id       INTEGER,
  metadata_version  TEXT,
  file_id           TEXT NOT NULL,
  source_name       TEXT NOT NULL,
  line              INTEGER NOT NULL,         -- NDJSON line / JSON array index, 1-based
  received_at       INTEGER NOT NULL,         -- ingest time; retention axis
  body              TEXT NOT NULL,            -- the event JSON as written
  UNIQUE (class_uid, event_key)
);

-- list<Class>: one class, newest first.
CREATE INDEX IF NOT EXISTS events_class_time ON events(class_uid, time DESC, seq DESC);
-- retention (maxAge by ingest time, maxBytes oldest first).
CREATE INDEX IF NOT EXISTS events_received ON events(received_at, seq);
CREATE INDEX IF NOT EXISTS events_file ON events(file_id);

PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
