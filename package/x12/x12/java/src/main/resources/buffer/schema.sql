-- Durable buffer schema (DESIGN §8). One SQLite file at /var/lib/module/buffer.db.
-- WAL mode handles concurrent reader/writer; the single Java process owns one
-- writer thread. Timestamps are epoch-millis INTEGERs (see BufferStore).
--
-- synchronous defaults to NORMAL (fsync at WAL checkpoints). Operators set
-- config.ackDurability=full -> synchronous=FULL (fsync per commit) for a zero-loss
-- consume path; BufferStore applies that PRAGMA at open, so it is NOT pinned here.

-- One row per interchange FILE discovered in an inbox. Rows are never evicted by
-- retention — they are the audit trail, and the checksum index is what keeps a
-- re-dropped file from being consumed twice (DESIGN §4.2).
CREATE TABLE IF NOT EXISTS files (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  file_id           TEXT NOT NULL UNIQUE,     -- <file_path>@<first 12 hex of checksum> (DESIGN §2.1)
  file_path         TEXT NOT NULL,            -- absolute path at discovery (before the .done rename)
  file_name         TEXT NOT NULL,
  source_name       TEXT NOT NULL,            -- config.sources[].name (provenance)
  current_path      TEXT NOT NULL,            -- after rename (.done/.error); == file_id when rename failed
  size_bytes        INTEGER NOT NULL,
  checksum          TEXT NOT NULL,            -- sha256 hex of the bytes
  file_mtime        INTEGER NOT NULL,
  discovered_at     INTEGER NOT NULL,
  consumed_at       INTEGER,
  status            TEXT NOT NULL,            -- consumed | error | duplicate
  isa_count         INTEGER,
  transaction_count INTEGER,
  error_message     TEXT,
  rename_failed     INTEGER DEFAULT 0,
  redelivery_count  INTEGER DEFAULT 0         -- same bytes re-landed at the same path (DESIGN §4.2 step 3a)
);

CREATE INDEX IF NOT EXISTS files_checksum ON files(checksum);
CREATE INDEX IF NOT EXISTS files_path ON files(file_path);
CREATE INDEX IF NOT EXISTS files_source ON files(source_name, status);

-- One row per TRANSACTION SET (ST..SE) — the collection element / drain atom.
CREATE TABLE IF NOT EXISTS transactions (
  id                 INTEGER PRIMARY KEY AUTOINCREMENT,
  element_key        TEXT NOT NULL UNIQUE,    -- <fileId>:<GS06>:<ST02>
  file_id            TEXT NOT NULL,
  source_name        TEXT NOT NULL,
  received_at        INTEGER NOT NULL,
  isa_control        TEXT,                    -- ISA13
  gs_control         TEXT,                    -- GS06
  st_control         TEXT,                    -- ST02
  gs08               TEXT NOT NULL,           -- implementation guide id, e.g. 005010X221A1
  transaction_type   TEXT NOT NULL,           -- display name: 835, 837P, 277CA, ...
  sender_id          TEXT,                    -- ISA06 (or GS02 per discriminator)
  receiver_id        TEXT,                    -- ISA08
  interchange_at     INTEGER,                 -- ISA09+ISA10 as epoch-millis
  schema_id          TEXT NOT NULL,           -- schema:table:x12.<GS08>.<TS>
  raw_x12            BLOB NOT NULL,           -- ST..SE verbatim (+ ISA/GS context lines)
  mapped_json        TEXT NOT NULL,           -- typed JSON per DESIGN §5
  parser_error_count INTEGER DEFAULT 0,
  envelope           TEXT NOT NULL DEFAULT 'file',   -- file | synthetic
  status             TEXT NOT NULL DEFAULT 'new',    -- new | in_flight | acked
  lease_id           TEXT,
  in_flight_until    INTEGER,
  acked_at           INTEGER
);

CREATE INDEX IF NOT EXISTS transactions_drain ON transactions(schema_id, status, received_at);
CREATE INDEX IF NOT EXISTS transactions_lease ON transactions(lease_id) WHERE lease_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS transactions_file ON transactions(file_id);

PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
