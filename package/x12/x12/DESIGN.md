# `@zerobias-org/module-x12-x12` — Design

**Status:** v1 design, 2026-09-22. Structural correlary: [`../../hl7/v2/DESIGN.md`](../../hl7/v2/DESIGN.md) —
read that first; this document states only what X12 changes and pins every decision an
implementer needs. Where this file is silent, hl7/v2 is the rule.

**One sentence:** an always-on DataProducer that polls one or more mounted directories for
X12 EDI interchange files, parses every transaction set with `com.imsweb:x12-parser`, persists
them to a durable SQLite buffer, renames consumed files `.done`, and exposes files, transactions
and typed JSON through the standard DataProducer surface with `take`/`ack` drain functions.

Decisions fixed by the product owner (David Reynolds, 2026-09-22): the module **parses** (like
hl7/v2, not transport-only) · **Java + imsweb x12-parser** to match hl7/v2 · **poll** semantics ·
consumed files marked with a **`.done` suffix** · one X12 file = one node with its own children.

---

## 1. Why this module is different from hl7/v2

| | hl7/v2 | x12/x12 |
|---|---|---|
| Ingress | MLLP sockets, pushed message by message | Files on a mounted volume, polled |
| Unit received | one HL7 message | one interchange **file** (ISA…IEA) holding N transaction sets |
| Atom (buffer row / collection element) | message (MSH-10) | **transaction set** (ST…SE) |
| Ack | `MSA\|AA` after SQLite commit | **rename `<file>` → `<file>.done` after SQLite commit** |
| Parser | HAPI generic + structure index | imsweb `X12Reader` (definition selected by GS08) + mapping index |
| Listener ports | required | **none** — `runtimeConfig.listenerPorts` absent |
| Raw access | `er7` function | `er7`-equivalent `raw` function **and** `downloadBinary` on the file node |
| Discriminators | by-type / by-version / by-sender / by-port | by-type / by-version / by-sender / **by-source** (watched dir) + **/files** |

Everything else — daemon mode, nginx→Javalin RPC envelope, `PagedResults` wrapper, error
envelope, lease/drain semantics, retention sweeper, classpath-served schemas generated at
build time, JUnit test surface, `zb.java-module` build — is inherited unchanged.

## 2. DataProducer surface

### 2.1 Object hierarchy

```
"/"                                   container (root, mandatory; id == name == "/")
└─ "/x12-receiver"                    container — the receiver instance
   ├─ /files                          container → one child per interchange file (emergent)
   │    └─ /files/<fileId>            ["container","binary"]  raw EDI downloadable
   │         └─ /files/<fileId>/transactions   collection — that file's ST..SE atoms (envelope schema)
   ├─ /transactions                   collection (heterogeneous, envelope schema) — everything
   ├─ /by-type                        container → /by-type/<TS>            collections, e.g. 835, 837P, 837I, 277CA, 999
   │                                   (version-interposed: /by-type/<TS>/<GS08> when a TS spans versions)
   ├─ /by-version                     container → /by-version/<GS08>       collections, e.g. 005010X221A1
   ├─ /by-sender                      container → /by-sender/<ISA06>       collections
   ├─ /by-source                      container → /by-source/<sourceName>  collections — one per watched dir
   ├─ /stats                          document — poller + buffer metrics
   └─ /ops                            container → take · ack · release · replay · recast · purge · raw · validate · rescan
```

Rules (from hl7/v2 `ObjectTree` javadoc, restated):
- **A transaction set is an atom** — a collection *element*, never a node. Element key =
  `<fileId>:<GS06>:<ST02>` (interchange file, group control number, transaction control number).
- **Folders are discriminators and their children are emergent**: read live from the buffer's
  `DISTINCT` values, so a node appears the first time matching data lands.
- **`/files/<fileId>` is the one exception**: a file is both a folder (its transactions) and a
  binary (its bytes). `fileId` = `<absolute path inside the container at discovery time>@<first
  12 hex of the sha256 of the bytes>` (the path is the one before the `.done` rename) — the same
  bytes re-landing at the same path share an id (a *redelivery*, counted), new bytes under a
  reused name get a new id, so a path is never a skip key. The raw path stays available as
  `filePath`. Binary fields: `fileName`, `filePath`, `size`, `mimeType: application/EDI-X12`,
  `checksum` (sha256 of the bytes, hex), `modified` (file mtime), `created` (discovery time),
  `tags: [source:<name>, status:<consumed|error|duplicate>]`, `redeliveryCount`.
- **Homogeneity**: a collection has exactly one `collectionSchema`. `/by-type/835` is itself
  the collection while every 835 in the buffer carries one GS08; it becomes a container with
  `/by-type/835/005010X221A1` leaves once it spans versions. Coarse facets (`/by-version`,
  `/by-sender`, `/by-source`, `/transactions`, `/files/<id>/transactions`) are heterogeneous →
  `schema:shared:x12.transaction-envelope`.
- `<TS>` display names: `835`, `837P` (X222), `837I` (X223), `837D` (X224), `277CA` (X214), `277`
  (X212), `999`, `834`, `820`, `270`, `271`, `276` — derived from GS08 via a fixed table in
  `TransactionTypes.java`; unknown GS08 → the bare `ST01` value.

### 2.2 Schema-ID namespace

Canonical form `schema:{type}:{catalog}.{schema}.{name}[:{direction}]`. Catalog token is always
**`x12`**; the schema slot is the **implementation guide id** (GS08, e.g. `005010X221A1`) for
guide-bound content, `codes` for the data-element code sets, `ops` for functions.

| Content | Schema id | Example |
|---|---|---|
| Transaction set (the collection schema) | `schema:table:x12.<GS08>.<TS>` | `schema:table:x12.005010X221A1.835` |
| Loop | `schema:type:x12.<GS08>.<loopXid>` | `schema:type:x12.005010X221A1.2100` |
| Segment | `schema:type:x12.<GS08>.<segXid>` | `schema:type:x12.005010X221A1.CLP` |
| Composite | `schema:type:x12.<GS08>.<compositeXid>` | `schema:type:x12.005010X221A1.C022` |
| Code set (per data element with `valid_codes` / `codes.xml`) | `schema:enum:x12.codes.<dataEle>` | `schema:enum:x12.codes.1029` (CLP02 claim status; 1032 is CLP06 filing indicator) |
| Transaction envelope (shared) | `schema:shared:x12.transaction-envelope` | — |
| File (binary node metadata) | `schema:shared:x12.file` | — |
| Receiver stats document | `schema:shared:x12.receiver-stats` | — |
| Functions | `schema:function:x12.ops.<fn>:input\|output` | `schema:function:x12.ops.take:input` |

These are module-internal addressing served by `getSchema` (hl7/v2 §2.2 scope note applies).

### 2.3 Composition

X12 is composition-all-the-way-down, like HL7: `835 → {header: HEADER, detail: 2000[], …}`,
`2000 → {lx: LX, 2100: 2100[]}`, `2100 → {clp: CLP, nm1: NM1[], svc: 2110[]}`, `CLP → {CLP01 …
CLP14}`. Every loop and segment is its own `schema:type`; repetition is `multi: true` on the
parent property; the transaction's `schema:table` has `primaryKey: true` on `elementKey`.
Property names on loops/segments are the **pyx12 `xid`s lower-camel-cased where they are words
and verbatim where they are codes**: loop `2100` → property `loop2100` (JSON key `2100` is
illegal as an identifier in consumers), segment `CLP` → `clp`, element `CLP01` → `clp01`, with
`name` from the mapping as the property `description`. The materialized JSON uses the **same**
keys — the generated schema is the contract (hl7/v2 trap #2).

### 2.4 Core dataType mapping (imsweb `dataele.xml` `data_type` → core)

| X12 type | Core `dataType` | Note |
|---|---|---|
| `AN` (alphanumeric) | `string` | trailing spaces trimmed at materialization |
| `ID` (identifier) | `string` + `references → schema:enum:x12.codes.<dataEle>` when a code list exists | enum holds the value set from `valid_codes`/`codes.xml` |
| `N0` … `N9` (implied decimal) | `decimal` | value / 10^n; **never float** — money |
| `R` (decimal) | `decimal` | |
| `DT` (`CCYYMMDD` or `YYMMDD`) | `date` | normalized to ISO `YYYY-MM-DD`; 2-digit years per ISA rule (`YYMMDD` → 20YY) |
| `TM` (`HHMM[SS[dd]]`) | `string` + `format: time` | no core time-only type; normalized `HH:MM[:SS[.dd]]` |
| `B` (binary) | `byte` | rare; base64 |
| composite (`C0nn`) | composition ref → `schema:type:x12.<GS08>.<compositeXid>` | sub-elements are its properties |
| repeated element (`^`) | parent property + `multi: true` | |

Materialization normalizes on the way in (like `Hl7Normalizer`): trimmed `AN`, decimal-shifted
`N*`, ISO dates, the ISA fixed-width padding stripped. Consumers never see wire formats.

### 2.5 Function objects

Inherited from hl7/v2 with the same I/O and constants (`DEFAULT_MAX=100`, `MAX_CAP=1000`,
`DEFAULT_TTL=PT5M`, partial `ack`, lease revert at TTL, `serializeNulls`):

| fn | input | output |
|---|---|---|
| `take` | `{filter?, max?, leaseTtl?}` | `{leaseId, transactions[], remaining}` |
| `ack` / `release` | `{leaseId, elementKeys?}` | `{acked\|released: n}` |
| `replay` | `{filter?}` | `{replayed: n}` |
| `recast` | `{filter?, max?}` | `{examined, recast, unchanged, failed}` — re-materialize from stored raw under current definitions |
| `purge` | `{olderThan?}` | `{purged: n}` — acked rows only |
| `raw` | `{elementKey}` | `{elementKey, fileId, gs08, transactionType, raw}` — the ST..SE segments verbatim, plus the ISA/GS lines for context |
| `validate` | `{elementKey}` | `{elementKey, schemaId, stored:{valid,errors[]}, rematerialized:{valid,errors[],schemaId}, repsAgree, parserErrors[], parserErrorCount}` — `rematerialized` re-parses the stored raw under the current definitions (`MaterializerRecastHook`); `parserErrors` are imsweb's non-fatal `getErrors()` from that re-parse |
| **`rescan`** (new) | `{source?}` | `{scanned, discovered, consumed, errored}` — trigger an immediate poll; the only way to force a pickup between intervals |

### 2.6 Filter semantics

RFC4515 over **schema property names**, translated to SQLite by an `X12SqlAdapter` cloned from
`Hl7SqlAdapter` (reflect public getters; switch on public enums; date extensions over epoch-millis;
`json_extract` for everything not an envelope column; `COLLATE NOCASE` string equality). Envelope
columns that resolve to real columns: `elementKey`, `fileId`, `sourceName`, `transactionType`,
`gs08`, `senderId`, `receiverId`, `receivedAt`, `status`, `leaseId`. Examples:

```
(&(transactionType=835)(senderId=ABCPAYER)(receivedAt>=2026-09-01)(status=new))
(|(transactionType=837P)(transactionType=837I))
(loop2100.clp.clp02=1)
(receivedAt:withinDays:1)
```

Same lite-filter constraints as hl7/v2 §2.6 (date-only literals; absolute-date `>=` is SQL-only).

### 2.7 Errors

RPC method names on the wire (`POST /connections/{id}/{ApiClass.method}`) use the interface's
operationIds: `ObjectsApi.getRootObject|getObject|getChildren|objectSearch|searchChildObjects`,
`CollectionsApi.getCollectionElements|getCollectionElement|searchCollectionElements`,
`DocumentsApi.getDocumentData`, `BinaryApi.downloadBinary`, `FunctionsApi.invokeFunction|
validateFunctionInput`, `SchemasApi.getSchema`. The router also accepts `BinaryApi.downloadBinaryContent`
and `DocumentsApi.getDocument` as aliases.

Identical to hl7/v2 §2.7: wire body is the OpenAPI `errorModelBase` (`{key, template, timestamp,
statusCode}` + subtype fields). 404 for unknown object/schema/lease/file, 400
`UnsupportedOperationError` for every write op (`createChildObject`, `addCollectionElement`,
`uploadBinaryContent`, `updateDocumentData`, …), 400 `illegalArgumentError` for bad filters.

### 2.8 Binary download

`downloadBinary(fileId)` streams the file bytes verbatim from the inbox (the `.done`-renamed
path is resolved from the `files` table, so download keeps working after consumption; after the
file is removed by inbox hygiene → 404 with `reason: gone`, the transactions remain). Range is not
in the generated signature (interface prose only); v1 serves 200 full-content.

## 3. Runtime shape

```
PID 1 ── startup.sh
        ├── nginx                    (0.0.0.0:8888 → 127.0.0.1:8889)
        └── java -jar x12-receiver.jar
              ├── Javalin HTTP on 8889          (DataProducer RPC ops)
              ├── InboxPoller thread per source (poll → parse → buffer → rename)
              ├── RetentionSweeper thread
              └── SQLite writer                  (single writer; readers on separate conns)
```

Env contract from Hub Node: `INTERNAL_PORT`, `MODULE_CONFIG` (JSON of `runtimeConfig.config`),
`RUNTIME_CONFIG_FILE` (optional), `HUB_NODE_INSECURE`, `JAVA_OPTS`. **No `LISTENER_PORT_*`.** When
`MODULE_CONFIG` is absent (bare `docker run`), the module parses the `config:` block of the
`runtimeConfig.yml` copied into the image — that is the dev/e2e fallback; production always gets
`MODULE_CONFIG`. Boot validates every `sources[].path`: must exist, be a directory, be writable
(rename test with a temp file); any failure → log + exit 1 (a daemon that cannot mark files
consumed must not run).

`runtimeConfig.yml` declares `daemonMode: true`, two `durability` mounts (`x12-buffer` →
`/var/lib/module`, `x12-inbox` → `/var/lib/x12/inbox`), `resources.memoryMb: 1024`, and the
opaque `config` (see the file). `connectionProfile.yml` exists for the publish pipeline and is
never read by the daemon.

## 4. Inbox poller

### 4.1 Configuration (`config.sources[]`)

`{name, path, pattern, pollIntervalSec, stableForSec}` per source. `name` is the provenance
label (`/by-source/<name>`, `sourceName` column). Several sources may share a buffer; names must
be distinct. `pattern` is a glob against the file name (case-insensitive).

### 4.2 Scan algorithm (per source, every `pollIntervalSec`)

1. List regular files matching `pattern`. **Skip** names ending in `.done`, `.error`, `.tmp`,
   `.part`, `.partial`, and dotfiles. **Nothing is skipped by path**: every stable candidate is
   hashed (candidates are only un-suffixed files, so this is cheap) and its identity decides
   what happens (step 3a). The one in-memory guard is per process: a file this process already
   consumed that is *still at its path* (its post-commit rename failed) is keyed by
   `(path, size, mtime)` and not re-hashed; the key is dropped as soon as the path leaves the
   listing, and nothing survives a restart. A file whose bytes cannot be read has no identity
   yet: it is left alone and retried next poll.
2. For each candidate, record `(size, mtime)`; a file is **stable** when the pair has been
   unchanged for ≥ `stableForSec` across polls (first sighting starts the clock). Unstable files
   are logged at debug and retried next poll.
3. Consume a stable file — one **transaction per file**:
   a. `sha256` the bytes → `checksum`; `fileId = <path>@<checksum[0..12)>`. Look the checksum
      up in `files` (a `consumed` row wins over a `duplicate` one, which wins over an `error`
      one). Known with status `consumed|duplicate` → **duplicate**: when a row with this very
      `fileId` exists (the same bytes re-landed at the same path — a *redelivery*) its `status`
      stays and `redelivery_count` is incremented; otherwise insert a `files` row with
      `status='duplicate'` (audit trail — the re-delivery is a fact worth recording; an `error`
      row that happens to carry this `fileId` is replaced by it). Either way insert NO
      transactions and rename `.done` (HL7 re-send rule: a duplicate is acknowledged, never
      re-ingested). Known only with status `error` → **retry**: if that error row has this
      `fileId` (same path, same bytes — an operator renamed the file back unchanged) it is
      deleted (`BufferStore.deleteFile`) and the file is consumed normally from 3b, so the
      row is replaced rather than duplicated; an `error` row for the same bytes at a *different*
      path is left as its own audit record. Unknown checksum → consume normally.
   b. Parse with imsweb: detect separators from ISA; read GS08 of the first GS; select
      `X12Reader.FileType` via `TransactionTypes.fileTypeFor(gs08)`; unknown GS08 → parse fails
      with `unsupported-guide`.
   c. For every `ST_LOOP` in every `GS_LOOP` of every `ISA_LOOP`: materialize (§5), build the
      envelope, `INSERT … ON CONFLICT(element_key) DO NOTHING` into `transactions`; insert the
      `files` row (`status='consumed'`, counts).
   d. `COMMIT`, **then** `rename(path, target)` where `target` is `<path><consumedSuffix>`, or
      `<path>.<discoveredAtEpochMillis><consumedSuffix>` when that name already exists (the same
      name delivered again with different bytes); the actual target is persisted in
      `files.current_path`. If the rename fails after commit, log at error and mark
      `files.rename_failed=1` (`current_path` = the discovery path) — the next scan re-hashes the
      file, finds the same `fileId` and treats it as a redelivery (3a), so the rows are never
      duplicated.
   e. On any parse failure before commit: `ROLLBACK`, `rename(path, target)` with the same
      collision rule and `errorSuffix`, insert a `files` row with `status='error'`,
      `error_message`, and imsweb `getFatalErrors()`. Errors are never retried automatically; an
      operator renames the file back (or fixes it) and the next scan picks it up: unchanged
      bytes replace the error row (3a retry) and, failing again, land back in `.error`; fixed
      bytes are a new file (new `fileId`) and the old error row stays as the audit record.
4. Backpressure: if the buffer is over `retention.maxBytes` and the sweeper cannot free space,
   the poller **leaves files untouched** (no rename, no insert) and reports `backpressure: true`
   in `/stats` and `/healthz` (503). Files are their own queue — strictly better than `MSA|AE`.

### 4.3 Envelope handling

- Files with ISA…IEA: normal. Files that start at `ST` (as the x12.org 837/277 examples do) are
  accepted when `config.allowBareTransactionSets: true` (default **false** in production, true in
  tests): the poller synthesizes an ISA/GS envelope from the ST03 / first-segment values and
  stamps `envelope: synthetic` on the rows.
- Multiple ISA in one file (rare, some clearinghouses concatenate): each `ISA_LOOP` is walked;
  `fileId` is shared, `isaControlNumber` distinguishes.

## 5. Materializer — imsweb Loop tree → typed JSON

- Walk the parsed `Loop` tree against the **mapping index** (generated at build time from the
  same `mapping/*.xml` imsweb ships — §6) rather than trusting `Loop.toJson()` (XStream output is
  shaped for XStream, not for consumers). For each loop: `{ "<segXid lower>": {...}, "loop<xid>":
  [...] }`; for each segment: `{ "<xid lower><nn>": value }` with §2.4 normalization; composites
  nest; repeated elements/segments/loops are arrays.
- Envelope overlay (authoritative, top level): `elementKey, fileId, fileName, sourceName,
  isaControlNumber, gsControlNumber, stControlNumber, gs08, transactionType, senderId (ISA06 or
  GS02 per discriminator), receiverId (ISA08), interchangeDate (ISA09+ISA10 → date-time),
  receivedAt, status, leaseId, envelope (file|synthetic), parserErrorCount`.
- Numeric typing is **on** in v1 (imsweb gives the `data_type`; hl7/v2 deferred it because HAPI
  did not) — `N*`/`R` become JSON numbers via `BigDecimal.toPlainString()` semantics.

## 6. Schema content — build-time generation

`java/codegen/` (build-time-only Maven project, mirrors hl7/v2's) reads
`com.imsweb:x12-parser`'s `mapping/<TS>.<ver>.<guide>.xml`, `dataele.xml`, `codes.xml`,
`x12.control.00501.xml` and emits:

```
java/src/main/resources/schemas/<GS08>/{transactions,loops,segments,composites}/*.json
java/src/main/resources/schemas/codes/*.json                   (enums)
java/src/main/resources/schemas/shared/*.json                  (envelope, file, receiver-stats)
java/src/main/resources/structure-index/<GS08>.json            (materializer index)
```

Guides in v1 (all present in imsweb 1.16): `005010X221A1` (835), `005010X222A1` (837P),
`005010X223A2` (837I; imsweb maps X223.A1 — emitted under the A2 id the wire carries, with A1 as
alias), `005010X214` (277CA), `005010X212` (277), `005010X231A1` (999), `005010X220A1` (834),
`005010X218` (820). **Not in v1**: 270/271 at 005010 (imsweb has only 4010 X092) and 837D X224 —
recorded as gaps; a custom mapping XML (schema `map.v2.xsd`) adds them later.

Output is **git-ignored and regenerated on every build** (`generate-resources`, never a profile);
runtime serves from the classpath. The pyx12 mapping files are BSD-licensed (John Holland) and
already inside the imsweb jar — the codegen reads them from the classpath, nothing is vendored.

## 7. Extensions

Not in v1. Trading-partner companion-guide deviations are handled by lenient parsing + the
`parserErrorCount` envelope field; a pack mechanism can follow hl7/v2 §7 if needed.

## 8. Buffer

Same engine as hl7/v2 (SQLite, WAL, epoch-millis, single writer, lease manager, sweeper). DDL:

```sql
CREATE TABLE IF NOT EXISTS files (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  file_id TEXT NOT NULL UNIQUE,        -- <file_path>@<first 12 hex of checksum>
  file_path TEXT NOT NULL,             -- absolute path at discovery (before the rename)
  file_name TEXT NOT NULL, source_name TEXT NOT NULL,
  current_path TEXT NOT NULL,          -- the actual rename target (.done/.error, possibly timestamped)
  size_bytes INTEGER NOT NULL, checksum TEXT NOT NULL, file_mtime INTEGER NOT NULL,
  discovered_at INTEGER NOT NULL, consumed_at INTEGER,
  status TEXT NOT NULL,                -- consumed | error | duplicate
  isa_count INTEGER, transaction_count INTEGER, error_message TEXT, rename_failed INTEGER DEFAULT 0,
  redelivery_count INTEGER DEFAULT 0   -- same bytes re-landed at the same path
);
CREATE INDEX IF NOT EXISTS files_checksum ON files(checksum);
CREATE INDEX IF NOT EXISTS files_path ON files(file_path);
CREATE INDEX IF NOT EXISTS files_source ON files(source_name, status);
CREATE TABLE IF NOT EXISTS transactions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  element_key TEXT NOT NULL UNIQUE,    -- <fileId>:<GS06>:<ST02>  (fileId = <path>@<hash12>)
  file_id TEXT NOT NULL, source_name TEXT NOT NULL,
  received_at INTEGER NOT NULL,
  isa_control TEXT, gs_control TEXT, st_control TEXT,
  gs08 TEXT NOT NULL, transaction_type TEXT NOT NULL,
  sender_id TEXT, receiver_id TEXT, interchange_at INTEGER,
  schema_id TEXT NOT NULL,
  raw_x12 BLOB NOT NULL,               -- ST..SE segments verbatim (+ ISA/GS context lines)
  mapped_json TEXT NOT NULL,
  parser_error_count INTEGER DEFAULT 0, envelope TEXT NOT NULL DEFAULT 'file',
  status TEXT NOT NULL DEFAULT 'new', lease_id TEXT, in_flight_until INTEGER, acked_at INTEGER
);
CREATE INDEX IF NOT EXISTS transactions_drain ON transactions(schema_id, status, received_at);
CREATE INDEX IF NOT EXISTS transactions_lease ON transactions(lease_id) WHERE lease_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS transactions_file ON transactions(file_id);
PRAGMA journal_mode = WAL;
```

Durability (`ackDurability`), drain/lease SQL, retention sweeper (acked rows only; `files` rows
are never evicted — they are the audit trail), and backpressure are as in hl7/v2 §8.

## 9. Health

`/healthz` → `{poller: {up, lastScan, lastConsumed, bufferDepth, oldestUnackedSec, sources[]:
{name, path, writable, pending, errored}}, db: {walBytes, lastCheckpoint}}`; 503 when any
source is unwritable, when the poller thread is dead, or under backpressure.

## 10. Out of scope (v1)

Outbound X12 (997/999 generation back to the sender) · TA1 interchange acks · trading-partner
extension packs · routing/transformation into AuditgraphDB (that is the collectorbot) · TLS on
the ops port beyond the self-signed default · HA / multi-instance.

## 11. Open questions

1. **Range downloads** — the generated `downloadBinary` has no Range argument; large 835 batches
   may want it. Platform question (interface), not module.
2. **Inbox hygiene** — who deletes `.done` files? Not the module (audit trail). Operator cron or
   the feed. `/stats` reports `.done` count and age so it is visible.
3. **`MODULE_CONFIG` size** — many sources/partners could grow it; hl7/v2 flagged the same.
4. **Shared SQL filter adapter** — second copy of `Hl7SqlAdapter`; promote into `lite-filter`.
5. **Gate stamp does not hash `java/`** (`zb.java-module` gap) — set `project.extra["sourceDirs"]`
   in this module's `build.gradle.kts` to include `java/src` so Java changes invalidate the stamp.

## 12. Implementation order

1. Foundation (port of hl7/v2 minus HAPI): `pom.xml`, config, buffer, lease, sweeper, filter
   adapter, RPC server + router + facade skeleton, health.
2. Codegen from imsweb mappings → schemas + structure index; `SchemaRegistry`.
3. Poller + parser + materializer; `files`/`transactions` write path; `.done`/`.error`.
4. `ObjectTree`, `X12Operations`, `X12ProducerFacade` (tree, collections, functions, download).
5. Tests: JUnit unit (`*Test`) + integration (`*IT`) on synthetic fixtures; `fetch-x12org-examples.py`
   conformance run (local only); `e2e-local.sh` (real container, real file drop, take/ack/purge).
6. `zbb gate` → `gate-stamp.json` → PR to `dev`.

## 13. Test fixtures and the x12.org examples

x12.org's examples are ASC X12 intellectual property — reproduction requires their consent;
linking is permitted (`https://x12.org/examples/disclaimers`). Therefore:
- `java/scripts/fetch-x12org-examples.py` downloads the 44 HIPAA 005010 leaf example pages
  (835/837P/837I/277CA/999/270-271/276-277), extracts the EDI from `<p class="data">`
  (strip `<wbr>`, join `<br>`, unescape, split on `~`), wraps envelope-less 837/277 examples in a
  synthetic ISA/GS…GE/IEA, and writes `java/src/test/resources/x12org/<guide>/<example>.x12` —
  a **git-ignored** directory. The conformance IT (`X12OrgConformanceIT`) parses every file
  present and asserts a full tree with zero fatal errors; it **skips** when the directory is
  absent, so CI stays green without the files.
- Committed fixtures under `java/src/test/resources/fixtures/` are **synthetic** (fictional
  payer/provider, `TEST-NET` style identifiers), one per guide, authored by the test agent, plus
  deliberately malformed files for the `.error` path.
