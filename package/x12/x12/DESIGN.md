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
| Parser | HAPI generic + structure index | imsweb `X12Reader`, one definition per functional group (selected by its GS08) + mapping index |
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
   │    └─ /files/<fileId>            ["container","document","binary"]  files row as document, raw EDI downloadable
   │         └─ /files/<fileId>/transactions   collection — that file's ST..SE atoms (envelope schema)
   ├─ /transactions                   collection (heterogeneous, envelope schema) — everything
   ├─ /by-type                        container → /by-type/<TS>            containers, e.g. 835, 837P, 837I, 277CA, 999
   │    └─ /by-type/<TS>/<GS08>       collection — that type under one guide (the guide's table schema)
   ├─ /by-version                     container → /by-version/<GS08>       collections, e.g. 005010X221A1
   ├─ /by-sender                      container → /by-sender/<ISA06>       collections
   ├─ /by-source                      container → /by-source/<sourceName>  collections — one per watched dir
   ├─ /inbox                          container → one child per configured source (LIVE filesystem, §2.9)
   │    └─ /inbox/<source>            container — that source's directory
   │         ├─ /inbox/<source>/<dir>   container — a real subdirectory
   │         └─ /inbox/<source>/<file>  ["binary"] — a real file, ingested or not
   ├─ /claims, /payers, …             business collections projected out of the object graph (§8.5)
   ├─ /stats                          document — poller + buffer metrics (§9)
   └─ /ops                            container → take · ack · release · replay · recast · purge · raw · validate · rescan · packs
```

Rules (from hl7/v2 `ObjectTree` javadoc, restated):
- **A transaction set is an atom** — a collection *element*, never a node. Element key =
  `<fileId>:<ISA13>:<GS06>:<ST02>` (interchange file, interchange control number, group control
  number, transaction control number). ISA13 is in it because one file may carry several
  interchanges and GS06/ST02 are only unique within theirs. The key is opaque: no reader splits
  it, so its shape can change without a migration (older rows keep the key they were stored under).
- **Folders are discriminators and their children are emergent**: read live from the buffer's
  `DISTINCT` values, so a node appears the first time matching data lands.
- **`/files/<fileId>` is the one exception**: a file is a folder (its transactions), a
  document (its `files` row) and a binary (its bytes) — `objectClass: [container, document,
  binary]`. `fileId` = `<absolute path inside the container at discovery time>@<first 12 hex of
  the sha256 of the bytes>` (the path is the one before the `.done` rename) — the same bytes
  re-landing at the same path share an id (a *redelivery*, counted), new bytes under a reused
  name get a new id, so a path is never a skip key. The node carries only interface
  `DataProducerObject` fields: `documentSchema: schema:shared:x12.file`, `fileName`, `size`,
  `mimeType: application/EDI-X12`, `checksum` (sha256 of the bytes, hex), `modified` (file
  mtime), `created` (discovery time), `tags: [source:<name>, status:<consumed|error|duplicate>]`.
  The interface has no `binarySchema`, so the row's own fields — `fileId`, `filePath`,
  `currentPath`, `status`, counts, `errorMessage`, `renameFailed`, `redeliveryCount` — are the
  node's document, read with `getDocumentData`, and never extra keys on the node.
- **Ids are stable.** A node's class never depends on what else is in the buffer:
  `/by-type/<TS>` is always a container of one `/by-type/<TS>/<GS08>` collection per guide
  present, even while a single guide carries that type. An id a caller saved as a collection
  must not turn into a container the day a second GS08 of the same type arrives.
- **Homogeneity**: a collection has exactly one `collectionSchema`. `/by-type/<TS>/<GS08>` is
  bound to that guide's `schema:table:x12.<GS08>.<TS>`. Coarse facets (`/by-version`,
  `/by-sender`, `/by-source`, `/transactions`, `/files/<id>/transactions`) are heterogeneous →
  `schema:shared:x12.transaction-envelope`.
- `<TS>` display names come from the guide table (`guides.txt`, §6): `835` (X221A1), `837P`
  (X222A1), `837I` (X223A2), `277CA` (X214), `277` (X212), `999` (X231A1), `834` (X220A1). A GS08
  outside the table never reaches the buffer: the file goes to `.error` as `unsupported-guide`.

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
| Code set coded inline (`valid_codes`) | `schema:enum:x12.codes.<dataEle>` | `schema:enum:x12.codes.1029` (CLP02 claim status; 1032 is CLP06 filing indicator) |
| Code set from imsweb `codes.xml` | `schema:enum:x12.codes.<codeset>` | `claim_status_cat`, `claim_status`, `adjustment_reason` (CARC), `remark_code` (RARC), `states`, `country`, `currency`, `pos` |
| Receiver enums | `schema:enum:x12.ops.<Name>` | `TransactionStatus`, `EnvelopeOrigin`, `FileStatus` |
| Transaction envelope (shared) | `schema:shared:x12.transaction-envelope` | — |
| File document (a `/files/<fileId>` node's row) | `schema:shared:x12.file` | — |
| Receiver stats document | `schema:shared:x12.receiver-stats` (+ `receiver-stats-source`) | — |
| Functions | `schema:function:x12.ops.<fn>:input\|output` | `schema:function:x12.ops.take:input` |
| Function error / verdict shapes | `schema:shared:x12.not-found-error`, `schema:shared:x12.ops-verdict` | — |

These are module-internal addressing served by `getSchema` (hl7/v2 §2.2 scope note applies).

A coded position references the codeset its uses take their values from: when every use of it
points at one `codes.xml` codeset, the enum is that codeset (so 277CA STC01-01 and STC01-02, both
data element 1271, stay two enums — `claim_status_cat` and `claim_status`); when it is coded
inline, or its uses mix sources, the enum is its data element and absorbs every list involved.
A codeset is deliberately not keyed by its own declared data element: `remark_code` declares
127, the generic Reference Identification, and only the positions the guide marks reference it.

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
| `TM` (`HHMM[SS[dd]]`) | `string` | no core time-only type; normalized `HH:MM:SS[.d[d]]`, seconds always present |
| Date Time Period (element 1251) | `string` | by its format qualifier (1250): `D8` → `YYYY-MM-DD`, `RD8` → the ISO 8601 interval `YYYY-MM-DD/YYYY-MM-DD`, `DT` → `YYYY-MM-DDTHH:MM:SS` |
| control numbers (I12 ISA13, 28 GS06 / AK102, 329 ST02 / AK202) | `string` | `N0` on the wire, but leading zeros are identity (`000000101`) and a 999 joins AK102 to the acknowledged GS06 by text |
| `B` (binary) | `byte` | rare; base64 |
| composite (`C0nn`) | composition ref → `schema:type:x12.<GS08>.<compositeXid>` | sub-elements are its properties |
| repeated element (`^`) | parent property + `multi: true` | |

Materialization normalizes on the way in (like `Hl7Normalizer`): trimmed `AN`, decimal-shifted
`N*`, ISO dates, times and date periods, the ISA fixed-width padding stripped. Consumers never see
wire formats. A value that does not fit its declared type — a date that does not exist such as
`20261345`, an unknown 1250 qualifier — is kept trimmed-but-unchanged rather than dropped or
coerced: no silent data loss on a dirty feed.

### 2.5 Function objects

Inherited from hl7/v2 with the same I/O and constants (`DEFAULT_MAX=100`, `MAX_CAP=1000`,
`DEFAULT_TTL=PT5M`, partial `ack`, lease revert at TTL, `serializeNulls`):

| fn | input | output |
|---|---|---|
| `take` | `{filter?, max?, leaseTtl?}` | `{leaseId, transactions[], remaining}` |
| `ack` / `release` | `{leaseId, elementKeys?}` | `{acked\|released: n}` — idempotent: `0` for a lease that is unknown, already finalized or expired, never a 404 |
| `replay` | `{filter?}` | `{replayed: n}` |
| `recast` | `{filter?, max?}` | `{examined, recast, unchanged, failed}` — re-materialize from stored raw under current definitions and replace the graph only where it changed |
| `purge` | `{olderThan?}` | `{purged: n}` — acked rows only |
| `raw` | `{elementKey}` | `{elementKey, fileId, gs08, transactionType, raw}` — the stored single-transaction interchange: ISA/GS lines, the ST..SE segments verbatim, GE/IEA trailers counting this one set (§4.2 step 3c) |
| `validate` | `{elementKey}` | `{elementKey, schemaId, stored:{valid,errors[]}, rematerialized:{valid,errors[],schemaId}, repsAgree, parserErrors[], parserErrorCount}` — `rematerialized` re-parses the stored raw under the current definitions (`MaterializerRecastHook`); `parserErrors` are imsweb's non-fatal `getErrors()` from that re-parse |
| **`rescan`** (new) | `{source?}` | `{scanned, discovered, consumed, errored}` — trigger an immediate poll; the only way to force a pickup between intervals |
| **`packs`** (new) | `{name?, gs08?}` | the bundled content packs and whether the registry serves them (§7.3) |

The re-materialization behind `recast` and `validate` is always present: the module ships the
structure indexes it re-parses with, so `rematerialized` and `repsAgree` are never null, and a
row that cannot be re-derived counts as `failed` (`validate`: `rematerialized.valid=false`,
`repsAgree=false`) rather than stopping the call. `RecastHook` is an interface only so a test can
substitute a hook whose output differs from the store — the real one reproduces every row it
ingested, which would leave the rewrite path of `recast` unexercised.

Each function node declares in `throws` exactly the codes it raises: `raw`, `validate` and
`rescan` raise `not_found` (an unknown `elementKey` or source), shaped
`schema:shared:x12.not-found-error` — the platform `noSuchObjectError` body. Nothing else is
declared, because nothing else is raised: there is no outstanding-lease cap, backpressure holds
files back on the inbox side rather than failing `take`, and `ack`/`release` are idempotent (the
buffer clears `lease_id` on finalize, so an unknown, a finalized and an expired lease are
indistinguishable, and a retried ack must not fail).

Every function input is checked against its declared `schema:function:x12.ops.<fn>:input`
(one in-code table in `SchemaRegistry` drives both the served schema and the check) before
anything runs: an unknown key, a wrong JSON type, a missing required property, an unparseable
filter/duration, `max < 1` or an empty `elementKeys` is a 400 `illegalArgumentError` — never a
silently dropped argument (`purge {"olderthan":"P30D"}` must not purge every acked row).
`FunctionsApi.validateFunctionInput` (body `validateFunctionInputRequest: {input, strict}`)
runs the same check without executing and returns the interface `ValidationResult`
(`{valid, errors[{path,message,code}], warnings[{path,message}]}`). Input that runs but is
adjusted is a warning, an error under `strict`: a `max` above 1000, and a `leaseTtl` above
`PT1H` — the buffer grants at most an hour (`BufferStore.MAX_LEASE_TTL`), so a longer lease
returns its rows to `new` after the hour, not after what the caller asked for.

### 2.6 Filter semantics

RFC4515 over **schema property names**, parsed by lite-filter and translated to SQLite by an
`X12SqlAdapter` cloned from `Hl7SqlAdapter` (reflect public getters; switch on public enums; date
extensions over epoch-millis; `COLLATE NOCASE` string equality). Envelope properties resolve to
real columns — `elementKey`, `fileId`, `sourceName`, `transactionType`, `gs08`, `senderId`,
`receiverId`, `receivedAt`, `status`, `leaseId`, plus `isaControlNumber`, `gsControlNumber`,
`stControlNumber`, `interchangeDate`, `envelope`, `parserErrorCount`, `schemaId`; every other
property is a dotted body path resolved into the object graph (§8.5.1). Examples:

```
(&(transactionType=835)(senderId=ABCPAYER)(receivedAt>=2026-09-01)(status=new))
(|(transactionType=837P)(transactionType=837I))
(loop2100.clp.clp02=1)
(receivedAt:withinDays:1)
```

Same lite-filter constraints as hl7/v2 §2.6 (date-only literals; absolute-date `>=` is SQL-only).
A filter the caller got wrong — a syntax error, an illegal path segment, a `:between:` bound that
is not a plain decimal, an array operator SQL cannot express — is a 400 `illegalArgumentError`
raised where it is detected (§2.7).

### 2.7 Errors

RPC method names on the wire (`POST /connections/{id}/{ApiClass.method}`) use the interface's
operationIds: `ObjectsApi.getRootObject|getObject|getChildren|searchChildObjects`,
`CollectionsApi.getCollectionElements|getCollectionElement|searchCollectionElements`,
`DocumentsApi.getDocumentData`, `BinaryApi.downloadBinary`, `FunctionsApi.invokeFunction|
validateFunctionInput`, `SchemasApi.getSchema`, plus the gated `ObjectsApi.createChildObject|deleteObject`
and `BinaryApi.uploadBinaryContent` (§2.9). Names are matched exactly — there are no aliases — and
`argMap` keys are the interface's parameter names (`schemaId`, `createObjectRequest`, `requestBody`,
`validateFunctionInputRequest`). `objectSearch` is not implemented, so api.yml does not reference its
path and `isSupported` answers false. `isSupported` is an explicit whitelist of the ops above.

Every parameter an operation declares is honoured or, when set, rejected — never silently dropped.
`getChildren` rejects `sortBy`/`sortDir`/`type`/`tags`/`pageToken`; `searchChildObjects` rejects
`sortBy`/`sortDir`/`filter`/`pageToken`/`properties` and `scope=subtree` (`one_level` is the listing);
the collection ops honour `filter` and `sortBy`/`sortDir` (one key; a bare value or one-element array)
and reject `pageToken`/`properties`; `deleteObject` rejects `recursive=true`; `createChildObject`
rejects `CreateObjectRequest` fields other than `id`/`name`/`objectClass` (all 400
`UnsupportedOperationError`). Paging is `pageNumber >= 1`, `1 <= pageSize <= 1000` (default 100); out
of range or not an integer is a 400 `illegalArgumentError`, not a silent default. The row offset
`(pageNumber - 1) × pageSize` is computed exactly (`X12ProducerFacade.pageOffset`): in int
arithmetic it wraps negative for a large page number, SQLite reads a negative `OFFSET` as 0, and
the caller would silently get page 1 again — a page past the last addressable offset is a 400.

Identical to hl7/v2 §2.7: wire body is the OpenAPI `errorModelBase` (`{key, template, timestamp,
statusCode}` + subtype fields). 404 for unknown object/schema/file, 400
`UnsupportedOperationError` for every write op (`createChildObject`, `addCollectionElement`,
`uploadBinaryContent`, `updateDocumentData`, …), 400 `illegalArgumentError` for bad filters, sorts,
paging, function input and a request body that is not valid JSON (or not an object). Anything
unexpected is a 500 `err.unexpected` with a fixed generic message: the cause (SQLite, IO) can name
buffer and inbox paths inside the container, so it is logged server-side and never returned.

**A caller mistake is raised as the 400 where it is detected**, as a `ProducerException`
carrying a message written for the caller: lite-filter syntax (`X12Filter.parse`), the SQL
compile (`X12SqlAdapter`), sort direction and property (`X12Filter.orderBy`,
`BusinessEntities.comparator`), business filters — attribute validation and the operands
lite-filter can only reject while evaluating a row (`BusinessFilter`) — and paging. There is no
`IllegalArgumentException` → 400 handler: it turned bugs into 400s that echoed internal messages.
A bare `IllegalArgumentException` that reaches the HTTP layer is a bug and gets the generic 500.

The module's own paths in `api.yml` declare what the server returns: `/connect` answers 200, 400
(`illegalArgumentError` from the interface) or 500 (`UnexpectedError`: the interface's
`errorModelBase` + `msg`) — there are no credentials, so there is no 401; `/metadata` is
`{status: On|Error, bufferDepth}`; `HealthStatus` is exactly the `/healthz` body (§9).

### 2.8 Binary download

`downloadBinary(fileId)` streams the file bytes verbatim from the inbox (the `.done`-renamed
path is resolved from the `files` table, so download keeps working after consumption; after the
file is removed by inbox hygiene → 404 with `reason: gone`, the transactions remain). Range is not
in the generated signature (interface prose only); v1 serves 200 full-content.
The bytes are streamed from disk with a `Content-Length` (never read onto the heap; compression off)
and opened `NOFOLLOW_LINKS`, so a symlink at the path is a 404 `gone`, not followed. The file name is
sender-controlled, so `Content-Disposition` is RFC 6266 `attachment` with a sanitised ASCII
`filename` and the exact name as RFC 5987 `filename*`.

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
  the normalized result must still sit under the source root. Symlinks cannot escape either:
  every existing component below the configured root is lstat'ed and a link at any of them is a
  404 (links are not listed), and stats/opens use `NOFOLLOW_LINKS` so a link swapped in later is
  not followed at the leaf.
- Request bodies are capped at 64 MiB (the `maxFileBytes` default) on both sides — Javalin
  `maxRequestSize` and `client_max_body_size 64m` in both committed nginx confs — so a real 835
  batch can be uploaded raw; over the cap is a 413 in the errorModelBase envelope. The base64 JSON
  intake inflates by a third, so it tops out near 48 MiB.

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
`MODULE_CONFIG`.

**The module config is fail-fast.** Defaults apply only when no config is present at all, and a
present config's missing keys take their defaults. A config that is present but wrong — malformed
JSON or YAML, an unknown key (at any level), a value of the wrong type, a number out of range
(`maxFileBytes` above 128 MiB, a non-positive `pollIntervalSec`, …), a bad glob, duration or
suffix, duplicate source names — throws `InvalidConfigException` and the process exits 1. Nothing
is clamped or ignored: a typo'd `retention` would silently disable eviction, a misspelt source
would watch the default directory, a bad `ackDurability` would drop the durability the operator
asked for — and nothing would show it but an unexpected quiet. The keys, defaults and ranges are
the README's configuration table. Boot then validates every `sources[].path`: must exist, be a
directory, be writable (rename test with a temp file), and no two sources may resolve to the same
real directory; any failure → log + exit 1 (a daemon that cannot mark files consumed must not run).

Boot order (`X12ApiServer.start`): config → source validation → buffer (open + `migrate`, §8.1) →
`GraphBackfill` (§8.1) → retention sweeper → pollers → producer surface → Javalin. The pollers are
started by constructing `X12InboxPollerFactory` directly: there is no discovery step that could
quietly find no provider and boot a receiver that ingests nothing. Every collaborator is required
— constructors reject null rather than swapping in a stub that answers 404 to everything, which
would look like an empty receiver rather than a broken one. Shutdown runs routes, then pollers
(each finishes the file in hand), then the sweeper, then the buffer.

`runtimeConfig.yml` declares `daemonMode: true`, two `durability` mounts (`x12-buffer` →
`/var/lib/module`, `x12-inbox` → `/var/lib/x12/inbox`), `resources.memoryMb: 1024`, and the
opaque `config` (see the file). The heap is `-XX:MaxRAMPercentage=70` of the container, so raising
`memoryMb` raises the heap. `connectionProfile.yml` exists for the publish pipeline, has no
required field and is never read by the daemon; it documents `ackDurability` only.

## 4. Inbox poller

### 4.1 Configuration (`config.sources[]`)

`{name, path, pattern, pollIntervalSec, stableForSec}` per source. `name` is the provenance
label (`/by-source/<name>`, `sourceName` column). Several sources may share a buffer; names must
be distinct, and so must the directories they resolve to (`toRealPath`, so a symlink, `..` or a
trailing slash cannot hide it): two pollers on one directory would race for every file. A source
nested inside another is allowed — each poller scans its own directory flat. `pattern` is a glob against the file name (case-insensitive).

### 4.2 Scan algorithm (per source, every `pollIntervalSec`)

1. List regular files matching `pattern` (one no-follow `stat` each: a **symbolic link is
   skipped**, never followed or renamed, and logged once). **Skip** names ending in `.done`,
   `.error`, `.tmp`, `.part`, `.partial`, and dotfiles. **Nothing is skipped by path**: every stable candidate is
   hashed (candidates are only un-suffixed files, so this is cheap) and its identity decides
   what happens (step 3a). The one in-memory guard is per process: a file this process already
   consumed that is *still at its path* (its post-commit rename failed) is keyed by
   `(path, size, mtime)` and not re-hashed; the key is dropped as soon as the path leaves the
   listing, and nothing survives a restart. A file whose bytes cannot be read has no identity
   yet: it is left alone and retried next poll.
2. For each candidate, record `(size, mtime)`; a file is **stable** when the pair has been
   unchanged for ≥ `stableForSec` across polls (first sighting starts the clock). Unstable files
   are logged at debug and retried next poll.
3. Consume a stable file — one **transaction per file**. The file is `stat`ed (no-follow)
   before a byte is read: if its `(size, mtime)` is not what the stability window saw, it is
   left for the next scan; if its size is over `config.maxFileBytes` (default 64 MiB; at most
   128 MiB, a larger value is a boot failure) it is hashed as a stream (constant memory, so it still gets a `fileId`) and goes
   straight to 3e as `too-large` — one oversized drop must not exhaust the heap and stall every
   file behind it (a file whose bytes do not fit in the heap goes the same way as
   `too-large-for-heap`). The hash is computed while reading, and the file is `stat`ed again
   afterwards: a file that changed while being read is left for the next scan too.
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
   b. Parse (`X12Parse`, the imsweb wrapper). The bytes are decoded as UTF-8 when they are
      valid UTF-8, else ISO-8859-1. The delimiters come from the **fixed-width ISA**: it must be
      106 characters with the element separator at every one of its sixteen positions (a padded
      field one character short shifts ISA16 and the terminator, and every later segment would
      split wrongly), and the delimiters must be distinct and not letters, digits or blanks —
      otherwise `bad-isa`. Every further ISA in the file must declare the same delimiters. Then
      **each functional group is parsed with its own guide**: its GS08 resolves through the guide
      table (`TransactionTypes.guide`, §6) — an alias to its canonical id, which is what the text
      handed to imsweb carries, since imsweb compares GS08 exactly — and the group is fed to its
      own `X12Reader` as ISA + GS..GE + IEA, with the results stitched back in document order. So
      one ISA may carry a 999 group and a 277CA group. A GS08 outside the table fails the file
      as `unsupported-guide`; an ST01 its guide has no map for (a 276 under `005010X212`) as
      `unsupported-transaction`. **Envelope integrity**, which imsweb does not check, is checked
      here: SE01 against the segment count and SE02 against ST02, GE01/GE02 against the group,
      IEA01/IEA02 against the interchange, an ST with no SE before the next ST/GE/IEA
      (`missing-se`), a GS with no GE. These are non-fatal — the set still holds every segment
      it was sent with — and are reported as parser errors counted into `parserErrorCount`; an
      ISA without IEA is reported too, though imsweb then fails the file.
   c. For every `ST_LOOP` in every `GS_LOOP` of every `ISA_LOOP`: materialize (§5), build the
      envelope, insert into `transactions` (+ its graph and dimensions); insert the `files` row
      (`status='consumed'`, counts). Everything guide-bound — `gs08`, `transactionType`, the
      structure index and `schema_id`, the graph, the dimensions — comes from the set's **own**
      functional group, never the file's first. `raw_x12` is a complete, re-parseable
      single-transaction interchange: the ISA and GS lines, the ST..SE segments verbatim, then
      GE and IEA trailers counting exactly this one set and group (the file's own trailers count
      the whole original group and interchange, so copied verbatim they would make every slice
      of a multi-set file invalid). Every set must land: the `fileId` is new at this point
      (3a resolved redelivery and duplicates by checksum), so an element key that is already
      taken can only be two sets of *this* file sharing ISA13/GS06/ST02. That rolls the whole
      file back and sends it down 3e as `duplicate-element-key` — acknowledging it `.done` would
      silently drop one set. (The insert keeps `ON CONFLICT(element_key) DO NOTHING` only so the
      clash reads as "not inserted" rather than as a constraint error indistinguishable from a
      store failure; no path relies on it to skip a row.)
   d. `COMMIT`, **then** `rename(path, target)` where `target` is `<path><consumedSuffix>`, or
      `<path>.<discoveredAtEpochMillis><consumedSuffix>` when that name already exists (the same
      name delivered again with different bytes); the actual target is persisted in
      `files.current_path`. If the rename fails after commit, log at error and mark
      `files.rename_failed=1` (`current_path` = the discovery path) — the next scan re-hashes the
      file, finds the same `fileId` and treats it as a redelivery (3a), so the rows are never
      duplicated.
   e. On any failure that belongs to the file before commit — a parse error, `too-large`,
      `duplicate-element-key`, the parser exhausting heap or stack on it, SQLite refusing one
      of its rows (`buffer-rejected`: over-long value, a UNIQUE/PRIMARY KEY clash) — `ROLLBACK`,
      insert a `files` row with `status='error'`, `error_message` (imsweb `getFatalErrors()`
      for parse errors), then `rename(path, target)` with the same collision rule and
      `errorSuffix`. Errors are never retried automatically; an
      operator renames the file back (or fixes it) and the next scan picks it up: unchanged
      bytes replace the error row (3a retry) and, failing again, land back in `.error`; fixed
      bytes are a new file (new `fileId`) and the old error row stays as the audit record.
   Renames never replace an existing file: a target taken between choosing it and moving onto
   it gets the next free name (a plain move refuses an existing target; `ATOMIC_MOVE` is a bare
   `rename(2)` and would overwrite it).

   **Isolation.** A failure in one file never ends the scan: anything else one file throws
   (an unexpected exception, an `Error` such as OOM) is logged, the file is left in place, and
   the scan moves on. A failure of the *buffer* — any other `SQLException`: I/O, disk full,
   corruption, or a NOT NULL/CHECK constraint (the table no longer matches its INSERTs, as when
   a stale `mapped_json NOT NULL` column stopped every ingest) — is not the file's fault and
   would recur for every file, so it ends the scan, leaves the file in place (no `.error`), and
   is reported by `/healthz` (§9).
4. Backpressure: if the buffer is over `retention.maxBytes` and the sweeper cannot free space,
   the poller **leaves files untouched** (no rename, no insert) and reports `backpressure: true`
   in `/stats` and `/healthz` (503). Files are their own queue — strictly better than `MSA|AE`.

### 4.3 Envelope handling

- Files with ISA…IEA: normal. Files that start at `ST` (as the x12.org 837/277 examples do) are
  accepted when `config.allowBareTransactionSets: true` (default **false** in production, true in
  tests): the poller synthesizes an ISA/GS envelope from the ST03 / first-segment values and
  stamps `envelope: synthetic` on the rows.
- Multiple ISA in one file (rare, some clearinghouses concatenate): each `ISA_LOOP` is walked;
  `fileId` is shared, `isaControlNumber` distinguishes — which is why ISA13 is part of the element
  key (§2.1): two interchanges that both number their first group `1` and first set `0001` are
  ordinary, and both land. Every ISA must declare the first one's delimiters (§4.2 step 3b).
- Functional groups of different guides in one interchange: each group is parsed with its own
  guide (§4.2 step 3b), and each set is stored under it.

## 5. Materializer — imsweb Loop tree → typed JSON

- Walk the parsed `Loop` tree against the **structure index** of the set's own guide
  (generated at build time from the same `mapping/*.xml` imsweb ships — §6) rather than trusting
  `Loop.toJson()` (XStream output is shaped for XStream, not for consumers). For each loop:
  `{ "<segXid lower>": {...}, "loop<xid>": [...] }`; for each segment: `{ "<xid lower><nn>":
  value }` with §2.4 normalization; composites nest; repeated elements/segments/loops are arrays.
  A guide with no index takes the envelope-only degrade: the row is stored under
  `schema:shared:x12.transaction-envelope` with no graph, still drainable and browsable.
- The materialized tree is flattened into the object graph (§8.4); it is never stored as a
  document.
- Envelope overlay (authoritative, top level, applied at read time by
  `X12ProducerFacade.toElement` from the row's columns): `elementKey, fileId, fileName,
  sourceName, isaControlNumber, gsControlNumber, stControlNumber, gs08, transactionType, senderId
  (ISA06), receiverId (ISA08), interchangeDate (ISA09+ISA10 → date-time), receivedAt, status,
  leaseId, envelope (file|synthetic), parserErrorCount`. Control numbers are strings.
- Numeric typing is **on** in v1 (imsweb gives the `data_type`; hl7/v2 deferred it because HAPI
  did not) — `N*`/`R` become JSON numbers via `BigDecimal.toPlainString()` semantics, except the
  control-number elements (§2.4).

## 6. Schema content — build-time generation

`java/codegen/` (build-time-only Maven project, mirrors hl7/v2's) reads the guide table and
`com.imsweb:x12-parser`'s `mapping/<TS>.<ver>.<guide>.xml`, `dataele.xml`, `codes.xml`,
`x12.control.00501.xml` and emits:

```
java/src/main/resources/schemas/<GS08>/{transactions,loops,segments,composites}/*.json
java/src/main/resources/schemas/codes/*.json                   (enums)
java/src/main/resources/schemas/shared/*.json                  (envelope, file, receiver-stats)
java/src/main/resources/structure-index/<GS08>.json            (materializer index)
java/src/main/resources/schemas/index.json, packs.json         (§7.1)
```

**One guide table.** `java/src/main/resources/com/zerobias/module/x12/parser/guides.txt` lists
the guides the receiver ingests, one row each — canonical GS08, display type, imsweb
`X12Reader.FileType`, the pyx12 map under imsweb's `mapping/`, and the wire aliases.
`parser.TransactionTypes` reads it at runtime and the codegen reads the same file at build time,
so a guide is parsed *and* has schemas, or neither. Aliases resolve to the canonical id before any
lookup, so they have no schemas, packs or mappings of their own.

| GS08 | Type | Wire aliases | Note |
|---|---|---|---|
| `005010X221A1` | 835 | `005010X221` | |
| `005010X222A1` | 837P | `005010X222` | |
| `005010X223A2` | 837I | `005010X223`, `005010X223A1` | imsweb maps X223.A1 but checks GS08 against the A2 errata id |
| `005010X214` | 277CA | | |
| `005010X212` | 277 | | |
| `005010X231A1` | 999 | `005010X231` | imsweb keys the map as `005010X231` |
| `005010X220A1` | 834 | `005010X220` | |

**Not ingested**: a guide imsweb 1.16 has no 005010 `FileType` for — 820 X218, 270/271 X279
(imsweb has only 4010 X092), 837D X224. Its files go to `.error` as `unsupported-guide`; a custom
mapping XML (schema `map.v2.xsd`) and a row in the table add one later.

Output is **git-ignored and regenerated on every build** (`generate-resources`, never a profile);
runtime serves from the classpath. The pyx12 mapping files are BSD-licensed (John Holland) and
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

A pack manifest carries `name`, `namespace`, `source`, `gs08`/`transactionType` (guide packs
only; `aliasOf` is reserved for a pack that relabels another and is absent on the bundled set), `structureIndex`, `idScope`, `schemaCount` and `schemaIds`.
**`schemaIds` is the authoritative ownership record** — `idScope` is a human-readable
summary, because a guide pack owns both `schema:type:x12.<gs08>.<xid>` and its
`schema:table:x12.<gs08>.<TS>` id, which share no prefix. `version` is absent for bundled
packs: their version *is* the module's, so carrying a second one would only be a thing to
keep in sync.

### 7.2 The bundled set

One pack per canonical guide (`x12-guide-<GS08>`, seven today). Aliases have no pack: they
resolve to the canonical guide before any lookup (§6). One pack per guide means a customer
companion-guide pack can later supersede exactly one guide instead of shadowing the whole
core. Plus:

- **`x12-codes`** — the code-set enums, their own pack because X12 republishes
  CARC/RARC/claim-status on a quarterly cadence that has nothing to do with the guides. A
  code refresh must not mean regenerating every guide.
- **`x12-core`** — the receiver's own shared schemas and ops enums. Marked `core: true`;
  nothing may ever supersede it, because it is the module's contract rather than content.

### 7.3 `ops/packs`

Read-only: what content this deployment has and where it came from. Optional `name` / `gs08`
narrow the report — a `gs08` in its canonical spelling, like every other GS08 the receiver
handles, so `005010X223A1` names the 837I pack keyed `005010X223A2`; an unknown value yields an
empty list rather than an error, since "is this pack present?" is exactly the question being
asked. Each pack reports
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

Same engine as hl7/v2 (SQLite, WAL, epoch-millis, single writer, lease manager, sweeper).

### 8.1 Tables, durability and migration

DDL (`buffer/schema.sql`; the graph tables are §8.4):

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
  element_key TEXT NOT NULL UNIQUE,    -- <fileId>:<ISA13>:<GS06>:<ST02>  (fileId = <path>@<hash12>)
  file_id TEXT NOT NULL, source_name TEXT NOT NULL,
  received_at INTEGER NOT NULL,
  isa_control TEXT, gs_control TEXT, st_control TEXT,
  gs08 TEXT NOT NULL, transaction_type TEXT NOT NULL,
  sender_id TEXT, receiver_id TEXT, interchange_at INTEGER,
  schema_id TEXT NOT NULL,
  raw_x12 BLOB NOT NULL,               -- ST..SE segments verbatim (+ ISA/GS context lines)
  -- no typed-document column: the content is the object graph (§8.4), reassembled on demand
  parser_error_count INTEGER DEFAULT 0, envelope TEXT NOT NULL DEFAULT 'file',
  status TEXT NOT NULL DEFAULT 'new', lease_id TEXT, in_flight_until INTEGER, acked_at INTEGER
);
CREATE INDEX IF NOT EXISTS transactions_drain ON transactions(schema_id, status, received_at);
CREATE INDEX IF NOT EXISTS transactions_lease ON transactions(lease_id) WHERE lease_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS transactions_file ON transactions(file_id);
CREATE INDEX IF NOT EXISTS transactions_unacked ON transactions(received_at) WHERE status <> 'acked';  -- oldestUnacked
CREATE INDEX IF NOT EXISTS transactions_acked ON transactions(status, acked_at);         -- purge, retention, take's status probes
CREATE INDEX IF NOT EXISTS transactions_type ON transactions(transaction_type, gs08);    -- /by-type
CREATE INDEX IF NOT EXISTS transactions_gs08 ON transactions(gs08);                      -- /by-version
CREATE INDEX IF NOT EXISTS transactions_sender ON transactions(sender_id);               -- /by-sender
CREATE INDEX IF NOT EXISTS transactions_source ON transactions(source_name);             -- /by-source
PRAGMA journal_mode = WAL;
```

Indexes are additive: `CREATE INDEX IF NOT EXISTS` builds a new one on an existing buffer at the
next open, so adding an index needs no `BufferStore.migrate` step (a column change still does).
A partial index is only used when a query repeats its WHERE term, which is why `oldestUnacked`
and the un-acked count behind `bufferDepth` say `status <> 'acked'` verbatim.

**`ackDurability` defaults to `full`** (`PRAGMA synchronous=FULL`, fsync per commit). The `.done`
rename is the ack, so under `normal` a power loss can roll back a commit whose file was already
renamed — that file is lost with nothing left to retry. Only an explicit `normal` weakens it; any
other value is a boot failure (§3).

**`schema.sql` is not a migration.** The `x12-buffer` volume outlives every image, and
`CREATE TABLE IF NOT EXISTS` never alters a table that is already there. `BufferStore.migrate`
brings an existing buffer to `SCHEMA_VERSION` by probing the table's real shape (not trusting
`PRAGMA user_version` alone): a buffer from before the object graph still has a
`transactions.mapped_json NOT NULL` column, which failed every ingest INSERT, so it is dropped.
Rows that predate the graph have no body; `GraphBackfill` rebuilds their graph **and** dimensions
from `raw_x12` at startup, before the pollers and routes open, so `take` never drains a row whose
content is missing. A row that still has no graph afterwards (a guide with no structure index) is
legitimately envelope-only; one whose raw fails to parse is logged and skipped.

### 8.2 Leases and drain

Drain/lease SQL is as in hl7/v2 §8: `take` atomically marks a batch `in_flight` under a lease with
a TTL (default `PT5M`, at most `PT1H` — `BufferStore.MAX_LEASE_TTL`), `ack` finalizes all or a
subset of it, `release` returns it early, and an expired lease reverts to `new`. The buffer
forgets a lease once its rows leave `in_flight`, so `ack`/`release` report the rows affected and
0 is the answer for an unknown, finalized or expired lease (§2.5).

### 8.3 Retention, backpressure and deletes

The retention sweeper evicts acked rows only, past `retention.maxAge` and/or `retention.maxBytes`
(either axis omitted = unbounded on it); `files` rows are never evicted — they are the audit
trail. Backpressure is as in hl7/v2 §8 (§4.2 step 4), with these x12 specifics:

- **Deletes are batched and atomic with the graph.** `purge` and both retention axes delete
  in batches of 500 acked rows (oldest `acked_at` first); each batch is ONE SQL transaction
  that removes the rows *and* their `entities`, `entity_values` and `transaction_dims`, so a
  failure can never orphan a graph, and a batch never binds more parameters than SQLite allows
  however many rows are due. The store's lock is released between batches.
- **Capacity is live data.** `retention.maxBytes` and backpressure compare
  `(page_count − freelist_count) × page_size` (`usedBytes`), not the file size: pages a delete
  freed are room at once. `/stats` `dbSizeBytes` and `/healthz` `db.sizeBytes` stay the file size.
- **Every delete path vacuums.** `purge`, the `maxAge` pass and the `maxBytes` loop are each
  followed by `PRAGMA incremental_vacuum(n)` run to completion (run with `execute()` the pragma
  steps once and frees a single page), so the file shrinks as well.

### 8.4 The object graph

A document is not queryable, so the typed document is **not stored**. There is no
`mapped_json` column: every materialized **instance** is a row, and the document is
reassembled on demand. One representation, nothing to keep in sync.

```
entities       one row per loop / segment / composite instance
               (element_key, file_id, gs08, schema_id, xid, kind, parent_id, property,
                path, ordinal, property_order)
entity_values  one row per scalar field
               (entity_id, seq, property, data_type, value_text, value_num, value_date)
```

`schema_id` is the **real** pack schema — `schema:type:x12.005010X221A1.CLP`, the same id
`getSchema` serves — and `parent_id` is the edge, so a transaction set is a graph of
addressable objects. `path` (`detail[0].loop2000[0].loop2100[1].clp`) identifies an instance
uniquely within its transaction set.

Three rules the tests pin down:

- **`value_text` is the value; `value_num` is only a comparison key.** Amounts are stored as
  exact integer **micro-units** (×10⁶, half-up) so range filters and `ORDER BY` are integer
  comparisons, and reassembly reads the lexical form — a decimal must not round-trip through a
  float (`450.00` came back `450.0` when it did). Dates go to `value_date` as epoch-millis.
- **Wire order is data.** Field order is part of the contract (§5) and a composite is a child
  row rather than a value, so each instance stores its `property_order` and each value its
  `seq`. Without them a reassembled segment lists every scalar before every composite.
- **The graph commits with its transaction.** One SQL transaction covers the transaction rows,
  the graph and the dimensions; the `.done` rename still happens only after it returns. A
  committed row whose entities were missing would be queryable-but-empty. Purge and retention
  delete graph rows with their transaction, since SQLite enforces no foreign key unless the
  pragma is on.

`EntityGraph.assemble` walks the rows back into the nested form, and **every read goes through
it**: the structural collections, `ops/take`, `ops/validate` and the business projection. `BufferStore`
exposes `documentFor(elementKey)` and a batched `documentsFor(keys)` that reads a whole page in
two queries rather than two per row. The round-trip equality test against the materializer is
what licenses this: if it ever fails, the rows are no longer a faithful substitute.

`ops/recast` replaces the graph (`replaceGraph`) instead of rewriting a column — one
transaction, and a leased row is never rewritten under its consumer. Dimensions survive a
recast: they describe the interchange, not the materialization.

The envelope is **overlaid at read time** by `X12ProducerFacade.toElement`, never stored in the
body — so a body cannot contradict the envelope, and there is one place that decides which
wins.

### 8.5 Business entities

The graph is X12-shaped: `loop2100` whose `clp` child has a `clp04`. A **mapping** says what
that *is* — a Claim whose `paidAmount` is `clp.clp04` — and at what **grain**: one row per
instance of the declared `anchor` schema. Choosing the anchor chooses the grain, which is why
it is declared, never inferred.

| Guide | Entity → collection | Anchor = grain | Rows |
|---|---|---|---|
| 835 X221A1 | `Remittance` → `/remittances` | the transaction root | 1 per transaction set |
| 835 X221A1 | `Claim` → `/claims` | `loop2100` (CLP) | n claims |
| 835 X221A1 | `ServiceLine` → `/service-lines` | `loop2110` (SVC) | n lines per claim |
| 837P X222A1 | `Claim` → `/professional-claims` | `loop2300` (CLM) | n claims |
| 837P X222A1 | `ServiceLine` → `/professional-service-lines` | `loop2400` (LX/SV1) | n lines per claim |
| 837I X223A2 | `Claim` → `/institutional-claims` | `loop2300` (CLM) | n claims |
| 837I X223A2 | `ServiceLine` → `/institutional-service-lines` | `loop2400` (LX/SV2) | n lines per claim |
| 835 + 837P + 837I | `Payer` → `/payers` | *dimension grain* (§8.5.2) | 1 per distinct payer |
| 835 | `Payee` → `/payees` | *dimension grain* (§8.5.2) | 1 per distinct payee |

**Why 837 claims are not in `/claims`.** An 835 CLP is an *adjudicated* claim (paid amount,
status, patient responsibility); an 837 CLM is a *submitted* one (charge, place of service,
diagnosis). One `/claims` spanning both would need a union schema in which most columns are
null for half the rows, and a collection has exactly one `collectionSchema` (§2.1) that must
describe every row honestly. So each guide's grain gets its own collection and schema
(`schema:business:x12.837P.Claim`), and `claimId` — CLM01, which the 835 echoes in CLP01 — is the
join between them. An anchor-grain collection belongs to exactly one guide; an aliased GS08
(`005010X223A1`, `005010X223`, `005010X222`) resolves to its canonical guide's mapping, because
it is materialized with that guide's structure and carries its schema ids.

Column paths are relative to the anchor and may carry a **qualifier predicate**, which is how
the semantics X12 hides in code positions become names: `nm1[nm101=QC].nm103` is the patient's
last name, `amt[amt01=AU].amt02` the allowed amount, `svc.svc01.c00302` the procedure inside
composite C003. A step `^property` climbs to the nearest **ancestor** under that property, so a
row can read what its grain inherits, exactly and per instance: an 837 claim's subscriber is
`^loop2000B.loop2010BA.nm1.nm109` (whether the claim sits under 2000B or 2000C), a service
line's claim id `^loop2300.clm.clm01`. A column the transaction lacks is present and null, so
every row of a collection has one shape.

Values are projected from `value_text`, never from the comparison key (§8.4): a decimal is a
`BigDecimal` parsed from the lexical form, so `300.00` reaches the wire as `300.00`. A `date`
column is ISO `YYYY-MM-DD`; an `AN` date element (`DTP03`, `DMG02`) carrying `CCYYMMDD` is
rewritten, and a column may take one end of an `RD8` range with `"part": "from" | "to"`
(a lone `D8` date is both ends). The generated schemas declare `decimal` as a JSON number and
`date` as an ISO string, matching the guide schemas' core types.

**Dimensions** are transaction-level values (payer, payee, check number, effective date)
resolved once per transaction set into `transaction_dims` and merged onto every row anchored
under it. The payer lives in `N1*PR` up in the header, so without that "claims for this payer"
would walk up the graph per claim instead of hitting an index. A dimension path is followed
along **every** matching branch, and the dimension exists only when all of them agree: an 835
has one `N1*PR`, but an 837 batch can hold several billing providers (2000A) and payers (2010BB
under each 2000B). Such a transaction has no `payerName` dimension — it is in no payer segment
and not counted in `/payers` — rather than being attributed to whichever payer came first. The
per-claim value is always available as a column (`claimPayerName`, `claimPayerId`), which is
exact. 837 dimensions: `submitterName/Id` (1000A), `receiverName/Id` (1000B),
`billingProviderName/Npi` (2010AA), `payerName/Id` (2010BB).

Business element schemas are **generated from the mappings** (`schema:business:x12.835.Claim`)
and registered at boot: a collection may not advertise a `collectionSchema` the registry
cannot serve, and generating it means the schema and the projection can never disagree about
what a Claim has. Every row also carries `elementKey` and `fileId`, so any business row traces
back to the interchange that delivered it.

Mappings are **content, not code** — `mappings/<GS08>.json`, shipped in the pack format (§7) —
so a trading-partner variant or a new entity is a mapping file, not a module release. A guide
with no mapping simply has no business entities.

#### 8.5.1 Collections, segments and filters

```
/x12-receiver
├─ /claims                            collection, Claim schema, one element per CLP loop
│   ├─ /claims/by-file/<fileId>       "claims from this file"
│   └─ /claims/by-payerName/<value>   "claims for this payer"  (also by-payeeNpi, by-check…)
├─ /service-lines  …
├─ /remittances    …
├─ /professional-claims · /professional-service-lines    (837P; by-billingProviderNpi, by-payerId…)
├─ /institutional-claims · /institutional-service-lines  (837I)
└─ /payers · /payees                  dimension grain, no segments
```

Segment children are **emergent**, exactly like `/by-type`: `SELECT DISTINCT` over the
dimensions actually present, so a payer node appears the first time that payer sends something.
Dimensions are named alike across guides (both an 835 and an 837 have `payerName`), so the
values offered under a collection are narrowed to those that scope at least one of **its own**
rows — a payer that only ever sent 837s is not a segment of the 835's `/claims`. An unknown
segment value is a 404, not an empty page.

A **structural** filter (`/transactions`, `/by-type/<TS>/<GS08>`, `ops/take`) compiles to SQL
through `X12SqlAdapter`, and a body path resolves into the graph: `(loop2100.clp.clp04>1000)`
becomes a scalar subquery for the `clp04` of a `clp` instance in that transaction set — the
FIRST matching instance in wire order, not "any". A filter that needs per-instance semantics ("every claim over 1000", not
"a transaction whose first claim is") belongs on a business collection, where the grain IS the
row. Numeric comparisons read `value_num / 1000000.0`; the exact value stays in `value_text`.

Scoping is pushed into SQL — grain always, plus a file or dimension equality inside a segment.
**One parser for both paths.** A business filter is parsed by lite-filter — the same parser
the structural path uses — and evaluated with lite-filter's own evaluator against the projected
row, so the two surfaces cannot drift in what they accept; the extensions
(`:contains:`, `:startsWith:`, `:endsWith:`) work on a claims collection exactly as on
`/transactions`. The only thing layered on top is attribute validation, because the library
cannot know which columns a business entity has: an unknown name is a 400 that lists what IS
filterable, rather than an empty page that looks like "no matches". lite-filter rejects some
operands only while evaluating a row (a `:between:` without two numeric bounds); the predicate
raises that as the same 400, so it never surfaces as a 500 halfway through a page.

The structural path compiles that expression to SQL; the business path evaluates it in memory,
because a business column can sit behind a qualifier predicate or inside a composite, which SQL
over `entity_values` cannot express. A filtered page therefore reads the scoped set and pages
after filtering.

**Sorting** works on both paths. `sortBy` resolves through the same property mapping the
filter uses — an envelope column or a graph lookup for a body path on the structural side, a
declared column or dimension on the business side — so a sort and a filter can never disagree
about what a property means. The structural path emits `ORDER BY` in SQL (built by the adapter,
never from the caller's raw string); the business path sorts the projected rows by the column's
declared type, so an amount orders numerically and a date as an ISO string. **NULLs sort last
in both directions**, so a page is never led by rows missing the field it was sorted on, and an
unknown attribute or direction is a 400 rather than an arbitrary order. A business collection
serializes nulls, because its schema promises a shape: a column the transaction lacks is
present-and-null, not absent. Correct, and bounded by
the segment rather than the buffer — pushing the compilable subset down is a follow-up, and the
value indexes are already in place for it. Filters compare by the column's declared type, so
`(paidAmount>=1000)` is an exact decimal comparison and cannot match `999.99` lexically, and an
unknown column name is a 400 rather than a silently empty result.

#### 8.5.2 Dimension-grain entities: payers and payees

A payer is not an instance of any loop: it is a value the transactions *name*. A mapping entity
may therefore declare `"grain": "dimension"` with an `identity` — the pair of dimensions that
name a party (`{"name": "payerName", "id": "payerId"}`) — and columns whose `path` is one of the
derived fields `key`, `name`, `id`, `transactionCount`, `firstSeen`, `lastSeen`,
`transactionTypes`. Its schema is generated from those columns like any other
(`schema:business:x12.Payer`; no `elementKey`/`fileId`, since a party spans transactions), and
filter, sort and paging go through the same `BusinessFilter` / typed comparator. Every guide
that names payers declares the same entity; declarations of one collection are **merged** into
one entity spanning those guides (they must agree on schema, identity and columns, or the later
one is dropped with a warning — one collection, one schema).

Rows come from one grouped query over `transactions ⋈ transaction_dims` (distinct name/id pairs
per guide with count and first/last `received_at`), folded into parties by the **identity rule**:

1. An occurrence with an identifier is keyed by it: `id:<payerId>`. An identifier is stable
   where a name is not.
2. An occurrence with only a name joins the identified party carrying that same name (trimmed,
   whitespace-collapsed, case-insensitive) **when exactly one exists** — an 835 `N1*PR` often
   omits `N104` while the 837 `NM1*PR` for the same payer states `NM109`, and they are one payer.
3. Otherwise (no identified match, or several — the name alone cannot say which) it is its own
   party keyed `name:<NORMALIZED NAME>`. A name-only occurrence is never guessed onto a party
   whose identifier it did not state.

A party's `payerName` is the name most of its transactions carry (ties to the lexicographically
first). `transactionCount`, `firstSeen`/`lastSeen` (receipt time, ISO) and `transactionTypes`
(`835,837I,837P`) are exact over the transaction sets currently in the buffer, so they shrink
when retention sweeps acked rows. The same machinery serves `/payees` (835 `N1*PE`, keyed by
`payeeNpi`). The list is computed per request — one grouped scan in SQL — and filtered, sorted and
paged in memory, which is bounded by the number of distinct parties, not transactions.

## 9. Health

`bufferDepth` means one thing everywhere — `/healthz`, `/stats` and the connection metadata: the
**un-acked** backlog, `new` + `in_flight` (`BufferStore.unackedCount`). Acked rows are only
waiting for retention; counting them made a fully drained receiver look backed up.

`/healthz` → `{poller: {up, lastScan, lastConsumed, bufferDepth, oldestUnackedSec, backpressure,
sources[]: {name, path, writable, pending, errored, failing, stalled, lastScan?, lastScanStarted?,
lastError?, lastErrorAt?}}, db: {walBytes, sizeBytes}}`; 503 when the poller thread is dead,
under backpressure, or when any source is:

- **unwritable**;
- **failing** — its most recent scan failed (the buffer rejected the work, §4.2 "Isolation", or
  the directory could not be listed) and no scan has completed since. `lastError`/`lastErrorAt`
  say what and when, and stay visible after recovery;
- **stalled** — no scan has completed and no file has finished for `3 × pollIntervalSec`
  (at least 120 s), measured from the later of `lastScan` and the running scan's last finished
  file (so a long catch-up scan over a backlog is not a stall), or from poller start before the
  first scan completes.

A live thread is not proof of ingestion: a buffer whose every INSERT failed (the
`mapped_json NOT NULL` upgrade bug) kept `up=true` and a green probe while nothing moved.
`lastScan` is the last scan that *completed*, never merely started. `api.yml`'s `HealthStatus`
declares exactly these fields, the always-emitted ones required.

`/stats` (document, `schema:shared:x12.receiver-stats`) → `{up, lastScan?, lastConsumed?,
bufferDepth, oldestUnackedSec?, backpressure, newCount, inFlightCount, ackedCount, fileCount,
doneFileCount, oldestDoneFileAgeSec?, walBytes, dbSizeBytes, sources[]: {name, path, writable,
pending, errored}}`. The schema declares exactly what is emitted: the always-emitted fields are
required, the `?` ones are omitted when there is nothing to report. `doneFileCount` and the age
of the oldest `.done` file make inbox hygiene visible (§11, question 2). The connection metadata
(`GET /connections/{id}/metadata`) is `{status: On|Error, bufferDepth}` — `On` while `/healthz`
is healthy.

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

## 12. Test fixtures and the x12.org examples

x12.org's examples are ASC X12 intellectual property — reproduction requires their consent;
linking is permitted (`https://x12.org/examples/disclaimers`). Therefore:
- We do not fetch, store, commit or test against them — no scraper, no conformance run over
  them: bulk-copying the examples to disk and CI is itself reproduction, and the synthetic
  fixtures cover the same guides. Cite an example by link when one explains a structure.
- Committed fixtures under `java/src/test/resources/fixtures/` are **synthetic** (fictional
  payer/provider, `TEST-NET` style identifiers), one per guide, authored from scratch, plus
  deliberately malformed files for the `.error` path.
