# `@zerobias-org/module-ocsf-v1` — OCSF Receiver

An always-on Hub module that receives [OCSF](https://schema.ocsf.io) security events as files and
serves them through the OCSF interface
([`@zerobias-org/module-interface-ocsf`](../../interface/ocsf)). Anything that can write OCSF to a
folder — a SIEM export, an S3 or SFTP sync job, an operator — can feed it; anything that speaks the
OCSF interface (the OCSF collector bot) can read it, the same way it reads AWS Security Lake or
Splunk modules that implement the same interface.

Implementation: Java (`java/`), the daemon shell of [`x12/x12`](../../x12/x12) without its parser.
Design: [DESIGN.md](DESIGN.md).

## What it does

1. **Polls** each configured source directory (flat, every `pollIntervalSec`) for files matching
   `pattern` — by default `*.{json,ndjson,jsonl,gz}`.
2. **Waits** until a file's size and mtime are unchanged for `stableForSec`, so a file still being
   written is never read.
3. **Splits** it into events: `.ndjson` / `.jsonl` one per line, `.json` one object or an array,
   `.gz` of either (never inflated past `maxFileBytes`). Each event must be a JSON object with a
   `class_uid` the interface serves; anything else is rejected with a reason and the rest is kept.
4. **Stores** the accepted events verbatim in a SQLite buffer and the file's record (status,
   counts, rejection reasons) — one transaction — then **renames** the file `.done`. The rename is
   the acknowledgement. A file with nothing usable goes to `.error`.
5. **Serves** them: `listClasses`, `list<Class>(startTime, endTime, pageNumber, pageSize)` and
   `get<Class>(uid)` for every OCSF class, event bodies byte for byte as they arrived.

Duplicates: the same file again (same bytes) is recorded `duplicate`; the same event
(`metadata.uid` + bytes) in a new file is counted, not stored twice; the same `metadata.uid` with
different content is kept as a second copy, never an overwrite.

## Feeding it files

- **Mounted volume (production).** Write into the `ocsf-inbox` volume (`/var/lib/ocsf/inbox`),
  ideally under a dotfile or `.part` name and then rename: dotfiles are ignored. Subdirectories are
  not scanned.
- **Through Hub (opt-in).** With `allowFileManagement: true`,
  `BinaryApi.uploadBinaryContent` writes into `/ocsf-receiver/inbox/<source>`
  (`?objectId=&fileName=` + raw body, or `{"argMap":{objectId, fileName, contentBase64}}`), atomic,
  never overwriting, up to 64 MiB. Off by default: any Hub-authenticated caller could inject events.
- **Locally.** `docker cp events.ndjson <container>:/var/lib/ocsf/inbox/`, or
  `java/scripts/e2e-local.sh`.

## Reading events

Hub calls `POST /connections/{id}/<Tag>Api.<operation>` with `{"argMap": {…}}`:

```text
ClassesApi.listClasses                                  → [{uid, name, caption, categoryUid, category}]
IamApi.listAuthentication {startTime, endTime, pageSize} → {items: [<event>…], count, pageSize, pageNumber}
IamApi.getAuthentication  {uid}                          → <event>   (404 when absent from this class)
```

The API class is the category tag (`SystemApi`, `FindingsApi`, `IamApi`, `NetworkApi`,
`DiscoveryApi`, `ApplicationApi`, `RemediationApi`, `UnmannedSystemsApi`). `startTime` is
inclusive, `endTime` exclusive (OCSF `time`, epoch ms); newest first. `uid` is `metadata.uid`, or
`<fileId>#<line>` for an event without one. A class with no events answers an empty page.

The files themselves are a DataProducer tree:

```text
/ocsf-receiver
├─ /inbox        live view of each source directory (browse, download; upload/mkdir/delete if enabled)
├─ /files        one [document, binary] per file ever received — status, counts, reasons; raw bytes
└─ /stats        poller + buffer metrics
```

## Deployment

| Volume | Mount | Holds |
|--------|-------|-------|
| `ocsf-buffer` | `/var/lib/module` | `buffer.db` (+ WAL). Survives redeploys; migrated in place at boot. |
| `ocsf-inbox` | `/var/lib/ocsf/inbox` | The drop directory, usually bound to a host path the producer writes into. |

- **Runs as uid `10001`.** The container starts as root only long enough to repair its volumes,
  then runs nginx and Java as `ocsf` (uid/gid `10001`). The buffer volume is `chown -R 10001` only if
  that uid cannot write it; the default inbox gets `chgrp 10001` + `g+rwx` only if needed — its
  **owner is never changed**, so a host producer keeps writing.
- **A source it cannot rename in is a boot failure.** Each source is proven at start by creating
  and renaming a probe file; a missing path, a read-only mount or two sources on the same directory
  stop the daemon (exit 1).
- Symbolic links in a source are skipped and never followed.

## Configuration

`runtimeConfig.yml` `config:` (delivered as `MODULE_CONFIG`); fail-fast — an unknown key, a wrong
type or a bad glob/duration exits 1.

| Key | Default | |
|---|---|---|
| `sources[]` | one `inbox` at `/var/lib/ocsf/inbox` | `name`, `path`, `pattern` (`*.{json,ndjson,jsonl,gz}`), `pollIntervalSec` (30), `stableForSec` (60) |
| `consumedSuffix` / `errorSuffix` | `.done` / `.error` | |
| `ackDurability` | `full` | `full` fsyncs each commit; `normal` can lose a file on power loss |
| `allowFileManagement` | `false` | upload/mkdir/delete under `/inbox` |
| `maxFileBytes` | 64 MiB | largest file read (and the most a `.gz` may inflate to) |
| `maxEventBytes` | 1 MiB | largest single event |
| `retention` | 10 GiB / `P90D` | evicts events by **when they were received**, oldest first; file records are kept |

## Health

`GET /healthz` returns `{poller, db}`: **200** healthy, **503** when the poller is down, the buffer
is under backpressure, or a source is unwritable, failing its scans, or stalled (no scan completed
for 3 poll intervals, at least 120 s). `eventCount` is the number of events held — on `/healthz`,
`/stats` and the connection metadata.

## Sizing

- `resources.memoryMb` (default **1024**) caps the container; the heap is 70% of it.
- A file over `maxFileBytes` is renamed `.error` as `too-large` without being read; one that does
  not fit the free heap goes to `.error` as `too-large-for-heap`. Raise both together.

## Operations notes

- **Logs** name files, file ids and the kind of a failure — never event content. The reasons live
  on the file's record (`/files/<fileId>`), behind the API's authorization.
- **Unreadable files** (permissions, I/O) stay in place and are retried; after 5 failed reads they
  are recorded as an `error` row `<path>@unreadable`, removed on the first successful read.
- **Retrying an `.error` file:** fix the cause and rename it back.
- **Shutdown** closes the HTTP routes, lets each poller finish its file, then closes the buffer.

## Building and testing

```bash
npm install                    # brings @zerobias-org/module-interface-ocsf (its api.yml is bundled in the jar)
(cd java && mvn verify)        # JUnit: ingest, router, retention, spec, daemon shell
java/scripts/e2e-local.sh      # the real image: every OCSF class uploaded, 1.1.0 samples dropped, restart
./gradlew :ocsf:v1:gate        # from the repo root
```

Committed fixtures (`test/e2e/fixtures/`) are hand-written. The e2e script fetches sample events
from schema.ocsf.io and caches them in `~/.cache/ocsf-e2e-samples`.
