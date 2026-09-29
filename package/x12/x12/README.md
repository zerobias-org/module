# `@zerobias-org/module-x12-x12` — X12 EDI File Receiver

An **always-on X12 EDI receiver** exposed through the standard **DataProducer** interface.
Payers, clearinghouses and billing systems drop X12 interchange files (837 claims, 835
remittances, 277CA acknowledgments, 999s …) into a mounted directory; the module polls it,
parses every transaction set with [`imsweb/x12-parser`](https://github.com/imsweb/x12-parser),
persists it to a durable SQLite buffer, renames the file `.done`, and offers files and
transactions to the Hub pipeline through the same container/collection/function model every
other DataProducer uses — plus raw file download.

> Design canon: [`DESIGN.md`](DESIGN.md) · Working on the code? [`CLAUDE.md`](CLAUDE.md) ·
> Structural correlary: [`../../hl7/v2`](../../hl7/v2) (the HL7 v2 MLLP receiver this module mirrors) ·
> Interface canon: [`../../interface/dataproducer/documentation/`](../../interface/dataproducer/documentation/).

## What it does

1. **Watches** one or more inbox directories on a mounted volume (polling; no sockets, no listener ports).
2. **Never consumes a partial file**: a file is read only after its size and mtime have been
   unchanged for `stableForSec`, and again unchanged while it is read.
3. **Parses** each file into its transaction sets (ISA → GS → ST…SE), each functional group with
   its own guide (GS08), and **materializes** them to typed JSON (decimals keep their scale, dates
   are ISO) against schemas generated at build time from the parser's guide definitions.
4. **Never loses one**: rows, object graph and dimensions commit in one SQLite transaction, and
   only then is the file renamed `.done` — the rename *is* the acknowledgment. A file that cannot
   be parsed is renamed `.error` with the reason on its files row.
5. **Offers** everything through the DataProducer surface (below) and the `take`/`ack` lease
   drain the collectorbot uses.

It is not a mapper into AuditgraphDB (that is the collectorbot), it sends nothing back (no
997/999/TA1 generation), and it fetches nothing — getting files onto the volume is the feed's job.

## Deployment

| Volume | Mount | Holds |
|--------|-------|-------|
| `x12-buffer` | `/var/lib/module` | `buffer.db` (+ WAL). Survives redeploys; the schema is migrated in place at boot and rows buffered before the object graph are rebuilt from their raw X12 before anything is served. |
| `x12-inbox` | `/var/lib/x12/inbox` | The drop directory. Usually bound to a host path the feed (or an SFTP landing job) writes into. |

- **Runs as uid `10001`.** The container starts as root only long enough to repair its
  volumes, then runs nginx and Java as `x12` (uid/gid `10001`), so X12 content is never parsed
  as root. The repair is idempotent and minimal: the buffer volume is `chown -R 10001` only if
  that uid cannot write it (volumes created by an older root image); the default inbox
  directory gets `chgrp 10001` + `g+rwx` only if needed. The inbox **owner is never changed**,
  so a host producer that owns the drop directory keeps writing. Other configured source paths
  are not repaired; the boot probe below reports them.
- **Permissions.** Every source directory must be readable, writable and renameable by uid
  `10001` (directly or via group `10001`): consuming a file is a rename to `.done`/`.error` in
  the same directory, which needs write permission on the directory, not ownership of the file.
  A host-path bind on a read-only or root-squashed mount can't be repaired and fails boot with a
  chgrp/chmod hint.
- **A source the process cannot rename in is a boot failure**, not a degraded receiver. At start
  each source is proven by creating a probe file and renaming it with both suffixes; a missing
  path, a non-directory, a read-only mount or a suffix the filesystem refuses stops the daemon
  (exit 1), as do two sources whose real paths are the same directory and duplicate source names.
- **Nested sources are fine**: each poller scans its own directory flat, never recursively.
- Symbolic links in a source are skipped and never followed (logged once per path).

## Configuration

Everything daemon-level is the opaque `config` block of `runtimeConfig.yml`, delivered to the
container as `MODULE_CONFIG` (JSON). Without `MODULE_CONFIG` the module reads the `config` block
of `RUNTIME_CONFIG_FILE` (if set), then the image's `/opt/module/runtimeConfig.yml`, then the
built-in defaults. A missing key takes its default.

**Fail-fast.** A config that is present but wrong stops the boot with exit 1: malformed JSON or
YAML, a `config` that is not an object, an unknown key (at any level), a value of the wrong type,
an out-of-range number, an unparseable duration or glob. Nothing is clamped or silently defaulted
— a typo'd `retention` would otherwise disable eviction without a trace.

| Key | Default | Validation |
|-----|---------|------------|
| `sources` | one source: `inbox` at `/var/lib/x12/inbox`, pattern `*.{x12,edi,txt,835,837,277,999,dat}` | Non-empty array of objects; names distinct |
| `sources[].name` | — (required) | Non-blank string; the `/by-source/<name>` node and every row's `sourceName` |
| `sources[].path` | — (required) | Non-blank string, a directory inside the container; proven renameable at boot |
| `sources[].pattern` | `*` | Non-blank glob that compiles; matched case-insensitively against the file name |
| `sources[].pollIntervalSec` | `30` | Whole number ≥ 1 |
| `sources[].stableForSec` | `60` | Whole number ≥ 0 |
| `consumedSuffix` | `.done` | Non-blank string without `/` or `\`; must differ from `errorSuffix` |
| `errorSuffix` | `.error` | Non-blank string without `/` or `\` |
| `ackDurability` | `full` | `full` (fsync per commit) or `normal`, any case. `normal` can lose a file already renamed `.done` on power loss |
| `maxFileBytes` | `67108864` (64 MiB) | Whole number, 1 – 134217728 (128 MiB) |
| `retention` | unbounded (the shipped yml sets 10 GiB / `P90D`) | Object with only `maxBytes` / `maxAge` |
| `retention.maxBytes` | none | Whole number ≥ 1; evicts the oldest **acked** rows while the database is larger |
| `retention.maxAge` | none | Positive ISO-8601 duration (`P90D`); evicts acked rows older than it |
| `allowBareTransactionSets` | `false` | JSON boolean. `true` wraps a file that starts at `ST` (no ISA/GS) in a synthetic envelope, using ST03 for the guide; otherwise it goes to `.error` as `bare-transaction-set` |
| `allowFileManagement` | `false` | JSON boolean (only a literal `true` enables). Opens upload / mkdir / delete under `/x12-receiver/inbox` ([DESIGN §2.9](DESIGN.md)) |

Un-acked rows and files rows are never evicted by retention. When the buffer is over
`retention.maxBytes`, the pollers apply **backpressure**: new files are left untouched until
eviction or draining frees space.

## Object tree

```
/x12-receiver
├── /files                    one node per files row, newest first (paged in SQL)
│   └── /files/<fileId>       ["container","document","binary"], documentSchema schema:shared:x12.file
│       └── /transactions     that file's transaction sets
├── /inbox                    the LIVE volume: /inbox/<source>/… real directories and files
├── /transactions             every transaction set (envelope schema)
├── /by-type/<TS>             always a container …
│   └── /<GS08>               … of one collection per guide, bound to schema:table:x12.<GS08>.<TS>
├── /by-version/<GS08> · /by-sender/<ISA06> · /by-source/<source>
├── /remittances · /claims · /service-lines                835 business entities
├── /professional-claims · /professional-service-lines     837P
├── /institutional-claims · /institutional-service-lines   837I
├── /payers · /payees         one row per distinct party, across guides
├── /stats                    document (schema:shared:x12.receiver-stats)
└── /ops                      take · ack · release · replay · recast · purge · raw · validate · rescan · packs
```

- A **`/files/<fileId>` node** carries only interface fields (name, size, mimeType, checksum,
  modified, created, tags); its files row — `fileId`, `filePath`, `currentPath`, `status`,
  `errorMessage`, counts, `redeliveryCount` — is its document (`getDocumentData`), and its bytes
  are `downloadBinary`, served from wherever the file now is (`.done`/`.error`). `fileId` is
  `<absolute path at discovery>@<first 12 hex of sha256>`.
- **`/by-type/<TS>` is a container even while one guide carries the type**, so a saved
  collection id does not change class when a second GS08 of that type arrives.
- **Business collections** are projected out of the stored object graph by
  `java/src/main/resources/mappings/<GS08>.json`: a claim is a row with `claimId`,
  `chargedAmount`, … Each is also a container of segments (`/claims/by-payerName/<value>`,
  `/claims/by-file/<fileId>`) and takes an RFC4515 `filter` plus `sortBy`/`sortDir` over its
  declared columns. 835 claims (adjudicated) and 837 claims (submitted) are different
  collections; `/payers` merges one payer across guides ([DESIGN §8.5.2](DESIGN.md)).
- **`/files` vs `/inbox`**: `/files` is what the buffer has consumed; `/inbox` is what is on the
  volume right now (a fresh readdir on every call, never cached). Each live file carries an
  `ingest` field (`watched`, `ignored:pattern`, `ignored:suffix`, `ignored:subdirectory`).
  With `allowFileManagement: true` the live branch accepts `uploadBinaryContent` (never replaces
  an existing name), `createChildObject` (mkdir) and `deleteObject` (never recursive);
  `isSupported` reports whichever way it is set.

Errors use the platform `errorModelBase` bodies: 404 `noSuchObjectError`, 400
`illegalArgumentError` for a caller mistake (malformed filter or sort, bad paging, ignored or
unknown parameters and function input), 500 with a generic message for anything else — the
cause is logged, never returned.

## Draining

| Function | Input | Behaviour |
|----------|-------|-----------|
| `take` | `filter?`, `max?`, `leaseTtl?` | Leases up to `max` drainable rows (`new`, or `in_flight` past its lease) oldest first. `max` default 100, capped at 1000; `leaseTtl` default `PT5M`, **capped at `PT1H`**. Returns `leaseId` (null when nothing was drainable), `transactions`, `remaining`. |
| `ack` | `leaseId`, `elementKeys?` | Finalizes the lease (or the listed subset). **Idempotent**: `{"acked": 0}` for an unknown, already-finalized or expired lease — a retried ack never fails. Un-acked rows of the lease return to `new` at its TTL. |
| `release` | `leaseId`, `elementKeys?` | Returns rows to `new` now; `{"released": 0}` for an unknown lease. |
| `replay` | `filter?` | Forces `in_flight` rows back to `new`. |
| `purge` | `olderThan?` | Deletes **acked** rows acked longer ago than the duration; omitted = every acked row. |
| `raw` / `validate` | `elementKey` | The stored ST..SE verbatim / the stored vs re-derived representation. 404 for an unknown key. |
| `rescan` | `source?` | Scans now instead of waiting for the next interval. 404 for an unknown source. |
| `recast` / `packs` | see `/ops/<fn>` `inputSchema` | Re-derive rows under the bundled definitions / report the bundled content packs. |

Every input is checked against the function's declared `inputSchema` before anything runs: an
unknown key (`{"olderthan":"P30D"}`), a wrong type or an unparseable filter/duration is a 400
and nothing happens. `validateFunctionInput` runs the same check without executing and returns
warnings for values the function would adjust (`max` over 1000, `leaseTtl` over `PT1H`);
`strict: true` turns them into errors.

## Health

`GET /healthz` returns `{poller, db}` with **200** when healthy and **503** when degraded. It is
degraded when any of these holds:

- the poller is not running;
- the buffer is under backpressure;
- a source is not writable;
- a source is **failing** — its most recent scan failed (the buffer rejected the work, or the
  directory could not be listed) and none has completed since;
- a source is **stalled** — no scan has completed and no file has finished for 3 poll intervals,
  and at least 120 s (a wedged mount keeps the thread alive while nothing moves).

`bufferDepth` means the same everywhere — `/healthz`, `/stats` and the connection metadata: the
**un-acked** backlog (`new` + `in_flight`). Acked rows waiting for retention are not backlog.
`/stats` adds per-status counts, file counts, `.done` files still in the inboxes and their age,
`walBytes` and `dbSizeBytes`.

## Sizing

- `resources.memoryMb` (default **1024**) is the container memory cap; the JVM heap is sized
  from it with `-XX:MaxRAMPercentage=70` (Dockerfile `JAVA_OPTS`), about 700 MB at the default.
  The node's own 512 MB default would OOM-kill the daemon, so it is always declared.
- `maxFileBytes` bounds what is read into memory. A file over it is checked from `stat` before
  a byte is read, hashed as a stream for its identity, and renamed `.error` as `too-large`; a
  file under it that still does not fit in the free heap goes to `.error` as
  `too-large-for-heap`. Parsing holds several copies of a file at once, so raise `maxFileBytes`
  together with `memoryMb`.
- Uploads (`uploadBinaryContent`) are capped at **64 MiB** by both nginx confs
  (`client_max_body_size 64m`) and the Java server; a base64 JSON upload inflates by a third, so
  that intake tops out near 48 MiB — use the raw-body form for large files.

## Supported guides

From `java/src/main/resources/com/zerobias/module/x12/parser/guides.txt`, the one table both
the parser and the schema codegen read:

| GS08 (canonical) | Type | Also accepted on the wire |
|------------------|------|---------------------------|
| `005010X221A1` | 835 | `005010X221` |
| `005010X222A1` | 837P | `005010X222` |
| `005010X223A2` | 837I | `005010X223`, `005010X223A1` |
| `005010X214` | 277CA | |
| `005010X212` | 277 | |
| `005010X231A1` | 999 | `005010X231` |
| `005010X220A1` | 834 | |

Aliases resolve to the canonical id before anything is stored or looked up. A GS08 not in the
table — including 820 (X218), 270/271 (X279) and 837D (X224), which the parser has no 005010
definition for — sends the file to `.error` as `unsupported-guide`. One interchange may carry
groups of different guides; each transaction set is typed, stored and projected under its own
group's guide.

## Operations notes

- **PHI in logs.** X12 here is PHI. Logs name files, file ids and the *kind* of a failure
  (`fatal`, `too-large`, `bad-gs`, …) — never segment content, and never a parser message that
  can quote it; non-fatal parser errors are logged as a count. The full message is kept on the
  file's files row (the `/files/<fileId>` document), behind the API's own authorization.
- **Unreadable files.** A file that cannot be read (permissions, I/O) has no identity yet, so it
  stays in place and is retried every scan. After 5 failed reads in a row it is recorded as an
  `error` files row with id `<path>@unreadable` — visible in `/files` and in the source's
  `errored` count on `/healthz` — with one ERROR log line; later retries log at DEBUG. It is never
  renamed, and the first successful read removes the row.
- **Re-dropping a consumed file.** The same bytes at the same path are a redelivery (counted,
  renamed `.done` again, no new rows); the same bytes elsewhere are a `duplicate`; new bytes at a
  reused name are a new file. To retry an `.error` file, rename it back once the cause is fixed.
- **Draining shutdown.** On stop the module closes the HTTP routes, then lets each poller finish
  the file in hand, then closes the buffer.

## Building and testing

```bash
(cd java && mvn verify)                      # unit + codegen + integration (needs GitHub Packages auth for lite-filter)
java/scripts/e2e-local.sh                    # real container; loads data through the DP API, drains, manages files
cd <repo-root>/package/x12/x12 && zbb --slot <slot> gate
```

Test fixtures under `java/src/test/resources/fixtures/` are authored from scratch. The x12.org
examples are ASC X12 intellectual property: they are linked from the docs, never fetched,
stored or tested against ([DESIGN §12](DESIGN.md)).
