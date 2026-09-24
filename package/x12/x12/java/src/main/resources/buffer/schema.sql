-- Durable buffer schema (DESIGN §8). One SQLite file at /var/lib/module/buffer.db.
-- WAL mode handles concurrent reader/writer; the single Java process owns one
-- writer thread. Timestamps are epoch-millis INTEGERs (see BufferStore).
--
-- synchronous is NORMAL here (fsync at WAL checkpoints) but BufferStore overrides it at
-- open from config.ackDurability: full (the default) -> synchronous=FULL, fsync per commit,
-- which the rename-is-the-ack guarantee needs; normal trades that for throughput.

-- One row per interchange FILE discovered in an inbox. Rows are never evicted by
-- retention — they are the audit trail, and the checksum index is what keeps a
-- re-dropped file from being consumed twice (DESIGN §4.2).
CREATE TABLE IF NOT EXISTS files (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  file_id           TEXT NOT NULL UNIQUE,     -- <file_path>@<first 12 hex of checksum> (DESIGN §2.1)
  file_path         TEXT NOT NULL,            -- absolute path at discovery (before the .done rename)
  file_name         TEXT NOT NULL,
  source_name       TEXT NOT NULL,            -- config.sources[].name (provenance)
  current_path      TEXT NOT NULL,            -- after rename (.done/.error); == file_path when the rename failed
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
  element_key        TEXT NOT NULL UNIQUE,    -- <fileId>:<ISA13>:<GS06>:<ST02>
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
-- Acked rows are most of the table (retention keeps them for maxAge), so every hot path
-- must reach its rows without walking them.
-- take + backlog + oldestUnacked: the un-acked rows, oldest first. A partial index is only
-- used when the query repeats its WHERE term verbatim (LeaseManager / BufferStore do).
CREATE INDEX IF NOT EXISTS transactions_unacked ON transactions(received_at) WHERE status <> 'acked';
-- purge + retention (maxAge range, maxBytes oldest-acked-first) and the per-status counts.
CREATE INDEX IF NOT EXISTS transactions_acked ON transactions(status, acked_at);
-- newest-first browse (/transactions, search) without a sort over the whole table.
CREATE INDEX IF NOT EXISTS transactions_received ON transactions(received_at);
-- The emergent object tree (/by-type, /by-version, /by-sender, /by-source): its children are
-- the distinct values of these columns and each child's size a count over one value, so both
-- read a narrow covering index in order instead of every row plus a temp sort. (type, gs08)
-- also serves the per-type guide list /by-type/<TS> and the (type, guide) collections.
CREATE INDEX IF NOT EXISTS transactions_type ON transactions(transaction_type, gs08);
CREATE INDEX IF NOT EXISTS transactions_gs08 ON transactions(gs08);
CREATE INDEX IF NOT EXISTS transactions_sender ON transactions(sender_id);
CREATE INDEX IF NOT EXISTS transactions_source ON transactions(source_name);

PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
