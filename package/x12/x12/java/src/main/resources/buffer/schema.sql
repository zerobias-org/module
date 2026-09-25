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
  -- No typed-document column: the content lives as the object graph below and is reassembled
  -- on demand (DESIGN §8.4), so there is exactly one representation and nothing to keep in sync.
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
-- Acked rows are most of the table (retention keeps them for maxAge), so every hot path must
-- reach its rows without walking them. Additive only: CREATE INDEX IF NOT EXISTS builds these
-- on an existing buffer at the next open, so they need no BufferStore.migrate step.
-- oldestUnacked (health, /stats): the un-acked rows, oldest first. A partial index is only used
-- when the query carries its WHERE term verbatim (BufferStore.oldestUnackedSeconds does).
CREATE INDEX IF NOT EXISTS transactions_unacked ON transactions(received_at) WHERE status <> 'acked';
-- purge + retention (maxAge range, maxBytes oldest-acked-first); also take's status probes.
CREATE INDEX IF NOT EXISTS transactions_acked ON transactions(status, acked_at);
-- The emergent object tree (/by-type, /by-version, /by-sender, /by-source): its children are the
-- DISTINCT values of these columns, read from a narrow index in order instead of a table scan
-- plus a temp sort. (type, gs08) also serves the per-type guide list /by-type/<TS>.
CREATE INDEX IF NOT EXISTS transactions_type ON transactions(transaction_type, gs08);
CREATE INDEX IF NOT EXISTS transactions_gs08 ON transactions(gs08);
CREATE INDEX IF NOT EXISTS transactions_sender ON transactions(sender_id);
CREATE INDEX IF NOT EXISTS transactions_source ON transactions(source_name);

-- ---------------------------------------------------------------------------
-- The object graph (DESIGN §8.4). One row per materialized LOOP / SEGMENT /
-- COMPOSITE instance, keyed by the schema that describes it — the same
-- schema:type:x12.<GS08>.<xid> ids the packs emit and getSchema serves. This is
-- what makes the DataProducer surface queryable: a transaction set is a graph of
-- addressable objects, not a JSON blob with an id.
--
-- parent_id is the edge. The root instance of a transaction set carries
-- parent_id IS NULL and the table schema id, so a whole transaction reassembles
-- by walking down from it, and any sub-object (a claim, a service line) is a
-- collection element in its own right.
CREATE TABLE IF NOT EXISTS entities (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  element_key  TEXT NOT NULL,            -- owning transaction set (<fileId>:<ISA13>:<GS06>:<ST02>)
  file_id      TEXT NOT NULL,            -- denormalized for file-scoped queries
  gs08         TEXT NOT NULL,            -- guide, so /by-type collections scope without a join
  schema_id    TEXT NOT NULL,            -- schema:type:x12.<GS08>.<xid> | schema:table:... at the root
  xid          TEXT NOT NULL,            -- loop/segment/composite id: 2100, CLP, C022
  kind         TEXT NOT NULL,            -- loop | segment | composite
  parent_id    INTEGER,                  -- NULL at the transaction root
  property     TEXT,                     -- the property name under the parent (loop2100, clp, ...)
  path         TEXT NOT NULL,            -- dotted instance path, e.g. detail[0].loop2000[1].clp
  ordinal      INTEGER NOT NULL DEFAULT 0,  -- position within a repeating property
  -- The instance's own property order as materialized, unit-separator joined. Field order
  -- is the wire order (DESIGN §5) and a composite is a child row rather than a value, so
  -- without this a reassembled segment would list every scalar before every composite.
  property_order TEXT
);

CREATE INDEX IF NOT EXISTS entities_tx ON entities(element_key);
CREATE INDEX IF NOT EXISTS entities_schema ON entities(gs08, schema_id);
CREATE INDEX IF NOT EXISTS entities_parent ON entities(parent_id);
CREATE INDEX IF NOT EXISTS entities_file ON entities(file_id);

-- One row per scalar field of an instance, typed by the schema's core dataType so
-- filters compare like with like: money in value_num (never a string compare),
-- dates in value_date as epoch-millis, everything else in value_text. This is the
-- table an RFC4515 filter compiles against, which is why (property, value_*) are
-- indexed rather than the row id.
CREATE TABLE IF NOT EXISTS entity_values (
  entity_id    INTEGER NOT NULL,
  seq          INTEGER NOT NULL,         -- emission order (WITHOUT ROWID has no rowid to order by)
  property     TEXT NOT NULL,            -- clp04, bpr02, nm103, ...
  data_type    TEXT NOT NULL,            -- string | integer | decimal | boolean | date | date-time
  value_text   TEXT,
  -- COMPARISON KEY ONLY, never the value: the amount scaled to integer micro-units
  -- (x10^6, half-up). Money must not round-trip through a float (CLAUDE.md), so the exact
  -- lexical form stays in value_text and reassembly reads THAT; value_num exists so range
  -- filters and ORDER BY are exact integer comparisons. NULL when the value does not fit.
  value_num    INTEGER,
  value_date   INTEGER,                  -- epoch-millis for date / date-time
  PRIMARY KEY (entity_id, property)
) WITHOUT ROWID;

-- Transaction-level business dimensions (DESIGN §8.5), resolved once per transaction set from
-- the guide's mapping. "Claims for this payer" has to be an indexed equality: the payer lives in
-- N1*PR up in the header, so without this every claim row would walk up the graph to find it.
CREATE TABLE IF NOT EXISTS transaction_dims (
  element_key  TEXT NOT NULL,
  dim          TEXT NOT NULL,            -- payerName, payeeNpi, checkOrEftNumber, ...
  data_type    TEXT NOT NULL,
  value_text   TEXT,
  value_num    INTEGER,                  -- micro-units, comparison key only (see entity_values)
  value_date   INTEGER,
  PRIMARY KEY (element_key, dim)
) WITHOUT ROWID;

CREATE INDEX IF NOT EXISTS transaction_dims_lookup ON transaction_dims(dim, value_text);

CREATE INDEX IF NOT EXISTS entity_values_num ON entity_values(property, value_num) WHERE value_num IS NOT NULL;
CREATE INDEX IF NOT EXISTS entity_values_text ON entity_values(property, value_text) WHERE value_text IS NOT NULL;
CREATE INDEX IF NOT EXISTS entity_values_date ON entity_values(property, value_date) WHERE value_date IS NOT NULL;

PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
