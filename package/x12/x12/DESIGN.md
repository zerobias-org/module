# `@zerobias-org/module-x12-x12` — Design

**Status:** v1. Structural corollary: [`../../hl7/v2/DESIGN.md`](../../hl7/v2/DESIGN.md) —
read that first; this document states only what X12 changes and pins every decision an
implementer needs. Where this file is silent, hl7/v2 is the rule.

**One sentence:** an always-on DataProducer that polls one or more mounted directories for
X12 EDI interchange files, parses every transaction set with `com.imsweb:x12-parser`, persists
them to a durable SQLite buffer, renames consumed files `.done`, and exposes files, transactions
and typed JSON through the standard DataProducer surface with `take`/`ack` drain functions.

Fixed decisions: the module **parses** (like hl7/v2, not transport-only) · **Java + imsweb
x12-parser** to match hl7/v2 · **poll** semantics · consumed files marked with a **`.done`
suffix** · one X12 file = one node with its own children.

---

## 1. Why this module is different from hl7/v2

| | hl7/v2 | x12/x12 |
|---|---|---|
| Ingress | MLLP sockets, pushed message by message | Files on a mounted volume, polled |
| Unit received | one HL7 message | one interchange **file** (ISA…IEA, possibly several) holding N transaction sets |
| Atom (buffer row / collection element) | message (MSH-10) | **transaction set** (ST…SE) |
| Ack | `MSA\|AA` after SQLite commit | **rename `<file>` → `<file>.done` after SQLite commit** |
| Parser | HAPI generic + structure index | imsweb `X12Reader`, one definition per functional group selected by its GS08, + structure index |
| Listener ports | required | **none** — `runtimeConfig.listenerPorts` absent |
| Raw access | `er7` function | `raw` function **and** `downloadBinary` on the file node |
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
   │    └─ /files/<fileId>            ["container","document","binary"]  raw EDI downloadable
   │         └─ /files/<fileId>/transactions   collection — that file's ST..SE atoms (envelope schema)
   ├─ /transactions                   collection (heterogeneous, envelope schema) — everything
   ├─ /by-type                        container → /by-type/<TS>          containers, e.g. 835, 837P, 837I, 277CA, 999
   │                                   └─ /by-type/<TS>/<GS08>           collections, e.g. /by-type/835/005010X221A1
   ├─ /by-version                     container → /by-version/<GS08>     collections, e.g. 005010X221A1
   ├─ /by-sender                      container → /by-sender/<ISA06>     collections
   ├─ /by-source                      container → /by-source/<sourceName> collections — one per watched dir
   ├─ /stats                          document — poller + buffer metrics (schema:shared:x12.receiver-stats)
   └─ /ops                            container → take · ack · release · replay · recast · purge · raw · validate · rescan
```

Rules (from hl7/v2 `ObjectTree` javadoc, restated):
- **A transaction set is an atom** — a collection *element*, never a node. Element key =
  `<fileId>:<ISA13>:<GS06>:<ST02>` (interchange file, interchange control number, group
  control number, transaction set control number). ISA13 is part of the key because one file
  may carry several interchanges and GS06/ST02 are only unique within one — senders routinely
  restart them at `1`/`0001`.
- **Folders are discriminators and their children are emergent**: read live from the buffer's
  `DISTINCT` values, so a node appears the first time matching data lands.
- **`/files/<fileId>` is the one exception**: a file is a folder (its transactions), a
  document (its `files` row, `schema:shared:x12.file`) and a binary (its bytes). `fileId` =
  `<absolute path inside the container at discovery time>@<first 12 hex of the sha256 of the
  bytes>` (the path is the one before the `.done` rename) — the same bytes re-landing at the
  same path share an id (a *redelivery*, counted), new bytes under a reused name get a new id,
  so a path is never a skip key. The node carries the interface binary fields: `fileName`,
  `size`, `mimeType: application/EDI-X12`, `checksum` (sha256 of the bytes, hex), `modified`
  (file mtime), `created` (discovery time), `tags: [source:<name>, status:<consumed|error|duplicate>]`.
  Everything else (`fileId`, `filePath`, `currentPath`, `status`, `consumedAt`, `isaCount`,
  `transactionCount`, `errorMessage`, `renameFailed`, `redeliveryCount`) is its document, read
  with `getDocumentData`. `/files` lists newest discovery first and pages in SQL — the `files`
  table is the never-evicted audit trail.
- **Id encoding**: a `fileId` contains `/`, and a sender id or source name may too, so every
  discriminator value is embedded in an object id with `/` → `%2F` and `%` → `%25` (nothing
  else is encoded). Ids returned by the tree round-trip verbatim through every operation.
- **Homogeneity**: a collection has exactly one `collectionSchema`. `/by-type/<TS>` is **always**
  a container of per-guide collections `/by-type/<TS>/<GS08>`, even while a type has arrived under
  one GS08 only — an id never changes class when a second guide lands. Each such collection
  carries `schema:table:x12.<GS08>.<TS>` (the envelope schema if that table is not bundled).
  Coarse facets (`/by-version`, `/by-sender`, `/by-source`, `/transactions`,
  `/files/<id>/transactions`) are heterogeneous → `schema:shared:x12.transaction-envelope`.
- `<TS>` display names come from the guide table (§6): `835`, `837P`, `837I`, `277CA`, `277`,
  `999`, `834`. A GS08 outside the table never reaches the buffer (the file goes to `.error` as
  `unsupported-guide`, §4.2).

### 2.2 Schema-ID namespace

Canonical form `schema:{type}:{catalog}.{schema}.{name}[:{direction}]`. Catalog token is always
**`x12`**; the schema slot is the **implementation guide id** (GS08, e.g. `005010X221A1`) for
guide-bound content, `codes` for the data-element code sets, `ops` for receiver-owned enums and
functions.

| Content | Schema id | Example |
|---|---|---|
| Transaction set (the collection schema) | `schema:table:x12.<GS08>.<TS>` | `schema:table:x12.005010X221A1.835` |
| Loop | `schema:type:x12.<GS08>.<loopXid>` | `schema:type:x12.005010X221A1.2100` |
| Segment | `schema:type:x12.<GS08>.<segXid>` | `schema:type:x12.005010X221A1.CLP` |
| Composite | `schema:type:x12.<GS08>.<compositeXid>` | `schema:type:x12.005010X221A1.C003` |
| Code set coded inline (`valid_codes`) — per data element | `schema:enum:x12.codes.<dataEle>` | `schema:enum:x12.codes.1029` (CLP02 claim status; 1032 is CLP06 filing indicator) |
| Code set from imsweb `codes.xml` — per codeset | `schema:enum:x12.codes.<codeset>` | `claim_status_cat`, `claim_status`, `adjustment_reason`, `remark_code`, `states`, `country`, `currency`, `pos` |
| Receiver enum | `schema:enum:x12.ops.<Name>` | `TransactionStatus`, `EnvelopeOrigin`, `FileStatus` |
| Transaction envelope (shared) | `schema:shared:x12.transaction-envelope` | — |
| File (file node document) | `schema:shared:x12.file` | — |
| Receiver stats document | `schema:shared:x12.receiver-stats` (+ `.receiver-stats-source`) | — |
| Function error / verdict shapes | `schema:shared:x12.not-found-error`, `schema:shared:x12.ops-verdict` | — |
| Functions | `schema:function:x12.ops.<fn>:input\|output` | `schema:function:x12.ops.take:input` |

A position whose uses all draw on one `codes.xml` codeset references that codeset (so 277CA
STC01-01 `claim_status_cat` and STC01-02 `claim_status`, both data element 1271, stay two
enums); a position coded inline, or drawing on mixed sources, references its data element.
These are module-internal addressing served by `getSchema` (hl7/v2 §2.2 scope note applies).

### 2.3 Composition

X12 is composition-all-the-way-down, like HL7: `835 → {st, header: HEADER, detail: DETAIL[],
footer: FOOTER, se}`, `2100 → {clp: CLP, cas: CAS[], nm1: NM1[], …, loop2110: 2110[]}`,
`CLP → {clp01 … clp14}`. Every loop and segment is its own `schema:type`; repetition is
`multi: true` on the parent property; the transaction's `schema:table` has `primaryKey: true` on
`elementKey`. Property names on loops/segments are the **pyx12 `xid`s lower-camel-cased where
they are words and verbatim where they are codes**: loop `2100` → property `loop2100` (JSON key
`2100` is illegal as an identifier in consumers), segment `CLP` → `clp`, element `CLP01` →
`clp01`, with `name` from the mapping as the property `description`. The materialized JSON uses
the **same** keys — the generated schema is the contract (hl7/v2 trap #2).

### 2.4 Core dataType mapping (imsweb `dataele.xml` `data_type` → core)

| X12 type | Core `dataType` | Note |
|---|---|---|
| `AN` (alphanumeric) | `string` | trimmed at materialization |
| `ID` (identifier) | `string` + `references → schema:enum:x12.codes.<key>` when a code list exists | enum holds the value set (§2.2) |
| `N0` … `N9` (implied decimal) | `decimal` | value / 10^n; **never float** — money |
| `R` (decimal) | `decimal` | |
| control numbers (data elements `I12`, `28`, `329`: ISA13, GS06, ST02, 999 AK102/AK202) | `string` | `N0` on the wire, but leading zeros are part of the identity (`000000101`) |
| `DT` (`CCYYMMDD` or `YYMMDD`) | `date` | ISO `YYYY-MM-DD`; `YYMMDD` → `20YY` |
| `TM` (`HHMM[SS[d[d]]]`) | `string` + `format: time` | no core time-only type; normalized `HH:MM:SS[.d[d]]` (seconds default `00`) |
| Date Time Period (1251), by its format qualifier (1250) | `string` | `D8` → `YYYY-MM-DD`, `RD8` → ISO interval `YYYY-MM-DD/YYYY-MM-DD`, `DT` → `YYYY-MM-DDTHH:MM:SS`; any other qualifier as sent |
| `B` (binary) | `byte` | rare; unchanged |
| composite (`C0nn`) | composition ref → `schema:type:x12.<GS08>.<compositeXid>` | sub-elements are its properties |
| repeated element (`^`) | parent property + `multi: true` | split with the file's repetition separator |

Materialization normalizes on the way in (like `Hl7Normalizer`): trimmed `AN`, decimal-shifted
`N*`, ISO dates and times, the ISA fixed-width padding stripped. A value that does not fit its
declared type — including a date or time that does not exist — is kept trimmed but unchanged,
never dropped or coerced.

### 2.5 Function objects

Inherited from hl7/v2 with the same I/O and constants (`DEFAULT_MAX=100`, `MAX_CAP=1000`,
`DEFAULT_TTL=PT5M` capped at `PT1H`, partial `ack`, lease revert at TTL, `serializeNulls`):

| fn | input | output |
|---|---|---|
| `take` | `{filter?, max?, leaseTtl?}` | `{leaseId, transactions[], remaining}` — `leaseId` is null when nothing was drainable; `remaining` is the approximate drainable backlog |
| `ack` / `release` | `{leaseId, elementKeys?}` | `{acked\|released: n}` — `elementKeys`, when given, is a non-empty subset of the lease |
| `replay` | `{filter?}` | `{replayed: n}` — force `in_flight` rows back to `new` |
| `recast` | `{filter?, max?}` | `{examined, recast, unchanged, failed}` — re-materialize from stored raw under current definitions; `in_flight` rows are skipped; `max` defaults to and is capped at 1000 |
| `purge` | `{olderThan?}` | `{purged: n}` — acked rows only; omitted = every acked row |
| `raw` | `{elementKey}` | `{elementKey, fileId, gs08, transactionType, raw}` — the ST..SE segments verbatim, plus the ISA/GS lines for context |
| `validate` | `{elementKey}` | `{elementKey, schemaId, stored:{valid,errors[]}, rematerialized:{valid,errors[],schemaId}, repsAgree, parserErrors[], parserErrorCount}` — `rematerialized` re-parses the stored raw under the current definitions (`MaterializerRecastHook`); its `schemaId` is always present, null when the re-parse failed; `parserErrors` are imsweb's non-fatal errors from that re-parse |
| **`rescan`** (new) | `{source?}` | `{scanned, discovered, consumed, errored}` — trigger an immediate poll of one source (by name) or all; the only way to force a pickup between intervals |

**Input is schema-checked before anything runs.** Every function declares its input schema
(`schema:function:x12.ops.<fn>:input`), and `invokeFunction` enforces it: an unknown key, a
wrong type, a missing required property, an unparseable filter or duration, `max < 1` or an
empty `elementKeys` is a 400 `illegalArgumentError` and nothing runs — `purge {"olderthan":
"P30D"}` must not purge every acked row. `validateFunctionInput` (`{input, strict?}`) runs the
same check without executing and returns `{valid, errors[{path,message,code}],
warnings[{path,message}]}`; a `max` above 1000 or a `leaseTtl` above `PT1H` runs capped and is a
warning (`capped`), and `strict` turns warnings into errors.

**Not found.** `ack`, `release`, `raw`, `validate` and `rescan` declare
`throws: {not_found: schema:shared:x12.not-found-error}`: an unknown `elementKey`, an unknown
source name, or a lease no `in_flight` row carries (unknown, already finalized, or expired and
re-leased — the buffer forgets a lease once its rows leave `in_flight`, so the three are
indistinguishable) is a 404. An `ack`/`release` subset naming keys outside a live lease is not
an error; it reports the count it touched.

### 2.6 Filter semantics

RFC4515 over **schema property names**, translated to SQLite by an `X12SqlAdapter` cloned from
`Hl7SqlAdapter` (reflect public getters; switch on public enums; date extensions over epoch-millis;
`json_extract` for everything not an envelope column; `COLLATE NOCASE` string equality). Envelope
properties that resolve to real columns: `elementKey`, `fileId`, `sourceName`, `transactionType`,
`gs08`, `senderId`, `receiverId`, `receivedAt`, `status`, `leaseId`, `isaControlNumber`,
`gsControlNumber`, `stControlNumber`, `interchangeDate`, `envelope`, `parserErrorCount`,
`schemaId`. Examples:

```
(&(transactionType=835)(senderId=ABCPAYER)(receivedAt>=2026-09-01)(status=new))
(|(transactionType=837P)(transactionType=837I))
(loop2100.clp.clp02=1)
(receivedAt:withinDays:1)
```

`getCollectionElements` honours `filter` exactly like `searchCollectionElements` (the hl7/v2
contract callers already use). Same lite-filter constraints as hl7/v2 §2.6 (date-only literals;
absolute-date `>=` is SQL-only).

### 2.7 RPC names, parameters and errors

RPC method names on the wire (`POST /connections/{id}/{ApiClass.method}`) are the interface's
operationIds, matched **exactly** — there are no aliases:
`ObjectsApi.getRootObject|getObject|getChildren|searchChildObjects`,
`CollectionsApi.getCollectionElements|searchCollectionElements|getCollectionElement`,
`DocumentsApi.getDocumentData`, `BinaryApi.downloadBinary`,
`FunctionsApi.invokeFunction|validateFunctionInput`, `SchemasApi.getSchema`. Any other name is
400 `UnsupportedOperationError`. `objectSearch` (`/objects/{objectId}/search`) is deliberately
not referenced in `api.yml`: a keyword or filter search across a subtree would enumerate the
never-evicted `/files` audit trail in memory.

Every parameter an operation declares is either honoured or, when set, rejected with 400
`UnsupportedOperationError` — never silently dropped:

| Operation | Honoured | Rejected when set |
|---|---|---|
| `getChildren` | `objectId`, `pageNumber`, `pageSize` | `sortBy`, `sortDir`, `type`, `tags`, `pageToken` |
| `searchChildObjects` | `objectId`, `scope` (`one_level` only; `subtree` → 400), `includeCount`, `pageNumber`, `pageSize` | `sortBy`, `sortDir`, `filter`, `pageToken`, `properties` |
| `getCollectionElements` / `searchCollectionElements` | `objectId`, `filter`, `pageNumber`, `pageSize` | `sortBy`, `sortDir`, `pageToken`, `properties` |

Paging is by offset in a fixed newest-first order: `pageNumber ≥ 1`, `1 ≤ pageSize ≤ 1000`
(default 100); every paginated op returns `PagedResults {items, count, pageSize, pageNumber}`.

Errors, identical to hl7/v2 §2.7: the wire body is the OpenAPI `errorModelBase`
(`{key, template, timestamp, statusCode}` + subtype fields). 404 `err.no.such.object` with
`{type, id}` for an unknown object / schema / lease / file (`reason: gone` when a file's bytes
cannot be served, §2.8); 400 `err.unsupported.operation` for every write op
(`createChildObject`, `addCollectionElement`, `updateDocumentData`, …), every unsupported
parameter and an operation on the wrong kind of object; 400 `err.illegal.argument` for a
malformed filter, function input or page bound; 500 `err.unexpected` with a fixed generic
message — the cause (an SQLite or IO message that can name paths inside the container) is
logged, never returned.

### 2.8 Binary download

`BinaryApi.downloadBinary(objectId)` on a `/files/<fileId>` node streams the file bytes
verbatim from `files.current_path`, so download keeps working after the `.done` rename:
`200`, `Content-Type: application/EDI-X12`, `Content-Length`, and `Content-Disposition:
attachment` with an ASCII `filename` and the exact name as RFC 5987 `filename*`. The recorded
path is served only when it is a regular file directly inside the file's configured source
directory; it is opened without following symlinks. Anything else — the file removed by inbox
hygiene, a source since dropped from config, a symlink planted at the recorded name — is 404
with `reason: gone`; the transactions remain. Range is not in the generated signature
(interface prose only); v1 always serves full content.

### 2.9 Live inbox browse and file management

`/files` is a projection of the SQLite `files` table: it shows what has been **consumed**.
That makes it useless for driving or observing the volume itself — a file that just landed
appears nowhere until the poller has ingested it. `/inbox` closes that gap: it is the
**live filesystem**, one child per configured source, then real directories and real files
beneath.

**Nothing in this branch is cached.** Every `getChildren` is a fresh readdir and every
`getObject` a fresh `stat`, so an uploaded file is visible immediately and a file removed
behind the module's back vanishes on the next call. There is no invalidation step, because
there is nothing to invalidate. Dotfiles are hidden, exactly as the poller skips them.

Each live file node carries an `ingest` field stating what the poller will do with it,
computed per call from config — never guessed by the caller:

| `ingest` | meaning |
|---|---|
| `watched` | in a source root, matches the source `pattern` → will be consumed on the next scan |
| `ignored:pattern` | in a source root, but the name does not match `pattern` |
| `ignored:suffix` | already carries `consumedSuffix`/`errorSuffix` |
| `ignored:subdirectory` | under a subdirectory — **the poller scans each source flat** (§4.2), so it will never be consumed where it sits |

The write surface is the interface's own container/binary write ops (Concepts.md
"Operations Matrix"), scoped to this branch:

- **`uploadBinaryContent`** — write bytes into any live container. The body is raw bytes,
  so `objectId`/`fileName` ride on the query string; a JSON-only invoker may instead send
  `{"argMap":{"objectId","fileName","contentBase64"}}`. Bytes land on a dot-prefixed
  temporary in the destination directory and are `ATOMIC_MOVE`d into place, so the poller
  can never observe a partial file regardless of `stableForSec`. **An existing name is
  refused, never replaced**: `fileId` is `<path>@<hash12>`, so overwriting bytes at a
  consumed path would fork one path into two identities and desynchronise the `.done`
  bookkeeping (§4.2). Delete first if you mean to replace.
- **`createChildObject`** — mkdir, so a caller can choose where uploads land. Containers
  only; there is no way to conjure a file without bytes.
- **`deleteObject`** — unlink a file, or remove an **empty** directory. A non-empty
  directory is refused rather than deleted recursively: a blind recursive delete over a
  live feed directory is how un-ingested claims get lost. The branch root and the
  configured source roots are never deletable — the daemon validated those mounts at boot
  (§3) and removing one takes the receiver down.
- Path traversal cannot escape: every segment is decoded, `.`/`..`/empty are rejected, and
  the normalized result must still sit under the source root.

All three are **off unless the deployment sets `config.allowFileManagement: true`**
(default `false` in `runtimeConfig.yml`). This is a revenue-cycle feed: an open upload path
lets any Hub-authenticated caller inject claims, so production takes files from the feed
and nothing else. `isSupported/{operationId}` answers from the same flag — and reports
`false` for the data writes the receiver never supports (`updateObject`,
`addCollectionElement`, `updateCollectionElement`, `deleteCollectionElement`,
`executeBulkOperations`, `updateDocumentData`) — so a caller can discover this deployment's
real capability set instead of being told a blanket yes. Browsing `/inbox` is unaffected by
the flag; only writes are gated.

The emergent branches reject all three ops whatever the flag says. `/files` is a view of
the buffer, so "uploading" into it would mean inventing a consumed file that never arrived,
and buffer rows leave through `ops/purge`, not `deleteObject`.

## 3. Runtime shape

```
PID 1 ── startup.sh                       (uid/gid 10001, never root)
        ├── nginx                    (0.0.0.0:8888 → 127.0.0.1:8889)
        └── java -jar x12-receiver.jar
              ├── Javalin HTTP on 8889          (DataProducer RPC ops, /healthz)
              ├── InboxPoller thread per source (poll → parse → buffer → rename)
              ├── RetentionSweeper thread       (only when retention is bounded; at start, then every 10 min)
              └── SQLite buffer                 (one connection; every buffer call serialized)
```

Env contract from Hub Node: `INTERNAL_PORT` (default 8889), `MODULE_CONFIG` (JSON of
`runtimeConfig.config`), `RUNTIME_CONFIG_FILE` (optional), `HUB_NODE_INSECURE`, `JAVA_OPTS`
(image default `-XX:MaxRAMPercentage=70`). `BUFFER_DB` (default `/var/lib/module/buffer.db`)
and `RUNTIME_CONFIG_YML` (default `/opt/module/runtimeConfig.yml`) are overridable for tests.
**No `LISTENER_PORT_*`.**

Config resolution: `MODULE_CONFIG` → the `config` object of the node-delivered
`RUNTIME_CONFIG_FILE` → the `config:` block of the `runtimeConfig.yml` copied into the image
(the bare `docker run` / e2e fallback; production always gets `MODULE_CONFIG`) → built-in
defaults, used only when no config is present at all.

**Fail fast.** A config that is present but unusable — bad JSON/YAML, an unknown key at any
level, a wrong type, an out-of-range value, a `sources[]` entry without `name`/`path`, two
sources with the same name or on the same or nested directories — stops the boot with exit 1.
So does boot validation: every `sources[].path` must exist, be a directory and be renameable
(proven by creating a dot-prefixed probe file, renaming it with `consumedSuffix` and then
`errorSuffix`, and deleting it — a suffix the filesystem refuses fails here, not on every file);
names distinct; no two
sources resolving (symlinks followed) to the same or nested directories; `consumedSuffix` and
`errorSuffix` non-blank and different. A daemon that cannot mark files consumed must not run,
and running on defaults would watch the wrong directory or drop the durability an operator
asked for.

`runtimeConfig.yml` declares `daemonMode: true`, two `durability` mounts (`x12-buffer` →
`/var/lib/module`, `x12-inbox` → `/var/lib/x12/inbox`), `resources.memoryMb: 1024`, and the
opaque `config` (§4.1). `connectionProfile.yml` exists for the publish pipeline and is never
read by the daemon.

## 4. Inbox poller

### 4.1 Configuration (`runtimeConfig.config`)

| Key | Default | Rule |
|---|---|---|
| `sources[]` | one source `inbox` at `/var/lib/x12/inbox`, pattern `*.{x12,edi,txt,835,837,277,999,dat}` | non-empty array |
| `sources[].name` | — (required) | provenance label (`/by-source/<name>`, `sourceName` column); distinct |
| `sources[].path` | — (required) | directory inside the container; not the same as or nested with another source's |
| `sources[].pattern` | `*` | glob against the file name, case-insensitive |
| `sources[].pollIntervalSec` | 30 | > 0 |
| `sources[].stableForSec` | 60 | ≥ 0 |
| `consumedSuffix` / `errorSuffix` | `.done` / `.error` | non-blank, no `/` or `\`, different from each other |
| `ackDurability` | `full` | `full` \| `normal` (§8.1) |
| `maxFileBytes` | 67108864 (64 MiB) | 1 … 134217728 (128 MiB: a one-transaction file's JSON runs 3–4½× its X12, and SQLite stores no value over 1e9 bytes) |
| `retention.maxBytes` / `retention.maxAge` | unbounded | positive integer / positive ISO-8601 duration (§8.3) |
| `allowBareTransactionSets` | `false` | boolean (§4.3) |

### 4.2 Scan algorithm (per source, every `pollIntervalSec`)

1. List the directory (never descending) for regular files matching `pattern`. **Skip** names
   ending in `.done`, `.error`, `.tmp`, `.part`, `.partial` or the configured suffixes, and
   dotfiles. **Symbolic links are skipped, never followed**, and logged once per path: a link
   can point anywhere in the container, and renaming it would need an identity only reading
   through it could give. **Nothing is skipped by path**: every stable candidate is hashed and
   its identity decides what happens (step 3a). The one in-memory guard is per process: a file
   this process already consumed that is *still at its path* (its post-commit rename failed) is
   keyed by `(path, size, mtime)` and not re-hashed; the key is dropped as soon as the path
   leaves the listing, and nothing survives a restart.
2. For each candidate, record `(size, mtime)`; a file is **stable** when the pair has been
   unchanged for ≥ `stableForSec` across polls (first sighting starts the clock). Unstable files
   are logged at debug and retried next poll.
3. Consume a stable file — one **transaction per file**. Every file is handled in isolation:
   whatever one file throws is logged and counted against the scan, and the scan moves on.
   a. Read the file without following a symlink and `sha256` it → `checksum`;
      `fileId = <path>@<checksum[0..12)>`. The read must see exactly the `(size, mtime)` the
      stability window saw, before and after, or the file is left for the next scan. A file
      that cannot be read has no identity yet: it is retried every scan, and after 5 failed
      reads in a row it gets one `error` row (`<path>@unreadable`, removed by the first
      successful read) and one ERROR log line. A file over `maxFileBytes` is hashed by
      streaming, never held in memory, and goes to `.error` as `too-large`; a file within it
      whose bytes the heap cannot hold (the allocation fails) is hashed the same way and goes
      to `.error` as `too-large-for-heap` — retrying would fail the same way every scan.
      Look the checksum up in `files` (a `consumed` row wins over a `duplicate` one, which wins
      over an `error` one). Known with status `consumed|duplicate` → **duplicate**: when a row
      with this very `fileId` exists (the same bytes re-landed at the same path — a
      *redelivery*) its `status` stays and `redelivery_count` is incremented; otherwise insert a
      `files` row with `status='duplicate'` (audit trail; an `error` row that happens to carry
      this `fileId` is replaced by it). Either way insert NO transactions and rename `.done`
      (HL7 re-send rule: a duplicate is acknowledged, never re-ingested). Known only with status
      `error` → **retry**: if that error row has this `fileId` (same path, same bytes — an
      operator renamed the file back unchanged) it is deleted and the file is consumed normally
      from 3b, so the row is replaced rather than duplicated; an `error` row for the same bytes
      at a *different* path is left as its own audit record. Unknown checksum → consume normally.
   b. Parse with imsweb: detect the delimiters from the first ISA (every further ISA must
      declare the same ones), then parse **each functional group with the `X12Reader.FileType`
      its own GS08 selects** (§6) — an imsweb reader checks only the first GS08, so an
      interchange carrying, say, a 999 group and a 277CA group is fed group by group and
      stitched back in document order. GS08 aliases are rewritten to the canonical id for
      imsweb only; the stored raw is untouched. A GS08 outside the guide table fails the file
      with `unsupported-guide`; an ST01 the guide's map does not describe (a 276 under
      `005010X212`) with `unsupported-transaction`.
   c. For every transaction set of every group of every interchange: materialize (§5) and build
      the envelope. `BufferStore.consumeFile` inserts every transaction row and then the `files`
      row (`status='consumed'`, `isa_count`, `transaction_count`) in **one SQL transaction**.
      Every row must land: two transaction sets of one file sharing `ISA13/GS06/ST02` roll the
      whole file back and send it to `.error` as `duplicate-element-key`.
   d. `COMMIT`, **then** `rename(path, target)` where `target` is `<path><consumedSuffix>`, or
      `<path>.<discoveredAtEpochMillis><consumedSuffix>` when that name already exists (the same
      name delivered again with different bytes); a rename never replaces an existing file, and
      the actual target is persisted in `files.current_path`. If the rename fails after commit,
      log at error and mark `files.rename_failed=1` (`current_path` = the discovery path) — the
      next scan re-hashes the file, finds the same `fileId` and treats it as a redelivery (3a),
      so the rows are never duplicated.
   e. On any failure that belongs to the file (nothing is committed for it): insert a `files`
      row with `status='error'` and `error_message = <kind>: <detail>`, **then** rename
      `.error` with the same collision rule. Kinds: `empty-file`, `no-isa`, `bad-isa` (short
      ISA, bad or non-distinct delimiters, a later ISA with other delimiters), `bad-gs`,
      `bare-transaction-set`, `unsupported-guide`, `unsupported-transaction`,
      `structure-mismatch`, `fatal` (imsweb fatal errors, verbatim), `parser-failure`,
      `too-large`, `too-large-for-heap`, `duplicate-element-key`, `buffer-rejected` (SQLite
      refused one of the file's rows: a value over its 1,000,000,000-byte limit, a constraint, a
      type mismatch — disk full, I/O or lock errors are the store's, and the file is retried),
      and `internal: …` for any other exception, heap or stack exhaustion while parsing that file. Logs carry the kind only — the detail can quote segments, and X12 here
      is PHI. Errors are never retried automatically; an operator renames the file back (or
      fixes it) and the next scan picks it up: unchanged bytes replace the error row (3a retry)
      and, failing again, land back in `.error`; fixed bytes are a new file (new `fileId`) and
      the old error row stays as the audit record.
4. Backpressure: when live buffer data is over `retention.maxBytes`, the consumer sweeps first
   (§8.3) and, if still over, **leaves files untouched** (no rename, no insert) and reports
   `backpressure: true` in `/stats` and `/healthz` (503). Files are their own queue — strictly
   better than `MSA|AE`.

One `FileConsumer` is shared by every poller and consumes one file at a time: the checksum
lookup and the insert that follows must not interleave, or the same bytes dropped into two
sources at once would be ingested twice.

On shutdown a running scan stops at the next file boundary: the file in hand is finished (its
commit and rename are one unit as above), the rest are left for the next start. The pollers get
at most 5 s together before the buffer closes — Docker kills the container 10 s after SIGTERM —
and a scan wedged on a hung mount is abandoned rather than waited for.

### 4.3 Envelope handling

- Files with ISA…IEA: normal. Files that start at `ST` (some senders and published examples
  omit the envelope) are accepted only when `config.allowBareTransactionSets: true` (default
  **false**; otherwise `.error` as `bare-transaction-set`): the parser synthesizes an ISA/GS
  envelope — guide from `ST03` (required), functional identifier from `ST01`, fixed
  `SYNTHETIC` sender/receiver, `ISA13 = 000000001`, `GS06 = 1` — and stamps
  `envelope: synthetic` on the rows.
- Multiple ISA in one file (some clearinghouses concatenate): each interchange is walked;
  `fileId` is shared and the element key's ISA13 tells the transaction sets apart.
- Envelope integrity: imsweb verifies none of SE01/SE02, GE01/GE02, IEA01/IEA02 and accepts an
  ST without SE or a GS without GE. The wrapper checks all of them and reports mismatches as
  **non-fatal** parser errors — the file is consumed, and the file's error count is stamped on
  every row as `parserErrorCount` (imsweb does not attribute its non-fatal errors to a
  transaction set). A transaction set still open when the next ST, GE, IEA, GS, ISA or the end
  of the file arrives is `missing-se`; it keeps the segments it was sent with, and the envelope
  segment that closed it is never counted as one of them.

## 5. Materializer — imsweb Loop tree → typed JSON

- Walk the parsed `Loop` tree against the **structure index** (generated at build time from the
  same `mapping/*.xml` imsweb ships — §6) rather than trusting `Loop.toJson()` (XStream output is
  shaped for XStream, not for consumers). For each loop: `{ "<segXid lower>": {...}, "loop<xid>":
  [...] }`; for each segment: `{ "<xid lower><nn>": value }` with §2.4 normalization; composites
  nest; a segment or loop is an array exactly when the index says `multi`. A second occurrence
  of a single-use segment or loop is left out of the JSON (the raw keeps it, and imsweb reports
  it as a non-fatal error); `^`-repeated elements become arrays; empty elements are omitted;
  anything the index does not know is emitted under its generic key so nothing is dropped.
- Envelope overlay (authoritative, top level, stored in `mapped_json`): `elementKey, fileId,
  fileName, sourceName, isaControlNumber, gsControlNumber, stControlNumber, gs08,
  transactionType, senderId (ISA06), receiverId (ISA08), interchangeDate (ISA09+ISA10 as UTC;
  omitted when malformed), receivedAt, envelope (file|synthetic), parserErrorCount`. Served
  elements add `status` and `leaseId` from the row.
- Numeric typing is **on** in v1 (imsweb gives the `data_type`; hl7/v2 deferred it because HAPI
  did not) — `N*`/`R` become JSON numbers via `BigDecimal.toPlainString()` semantics.
- A guide without a bundled structure index would take the envelope-only degrade (the row keeps
  its envelope under `schema:shared:x12.transaction-envelope`); with the shared guide table (§6)
  every parseable guide has one.
- Ingest (`FileConsumer`) and re-materialization (`recast`/`validate`) build the JSON through the
  same code (`TransactionJson`), so a fresh row reproduces byte-for-byte and `repsAgree` means
  what it says.

## 6. Schema content — build-time generation

`java/src/main/resources/com/zerobias/module/x12/parser/guides.txt` is the one guide table:
`TransactionTypes` reads it at runtime and `java/codegen/` reads it at build time, so the guides
the receiver parses and the guides it has schemas for are one list. Each row: canonical GS08,
display type, imsweb `FileType`, pyx12 map, accepted wire aliases.

| GS08 | Type | Note |
|---|---|---|
| `005010X221A1` | 835 | alias `005010X221` |
| `005010X222A1` | 837P | alias `005010X222` |
| `005010X223A2` | 837I | imsweb maps X223.A1 but checks the A2 errata id; aliases `005010X223`, `005010X223A1` |
| `005010X214` | 277CA | |
| `005010X212` | 277 | a 276 under this GS08 is `unsupported-transaction` |
| `005010X231A1` | 999 | imsweb keys the map as `005010X231`; alias `005010X231` |
| `005010X220A1` | 834 | alias `005010X220` |

**Not supported** — no imsweb 005010 definition: 820 (X218), 270/271 (X279; imsweb has only
4010 X092), 837D (X224). Their files go to `.error` as `unsupported-guide`; a custom mapping XML
(schema `map.v2.xsd`) can add them later.

`java/codegen/` (build-time-only Maven project, mirrors hl7/v2's) reads the pyx12 maps named in
the table plus `dataele.xml` and `codes.xml` from the imsweb jar and emits:

```
java/src/main/resources/schemas/<GS08>/{transactions,loops,segments,composites}/*.json
java/src/main/resources/schemas/codes/*.json                   (code-set enums)
java/src/main/resources/schemas/ops/*.json                     (receiver enums)
java/src/main/resources/schemas/shared/*.json                  (envelope, file, receiver-stats[-source])
java/src/main/resources/structure-index/<GS08>.json            (materializer index)
```

The function input/output schemas and the `not-found-error` / `ops-verdict` shapes are built in
code by `SchemaRegistry`, so the declared input schema and the validator that enforces it share
one definition. Output is **git-ignored and regenerated on every build** (`generate-resources`,
never a profile); runtime serves from the classpath. The pyx12 mapping files are BSD-licensed and
already inside the imsweb jar — the codegen reads them from the classpath, nothing is vendored.

## 7. Content packs

Content is delivered as **packs**. A pack is one X12 guide's schema tree plus its structure
index, or the code-set enums, or the receiver's own shared schemas. The manifest shape is
the same however a pack arrives, so the loader has one code path and `ops/packs` reports
provenance uniformly.

Today every pack is **bundled**: generated by this package at `generate-resources` and baked
into the image. The delivery paths for later (npm at image build; upload + validate at
runtime) reuse the same format — only the root a pack resolves against differs.

### 7.1 Artifacts

The generator emits two manifests alongside the schema trees:

| Artifact | Contents |
|---|---|
| `schemas/index.json` | schema id → `schemas/`-relative path, for every emitted schema |
| `packs.json` | the pack manifests, in emission order |

`index.json` is the registry's preferred discovery path (`SchemaRegistry.fromClasspath`
falls back to walking the classpath only when it is absent). Emitting it makes enumeration
explicit: a schema that failed to emit is a missing key, not a silently absent file.

A pack manifest carries `name`, `namespace`, `source`, `gs08`/`aliasOf`/`transactionType`
(guide packs only), `structureIndex`, `idScope`, `schemaCount` and `schemaIds`.
**`schemaIds` is the authoritative ownership record** — `idScope` is a human-readable
summary, because a guide pack owns both `schema:type:x12.<gs08>.<xid>` and its
`schema:table:x12.<gs08>.<TS>` id, which share no prefix. `version` is absent for bundled
packs: their version *is* the module's, so carrying a second one would only be a thing to
keep in sync.

### 7.2 The bundled set

One pack per guide **label** — alias labels (e.g. `005010X231` for `005010X231A1`) get their
own pack pointing back at the canonical guide via `aliasOf`. That way a customer
companion-guide pack can later supersede exactly one guide instead of shadowing the whole
core. Plus:

- **`x12-codes`** — the code-set enums, their own pack because X12 republishes
  CARC/RARC/claim-status on a quarterly cadence that has nothing to do with the guides. A
  code refresh must not mean regenerating every guide.
- **`x12-core`** — the receiver's own shared schemas and ops enums. Marked `core: true`;
  nothing may ever supersede it, because it is the module's contract rather than content.

### 7.3 `ops/packs`

Read-only: what content this deployment has and where it came from. Optional `name` / `gs08`
narrow the report; an unknown value yields an empty list rather than an error, since "is
this pack present?" is exactly the question being asked. Each pack reports
`status: active | degraded` — degraded meaning it declares schema ids the registry cannot
serve, which is the failure this op exists to surface. Absent or malformed `packs.json`
degrades to an empty catalog with a warning, never a boot failure: an always-on receiver
must keep ingesting even when only its provenance reporting is broken.

### 7.4 Not yet built

`packInstall` / `packRemove` / `packReload` wait on a delivery path. When they land:

- Customer pack ids must be namespaced (`schema:type:x12.<vendor>.<gs08>.<xid>`) — the
  registry rejects duplicate ids by design, and id shape cannot change once rows reference
  it, so this has to be settled before the first external pack ships.
- `packReload` swaps an immutable registry + structure-index snapshot, so no restart is
  needed. It must refuse (or keep the prior snapshot alive) while un-acked rows reference a
  schema the reload would drop — otherwise in-flight transactions lose their schema
  mid-lease.
- The daemon does not fetch packs. It holds no registry credentials and needs no egress: the
  platform fetches server-side and delivers via `uploadBinaryContent`, or the image build
  bakes the pack in.

Trading-partner deviations that do *not* warrant a pack remain handled by lenient parsing
plus the `parserErrorCount` envelope field.

## 8. Buffer

Same engine as hl7/v2 (SQLite, WAL, epoch-millis, single connection, lease manager, sweeper).
`auto_vacuum=INCREMENTAL` is set before the tables are created so deletes can hand pages back.
DDL (`java/src/main/resources/buffer/schema.sql`):

```sql
CREATE TABLE IF NOT EXISTS files (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  file_id TEXT NOT NULL UNIQUE,        -- <file_path>@<first 12 hex of checksum> (<file_path>@unreadable for a file that never read)
  file_path TEXT NOT NULL,             -- absolute path at discovery (before the rename)
  file_name TEXT NOT NULL, source_name TEXT NOT NULL,
  current_path TEXT NOT NULL,          -- the actual rename target (.done/.error, possibly timestamped); the discovery path when the rename failed
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
  element_key TEXT NOT NULL UNIQUE,    -- <fileId>:<ISA13>:<GS06>:<ST02>
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
CREATE INDEX IF NOT EXISTS transactions_unacked ON transactions(received_at) WHERE status <> 'acked';  -- take, backlog, oldestUnacked
CREATE INDEX IF NOT EXISTS transactions_acked ON transactions(status, acked_at);                       -- purge, retention, counts
CREATE INDEX IF NOT EXISTS transactions_received ON transactions(received_at);                         -- newest-first browse
CREATE INDEX IF NOT EXISTS transactions_type ON transactions(transaction_type, gs08);                   -- /by-type, /by-type/<TS>
CREATE INDEX IF NOT EXISTS transactions_gs08 ON transactions(gs08);                                     -- /by-version
CREATE INDEX IF NOT EXISTS transactions_sender ON transactions(sender_id);                              -- /by-sender
CREATE INDEX IF NOT EXISTS transactions_source ON transactions(source_name);                            -- /by-source
PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;           -- overridden at open by ackDurability (§8.1)
```

Acked rows are most of the table (retention keeps them for `maxAge`), so every hot path reaches
its rows through an index; a partial index is used only when the query repeats its `WHERE` term
verbatim, which `LeaseManager` and `BufferStore` do. The facet folders are emergent from the
buffer's distinct values, so each discriminator column leads an index: a folder's children and
their sizes are one ordered pass over a covering index, and resolving one id is a single index
probe (`SELECT 1 … LIMIT 1`), never a table scan plus a sort under the store's lock. `files` rows
are never evicted — they are the audit trail.

### 8.1 Durability

`ackDurability` sets `PRAGMA synchronous` at open: `full` (the default) fsyncs every commit, so
a file is renamed `.done` only once its rows are on disk — the rename is the acknowledgement.
`normal` fsyncs at WAL checkpoints: faster, but a power loss can drop the rows of files already
renamed `.done`.

### 8.2 Leases

As hl7/v2 §8.2: `take` selects drainable rows (`new`, or `in_flight` with an expired lease) oldest
first, optionally narrowed by the filter, and marks them `in_flight` under a new lease id and
`in_flight_until = now + leaseTtl` (default `PT5M`, capped at `PT1H` — `validateFunctionInput` warns
for a longer one); an expired lease needs no
sweeper. `ack` finalizes (`acked`, `acked_at`), `release` returns rows to `new`, each for the whole
lease or a subset of element keys; rows of a partially acked lease revert at TTL. `replay` forces
`in_flight` rows back to `new` regardless of TTL. The buffer forgets a lease once none of its rows
are `in_flight`, which is what makes a later `ack`/`release` of it a 404 (§2.5). Delivery is
**at-least-once**: a consumer that dies between `take` and `ack` gets the rows again after TTL.

### 8.3 Retention and backpressure

The sweeper (running only when `retention` sets a bound) evicts **acked** transaction rows only —
un-acked rows are never evicted and `files` rows never touched — past `maxAge` (by ack age) and,
oldest acked first in batches, while live data exceeds `maxBytes`; every pass ends with an
incremental vacuum. Live data is `(page_count − freelist_count) × page_size`: pages a delete has
freed count as room at once instead of keeping the buffer "over capacity" until vacuumed. The
same measure drives backpressure (§4.2 step 4). `purge` deletes acked rows on demand the same
way. Every delete removes at most 500 rows (a `LIMIT`-bounded `id IN (…)` statement) and every
vacuum step hands back at most 1024 pages; the store's lock is released between them, so a sweep
over millions of rows never stalls ingestion or a `take` for its whole length. `/stats` reports the file size including free pages as `dbSizeBytes`.

## 9. Health

`GET /healthz` (Hub Node polls every 30s; a failing probe raises a Node alert):

```
{ poller: { up, lastScan?, lastConsumed?, bufferDepth, oldestUnackedSec?, backpressure,
            sources: [ { name, path, writable, pending, errored, stalled, consecutiveFailures,
                         lastScanStarted?, lastScanCompleted?, lastError? } ] },
  db: { walBytes, sizeBytes } }
```

- `bufferDepth` is the un-acked backlog (`new` + `in_flight`) — the same figure `/stats` and the
  connection metadata report; acked rows awaiting retention are not depth.
- `lastConsumed` falls back to the newest `files.consumed_at`, so it survives a restart.
- Optional fields are omitted when there is nothing to report rather than emitting zeros.

**503 (degraded)** when a poller thread is not alive, under backpressure, or when any source is
unwritable, has failed 3 scans in a row, or is **stalled** — no scan completed and no file
finished for 3 × `pollIntervalSec` + 60 s. A live thread is not proof of ingestion: a scan
wedged on a hung mount, or one that throws every time, keeps `up=true` while nothing moves;
measuring from the last finished file as well as the last completed scan keeps a long catch-up
scan over a backlog from reading as a stall.

The `/stats` document (`schema:shared:x12.receiver-stats`) carries the same poller fields plus
`newCount`, `inFlightCount`, `ackedCount`, `fileCount`, `doneFileCount` and
`oldestDoneFileAgeSec` (the `.done` files still in the watched directories, §11.2), `walBytes`,
`dbSizeBytes`, and per source `{name, path, writable, pending, errored, lastScanCompleted}`.

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

## 12. Test fixtures

- The JUnit suites under `java/src/test` and `java/codegen/src/test` are the test surface
  (`mvn verify`; the gate runs the same). There are no integration (`*IT`) tests; the failsafe
  plugin stays declared because the gate's integration task invokes it, and it passes with none.
- Committed fixtures under `java/src/test/resources/fixtures/` are **synthetic** — fictional
  payer/provider and example identifiers, one file each for 835, 837P, 837I, 277CA and 999 — plus
  deliberately malformed files for the `.error` path. The ASC X12 published examples are X12
  intellectual property and are not reproduced in this repository.
- `java/scripts/e2e-local.sh` is a local dev tool, not part of the gate: it builds the real
  container, drops real files into a bind-mounted inbox and drives the RPC surface end to end.
