# `@zerobias-org/module-x12-x12` — X12 EDI File Receiver

An **always-on X12 EDI receiver** exposed through the standard **DataProducer** interface.
Payers, clearinghouses and billing systems drop X12 interchange files (837 claims, 835
remittances, 277CA acknowledgments, 999s …) into a mounted directory; the module polls it,
parses every transaction set with [`imsweb/x12-parser`](https://github.com/imsweb/x12-parser),
persists them to a durable buffer, marks the file `.done`, and offers files and transactions to
the Hub pipeline through the same container/collection/function model every other DataProducer
uses — plus raw file download.

> Design canon: [`DESIGN.md`](DESIGN.md) · Working on the code? [`CLAUDE.md`](CLAUDE.md) ·
> Structural corollary: [`../../hl7/v2`](../../hl7/v2) (the HL7 v2 MLLP receiver this module mirrors) ·
> Interface canon: [`../../interface/dataproducer/documentation/`](../../interface/dataproducer/documentation/).

## Core purpose

Hospital revenue-cycle data does not arrive over APIs. Claims go out and remittances come back
as **X12 005010 files**, dropped daily by a clearinghouse or a payer. This module is the
ZeroBias ingestion endpoint for those files. Its one job:

1. **Watch** one or more inbox directories on a mounted volume (poll; no sockets).
2. **Never consume a partial file** — a file is read only after its size and mtime have been
   stable for a configured window.
3. **Parse** each file into its transaction sets (ISA → GS → ST…SE) and **materialize** them to
   clean, typed JSON keyed by the implementation guide (`005010X221A1` → `loop2100[].clp.clp04`
   is a decimal, dates are ISO), against schemas generated at build time from the parser's own
   guide definitions.
4. **Never lose one** — persist to the SQLite buffer and only then rename the file `.done`
   (the rename *is* the acknowledgment). Unparseable files become `.error` and are never
   retried silently.
5. **Offer** everything through the DataProducer surface: one node per file (browsable *and*
   downloadable), collections by transaction type / guide version / sender / source directory,
   and the `take`/`ack` lease-drain functions the collectorbot uses.

What it is **not**: it is not a mapper into AuditgraphDB (that is the collectorbot), it does not
send anything back (no 997/999/TA1 generation), and it does not fetch files from anywhere —
getting the file onto the volume is the feed's job.

## Architecture

```
single container (uid/gid 10001)
├── nginx                      0.0.0.0:8888  → 127.0.0.1:8889   (operations, Hub-facing)
└── java -jar x12-receiver.jar
      ├── DataProducer HTTP     127.0.0.1:8889                   (browse / drain / schemas / /healthz)
      ├── inbox poller          one thread per configured source (poll → parse → buffer → rename)
      ├── retention sweeper     evicts acked rows past retention.maxAge / maxBytes
      └── SQLite buffer (WAL)   /var/lib/module/buffer.db        (durable, single connection)
```

There are **no listener ports**: files arrive on a volume, not a socket. Only the operations
port (8888) is exposed.

## Deployment

A daemon module: its deployment shape is declared in [`runtimeConfig.yml`](runtimeConfig.yml)
(read by the dataloader at publish time) and carried on `EnsureDeployment`:

```yaml
daemonMode: true
durability:
  - { volumeName: x12-buffer, mountPath: /var/lib/module,    access: rw }   # buffer.db
  - { volumeName: x12-inbox,  mountPath: /var/lib/x12/inbox, access: rw }   # the drop directory
resources:
  memoryMb: 1024
config:                     # opaque to the platform; delivered as MODULE_CONFIG — see Configuration
  sources: [ { name: inbox, path: /var/lib/x12/inbox, … } ]
```

- **`x12-buffer`** holds the SQLite buffer (`buffer.db` plus its `-wal`/`-shm` files). It must
  survive re-deploys; the node mounts it but does not cap its size — retention is module-owned.
- **`x12-inbox`** is where the clearinghouse / payer feed (or an operator's SFTP landing job)
  writes files. Operators usually bind it to a **host path**; a named volume works the same.
  More sources can watch subdirectories of it (or of another mount) — each `sources[].path` is a
  path inside the container.
- **The container runs as uid/gid 10001**, never root. A named volume picks up that ownership
  when first created; a **host path must be readable, writable and renameable by uid 10001**
  (e.g. `chown 10001:10001 <dir>`, or group 10001 with `g+rwx`), because the module renames each
  file in place to `.done` or `.error`. The files dropped into it must be readable by 10001.
- **A read-only or missing inbox is a boot failure**, not a degraded mode: the module proves
  each source is renameable at start (create a probe file, rename it with `consumedSuffix` and
  then `errorSuffix`, delete it) and exits 1 with `path is not writable/renameable` otherwise. A daemon that cannot mark files consumed must not
  run.
- Feeds should write a file under a temporary name (`.tmp`, `.part`, `.partial`, or a dotfile)
  and rename it into place when complete; those names are never picked up. The stability window
  (`stableForSec`) covers writers that cannot.
- The module **never deletes** inbox files. `.done` and `.error` files accumulate — clearing them
  is the operator's or the feed's job; `/stats` reports how many `.done` files remain and the age
  of the oldest.

## Configuration

Everything daemon-level lives in the `config:` block of `runtimeConfig.yml`, delivered to the
container as `MODULE_CONFIG`. Without `MODULE_CONFIG` (a bare `docker run`) the module reads the
node-delivered `RUNTIME_CONFIG_FILE`, then the `runtimeConfig.yml` baked into the image; the
built-in defaults apply only when no config is present at all.

| Key | Shipped value | When omitted | Validation |
|---|---|---|---|
| `sources` | one source, `inbox` | one source `inbox` at `/var/lib/x12/inbox` | non-empty array |
| `sources[].name` | `inbox` | required | non-blank; unique — becomes `/by-source/<name>` and `sourceName` |
| `sources[].path` | `/var/lib/x12/inbox` | required | directory inside the container, renameable by uid 10001; not the same as, or nested with, another source |
| `sources[].pattern` | `*.{x12,edi,txt,835,837,277,999,dat}` | `*` | valid glob, matched case-insensitively against the file name |
| `sources[].pollIntervalSec` | `30` | `30` | whole number > 0 |
| `sources[].stableForSec` | `60` | `60` | whole number ≥ 0; size and mtime must hold this long before a file is read |
| `consumedSuffix` | `.done` | `.done` | non-blank, no `/` or `\`, different from `errorSuffix` |
| `errorSuffix` | `.error` | `.error` | as above |
| `ackDurability` | `full` | `full` | `full` (fsync every commit, so `.done` only after the rows are on disk) or `normal` (faster; a power loss can drop rows of files already renamed `.done`) |
| `maxFileBytes` | `67108864` (64 MiB) | `67108864` | whole number, 1 … 134217728 (128 MiB); a larger file goes to `.error` as `too-large` without being loaded |
| `retention.maxBytes` | `10737418240` (10 GiB) | unbounded | positive whole number; live buffer data ceiling |
| `retention.maxAge` | `P90D` | unbounded | positive ISO-8601 duration; acked rows older than this are evicted |
| `allowBareTransactionSets` | not set | `false` | boolean; accept files that start at `ST` (the envelope is synthesized from `ST03`) |

Retention evicts **acked** transaction rows only; un-acked rows and the per-file audit rows are
never evicted. With `retention` omitted there is no sweeper and no backpressure.

**Fail fast.** A config that is present but wrong — invalid JSON, an unknown key at any level
(a typo such as `retension` included), a wrong type or out-of-range value, a source without
`name` or `path`, duplicate names, overlapping directories — stops the boot with exit 1 and a
log line naming the problem. The module never falls back to defaults over an operator's config.

## Authentication

There are no credentials to configure: the module dials nothing. The
[connection profile](connectionProfile.yml) is informational (`x12Version`, `ackDurability`) and
the daemon never reads it; `connect` is the DataProducer handshake the pipeline makes before
browsing or draining. Access to the operations port is the Hub Node's; access to the inbox is
the host filesystem's.

## Usage

### Object tree

```
/x12-receiver
├── /files                           one node per interchange file  ["container","document","binary"]
│   └── /files/<fileId>/transactions that file's transaction sets
├── /transactions                    every transaction set (envelope schema)
├── /by-type/<TS>                    container per type: 835 | 837P | 837I | 277CA | 277 | 999 | 834
│   └── /by-type/<TS>/<GS08>         collection per guide, typed by the guide's table schema
├── /by-version/<GS08>               e.g. /by-version/005010X221A1
├── /by-sender/<ISA06>
├── /by-source/<source name>         one per watched directory
├── /stats                           document — poller + buffer metrics
└── /ops                             take · ack · release · replay · recast · purge · raw · validate · rescan
```

### Browsing the tree

- Nodes appear the first time matching data lands (`/by-type/835` exists once an 835 has
  arrived). `/by-type/<TS>` is always a container; its children `/by-type/<TS>/<GS08>` are the
  typed collections.
- A **file node** is three things: a folder (its `transactions` collection), a document
  (`getDocumentData` → `fileId`, `filePath`, `currentPath`, `status` consumed|error|duplicate,
  `isaCount`, `transactionCount`, `errorMessage`, `redeliveryCount`, …) and a binary
  (`downloadBinary` → the raw EDI, served from its `.done` name). `fileId` is
  `<path at discovery>@<first 12 hex of the sha256>`; in object ids `/` is written `%2F` —
  always use the `id` the tree returns.
- A **transaction set** is a collection element keyed `<fileId>:<ISA13>:<GS06>:<ST02>`: the
  envelope (`elementKey`, `fileId`, `sourceName`, `isaControlNumber`, `gsControlNumber`,
  `stControlNumber`, `gs08`, `transactionType`, `senderId`, `receiverId`, `interchangeDate`,
  `receivedAt`, `status`, `leaseId`, `envelope`, `parserErrorCount`) plus the typed body
  (`st`, `header`, `detail[]`, …). Money is decimal, dates ISO, control numbers strings.
- Pages are newest first: `pageNumber` (from 1) and `pageSize` (1–1000, default 100); sorting,
  `pageToken` and property projection are rejected with 400, never ignored.
- Browsing is **read-only**; it never leases or consumes anything.

### Filtering

Collections accept **RFC4515** filters. Attribute names are schema property names — envelope
fields, or dotted paths into the typed body — not X12 positions:

```
(&(transactionType=835)(senderId=EXAMPLEPAYER)(status=new))
(|(transactionType=837P)(transactionType=837I))
(loop2100.clp.clp02=1)            # 835 claims paid as primary
(receivedAt>=2026-09-01)          # date-only literals
(receivedAt:withinDays:1)
```

`getCollectionElements` and `searchCollectionElements` both honour `filter`; so do `take`,
`replay` and `recast`. A malformed filter is a 400.

### Draining

Consumption is the lease cycle, **at-least-once**:

1. `ops/take {filter?, max?, leaseTtl?}` leases up to `max` (default 100, cap 1000) drainable
   transactions — `new`, or `in_flight` past their lease TTL — oldest first, marks them
   `in_flight` and returns `{leaseId, transactions[], remaining}` (`leaseId` null when nothing
   was drainable).
2. Process them, then `ops/ack {leaseId, elementKeys?}` — the whole lease, or a subset.
3. Or give them back early: `ops/release {leaseId, elementKeys?}`. A lease not acked within its
   TTL (default `PT5M`, capped at `PT1H`; `validateFunctionInput` warns for a longer one) reverts by itself, so a consumer that crashes loses
   nothing; the rows are leased again by the next `take`.
4. `ops/replay {filter?}` forces `in_flight` rows back to `new`; `ops/recast {filter?, max?}`
   re-materializes stored rows under the current definitions; `ops/purge {olderThan?}` deletes
   acked rows now instead of waiting for retention.

`ack`/`release` of a lease that no `in_flight` row carries — unknown, already finalized, or
expired and re-leased — is a 404. Every function input is checked against its declared schema
before anything runs: an unknown or misspelled key is a 400, never a silently ignored argument.

## Operations

RPC names on the wire (`POST /connections/{id}/{ApiClass.method}` with `{"argMap": {…}}`) are
the interface operationIds, matched exactly:

| API | Operations |
|---|---|
| `ObjectsApi` | `getRootObject`, `getObject`, `getChildren`, `searchChildObjects` (`scope: one_level` only) |
| `CollectionsApi` | `getCollectionElements`, `searchCollectionElements`, `getCollectionElement` |
| `DocumentsApi` | `getDocumentData` (`/stats`, file nodes) |
| `BinaryApi` | `downloadBinary` (file nodes) |
| `FunctionsApi` | `invokeFunction`, `validateFunctionInput` |
| `SchemasApi` | `getSchema` |

| Function (`/x12-receiver/ops/<fn>`) | Input | Output |
|---|---|---|
| `take` | `{filter?, max?, leaseTtl?}` | `{leaseId, transactions[], remaining}` |
| `ack` / `release` | `{leaseId, elementKeys?}` | `{acked}` / `{released}` |
| `replay` | `{filter?}` | `{replayed}` |
| `recast` | `{filter?, max?}` | `{examined, recast, unchanged, failed}` |
| `purge` | `{olderThan?}` | `{purged}` |
| `raw` | `{elementKey}` | `{elementKey, fileId, gs08, transactionType, raw}` — ST..SE verbatim + ISA/GS context |
| `validate` | `{elementKey}` | stored vs re-materialized verdicts, `repsAgree`, parser errors |
| `rescan` | `{source?}` | `{scanned, discovered, consumed, errored}` — poll now instead of waiting |

The producer is **receive-only**: every write operation (`createChildObject`,
`addCollectionElement`, `updateDocumentData`, …) is a 400. Errors use the platform
`errorModelBase` body (`{key, template, timestamp, statusCode, …}`); a 500 never carries the
cause, which is logged instead. Full detail: [DESIGN.md §2](DESIGN.md#2-dataproducer-surface).

## Examples

Against a local container started by `java/scripts/e2e-local.sh` (or any container run with
`HUB_NODE_INSECURE=true` and port 8888 published as 18888). In production the Hub makes the
same calls through the Hub Node.

```bash
API=http://localhost:18888
curl -sS -X POST "$API/connections" -H 'content-type: application/json' -d '{"connectionId":"dev"}'
rpc() { curl -sS -X POST "$API/connections/dev/$1" -H 'content-type: application/json' -d "{\"argMap\":$2}"; }

# Browse: files, then one guide's typed collection, filtered
rpc ObjectsApi.getChildren '{"objectId":"/x12-receiver/files"}'
rpc CollectionsApi.getCollectionElements \
  '{"objectId":"/x12-receiver/by-type/835/005010X221A1","filter":"(loop2100.clp.clp02=1)","pageSize":50}'

# Drain: lease, process, acknowledge
rpc FunctionsApi.invokeFunction '{"objectId":"/x12-receiver/ops/take","requestBody":{"max":50}}'
rpc FunctionsApi.invokeFunction '{"objectId":"/x12-receiver/ops/ack","requestBody":{"leaseId":"<leaseId from take>"}}'

# Download a file's raw EDI (use the node id /files returned)
rpc BinaryApi.downloadBinary '{"objectId":"<file node id>"}' > interchange.x12
```

A failed call returns the error body with its HTTP status, e.g. an `ack` of a finalized lease:

```json
{ "key": "err.no.such.object", "template": "Lease not found: 9253fed6-…", "timestamp": "…",
  "statusCode": 404, "type": "lease", "id": "9253fed6-…" }
```

## Health

`GET /healthz` on the operations port; the Hub Node polls it every 30s and raises a Node alert
on failure.

```json
{ "poller": { "up": true, "lastScan": "…", "lastConsumed": "…", "bufferDepth": 142,
              "oldestUnackedSec": 3, "backpressure": false,
              "sources": [ { "name": "inbox", "path": "/var/lib/x12/inbox", "writable": true,
                             "pending": 0, "errored": 1, "stalled": false, "consecutiveFailures": 0,
                             "lastScanStarted": "…", "lastScanCompleted": "…" } ] },
  "db": { "walBytes": 12345, "sizeBytes": 4096000 } }
```

`bufferDepth` is the un-acked backlog (`new` + `in_flight`); `errored` counts the source's
`error` file rows; `pending` counts candidates the last scan left for later (unstable,
unreadable or held back).

**`200` healthy, `503` degraded** — when a poller thread has died, the buffer is over
`retention.maxBytes` and files are being left untouched (**backpressure**), or any source is
unwritable, has failed 3 scans in a row, or is **stalled**: no scan completed and no file
finished for 3 × `pollIntervalSec` + 60 s (a scan wedged on a hung mount keeps the thread alive
while nothing moves). `/x12-receiver/stats` carries the same figures plus per-status counts,
`fileCount`, `doneFileCount`, `oldestDoneFileAgeSec` and `dbSizeBytes`.

## Sizing

- **Memory**: `resources.memoryMb` (1024 by default) is the container limit; the JVM heap
  follows it (`-XX:MaxRAMPercentage=70`, about 700 MB). A file up to `maxFileBytes` is read into
  memory whole and parsing needs several times its size in heap, so **raise `memoryMb` together
  with `maxFileBytes`**. A file whose bytes do not fit in the heap goes to `.error` as
  `too-large-for-heap` (hashed as a stream, so it still gets an identity), and one that exhausts
  the heap while being parsed goes there as `internal: …OutOfMemoryError`; neither is retried
  automatically. `maxFileBytes` stops at 128 MiB: one transaction set's typed JSON runs 3–4½× its
  X12, and SQLite stores no single value over 1,000,000,000 bytes (a row it refuses sends the file
  to `.error` as `buffer-rejected`).
- **Buffer volume**: size it for `retention.maxBytes` plus the un-acked backlog you expect
  between drains plus the per-file audit rows (never evicted). Over `maxBytes` the module sweeps
  and, if still over, stops picking up files (backpressure) until a drain and the sweeper make
  room — the inbox is the queue.
- **Inbox volume**: every consumed file stays as `.done` until something removes it.

## Supported guides

| GS08 | Transaction | Accepted wire spellings |
|---|---|---|
| `005010X221A1` | 835 Payment/Remittance Advice | `005010X221A1`, `005010X221` |
| `005010X222A1` | 837P Professional Claim | `005010X222A1`, `005010X222` |
| `005010X223A2` | 837I Institutional Claim | `005010X223A2`, `005010X223A1`, `005010X223` |
| `005010X214` | 277CA Claim Acknowledgment | `005010X214` |
| `005010X212` | 277 Claim Status Response | `005010X212` |
| `005010X231A1` | 999 Implementation Acknowledgment | `005010X231A1`, `005010X231` |
| `005010X220A1` | 834 Benefit Enrollment | `005010X220A1`, `005010X220` |

The list is [`guides.txt`](java/src/main/resources/com/zerobias/module/x12/parser/guides.txt),
read by the parser at runtime and by the schema generator at build time. Each functional group
is parsed with the guide its own GS08 names, so one interchange may mix guides. **Not
supported** (no imsweb 005010 definition): 270/271 (X279), 837D (X224), 820 (X218) — such files
go to `.error` as `unsupported-guide`. See [DESIGN.md §6](DESIGN.md#6-schema-content--build-time-generation).

## Testing

```bash
(cd java && mvn verify)                           # JUnit suites (receiver + schema codegen); needs GitHub Packages read access for lite-filter
READ_TOKEN=$(gh auth token) java/scripts/e2e-local.sh
                                                  # real container: file drop → .done → browse → take/ack/purge → download,
                                                  # plus multi-interchange, symlink and fail-fast boot checks
zbb --slot <slot> testDocker                      # test/e2e: fixtures docker cp'd into the real container's inbox,
                                                  # then the whole DataProducer surface through the hub-sdk client
cd <repo-root>/package/x12/x12 && zbb --slot <slot> gate
```

Maven resolves `lite-filter` from GitHub Packages: `~/.m2/settings.xml` needs server id
`github` with `${env.GITHUB_ACTOR}` / `${env.READ_TOKEN}`, and `READ_TOKEN` a token with
`read:packages` (see [`CLAUDE.md`](CLAUDE.md)). `e2e-local.sh` needs Docker and refuses to run
without `READ_TOKEN` (or `GITHUB_TOKEN`).

Test fixtures under `java/src/test/resources/fixtures/` are synthetic — fictional parties and
identifiers, one file each for 835, 837P, 837I, 277CA and 999, plus malformed files for the
`.error` path. The ASC X12 published
examples are X12 intellectual property and are not reproduced here.
