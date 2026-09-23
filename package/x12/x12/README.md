# `@zerobias-org/module-x12-x12` — X12 EDI File Receiver

An **always-on X12 EDI receiver** exposed through the standard **DataProducer** interface.
Payers, clearinghouses and billing systems drop X12 interchange files (837 claims, 835
remittances, 277CA acknowledgments, 999s …) into a mounted directory; the module polls it,
parses every transaction set with [`imsweb/x12-parser`](https://github.com/imsweb/x12-parser),
persists them to a durable buffer, marks the file `.done`, and offers files and transactions to
the Hub pipeline through the same container/collection/function model every other DataProducer
uses — plus raw file download.

> Design canon: [`DESIGN.md`](DESIGN.md) · Working on the code? [`CLAUDE.md`](CLAUDE.md) ·
> Structural correlary: [`../../hl7/v2`](../../hl7/v2) (the HL7 v2 MLLP receiver this module mirrors) ·
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

## Object tree

```
/x12-receiver
├── /files                    one node per interchange file  ["container","binary"]
│   └── /files/<fileId>/transactions
├── /transactions             every transaction set (envelope schema)
├── /by-type/835 | 837P | 837I | 277CA | 999 …
├── /by-version/005010X221A1 …
├── /by-sender/<ISA06>
├── /by-source/<inbox name>   one node per watched directory
├── /inbox                    the LIVE volume: /inbox/<source>/… real dirs and files
├── /stats                    document
└── /ops                      take · ack · release · replay · recast · purge · raw · validate · rescan
```

## Configuration

Everything daemon-level lives in `runtimeConfig.yml` and reaches the container as `MODULE_CONFIG`:
`sources[]` (`name`, `path`, `pattern`, `pollIntervalSec`, `stableForSec`), `consumedSuffix`
(`.done`), `errorSuffix` (`.error`), `ackDurability`, `retention`, `allowFileManagement`. Two
volumes are declared: `x12-buffer` (the SQLite buffer) and `x12-inbox` (the drop directory). See
the file for the annotated defaults.

### Browsing and managing the volume

`/files` shows what has been **consumed** (it is a projection of the buffer). `/inbox` shows
what is **on the volume right now** — one node per configured source, then real directories and
files, listed by a fresh readdir on every call, so a file that has never been ingested is still
browsable and downloadable. Each live file node carries an `ingest` field (`watched`,
`ignored:pattern`, `ignored:suffix`, `ignored:subdirectory`) saying what the poller will do with
it.

With `allowFileManagement: true` the same branch accepts the interface's container/binary write
ops, which is how a test or an operator loads data without reaching around the module:
`uploadBinaryContent` (atomic write into any chosen directory, never replacing an existing
name), `createChildObject` (mkdir, so you choose where uploads land) and `deleteObject` (unlink
a file, remove an empty directory). It defaults to **false** — a production receiver takes files
from the feed, and an open upload path would let any Hub-authenticated caller inject claims.
`isSupported/{operationId}` reports the real capability set, so a caller can tell the difference.
See [`DESIGN.md`](DESIGN.md) §2.9.

## Guides supported in v1

835 (X221A1) · 837P (X222A1) · 837I (X223A2) · 277CA (X214) · 277 (X212) · 999 (X231A1) ·
834 (X220A1); 820 (X218) has schemas but no parser definition. Not yet: 270/271 at 005010,
837D. See DESIGN.md §6.

## Building and testing

```bash
(cd java && mvn test)                        # JUnit unit suite (needs GitHub Packages auth for lite-filter)
(cd java && mvn verify)                      # + integration tests
java/scripts/e2e-local.sh                    # real container; loads the 835 through the DP API
java/scripts/fetch-x12org-examples.py        # local-only conformance set from x12.org (never committed)
cd <repo-root>/package/x12/x12 && zbb --slot <slot> gate
```

Test fixtures under `java/src/test/resources/fixtures/` are synthetic. The x12.org examples are
X12 intellectual property and are fetched locally, never vendored — see DESIGN.md §13.
