# `@zerobias-org/module-ocsf-v1` — OCSF Receiver — Design

**Status:** v1 design draft, 2026-10-20. Task contextDev-10, blocked by contextDev-12
(`@zerobias-org/module-interface-ocsf`, zerobias-org/module#68). Structural correlary:
[`../../x12/x12/DESIGN.md`](../../x12/x12/DESIGN.md) — this document states what the OCSF
Receiver changes; where it is silent, x12/x12 is the rule.

**One sentence:** an always-on daemon module that ingests OCSF event files (polled from a
mounted inbox, or uploaded), stores every event verbatim in a durable SQLite buffer, and serves
them through the **OCSF interface** (`listClasses`, `list<Class>`, `get<Class>`), with the
**DataProducer** interface for the file inbox only.

Decisions already taken (Kevin McCarthy, Daniel Rojas, 2026-10-19/20): OCSF is an interface that
many products implement; the interface comes first and is built from OCSF's own spec; this
Receiver is ZeroBias's own implementer, for OCSF data that arrives as files — "the same way we can
add files to HL7"; DataProducer is not required by the OCSF interface but may be on the Receiver;
Auditgraph stores facts, not events, so the Receiver — not the platform — holds the raw events.

---

## 1. What changes from x12/x12

| | x12/x12 | ocsf/v1 |
|---|---|---|
| Unit received | one interchange file, N transaction sets | one file, N OCSF events (`.json`, `.ndjson` / `.jsonl`, optionally `.gz`) |
| Atom | transaction set | **event** (one JSON object) |
| Parser | imsweb x12-parser + generated structure index | **none** — Gson reads JSON strictly (RFC 8259); the event is stored as written |
| Typed read surface | DataProducer collections per guide | **OCSF interface** operations per class (§2) |
| DataProducer surface | everything | **files only**: `/inbox`, `/files`, `/stats` (§3) |
| Schemas served | generated at build, classpath | none of its own for events — the interface's `api.yml` is the contract |
| Drain (`ops/take`, `ack`) | yes | **no** in v1 (§9) |

Inherited unchanged from x12/x12: Java daemon (`zb.java-module`, nginx 8888 → Javalin 8889),
fail-fast module config, inbox poller with `stableForSec` and `.done` / `.error` renames, guarded
file management (`allowFileManagement`, path-traversal and symlink rules, 64 MiB cap), SQLite
single-writer buffer with migrations, retention sweeper, `runtimeConfig.yml` daemon mode with
durable mounts, JUnit + e2e test surface.

**Why Java, not TypeScript:** the daemon shell (poller, buffer, upload safety, sweeper, nginx
envelope) exists only in Java (hl7/v2, x12/x12); OCSF parsing is plain JSON, so the language buys
nothing else. `shortcut:` the shell is copied from x12/x12 rather than shared — a third copy
(after hl7/v2 and x12/x12); extract a receiver library when a fourth receiver is planned.

## 2. OCSF interface surface

`api.yml` `$ref`s **every** path of `@zerobias-org/module-interface-ocsf`
(`dist/module-interface-ocsf.yml`) — 159 paths — plus the DataProducer paths of §3.

| Operation | Behaviour |
|---|---|
| `listClasses` | `SELECT DISTINCT class_uid FROM events`, mapped to `{uid, name, caption, categoryUid, category}` from the interface's class models (`x-ocsf-class-uid`, `x-ocsf-category`). A class appears once its first event lands. |
| `list<Class>(startTime, endTime, pageNumber, pageSize)` | `WHERE class_uid = ? AND time >= ? AND time < ? ORDER BY time DESC, seq DESC LIMIT/OFFSET`; response = the `PagedResults` body `{items, count, pageSize, pageNumber}` the Hub's java-http invoker expects (the interface's `count`/`link` headers are not used on the RPC wire), `count` = the matching `COUNT(*)`, `items` = the stored JSON of each event spliced in verbatim. `startTime >= endTime` is a 400. Missing bounds are open. Events with no `time` are only returned when neither bound is set. |
| `get<Class>(uid)` | `WHERE class_uid = ? AND event_key = ?`; 404 `noSuchObjectError` otherwise — including a key that exists under another class. |

- **One handler for all 158 class operations.** Hub calls `POST /connections/{id}/<Tag>Api.<operationId>`
  with `{"argMap": {…}}`. The route is resolved from the operationId: `list` / `get` prefix + model
  name → `class_uid` through a table built at boot from the interface's `api.yml` (bundled in the
  jar at classpath `ocsf/api.yml`), and the API class must be that class's tag
  (`IamApi.listAuthentication`; `SystemApi.listAuthentication` is unsupported). The table holds
  only classes the interface gives operations: `base_event` (class_uid 0) has none, so its events
  are rejected at ingest. No per-class code; a new OCSF version is a dependency bump.
- **Nested objects are typed by name.** The interface links models with `x-ocsf-object`, not
  `$ref` (the dataloader stores the dereferenced spec), so the generated client types nested
  objects as plain records. The Receiver never reads models; it is unaffected.
- **Events are returned as written** (interface `Implementing.md`): no renaming, dropping,
  coercion or version upgrade. `metadata.version` is whatever the source wrote.
- **Element key (`get<Class>` uid):** `metadata.uid` when present and non-empty, else
  `<fileId>#<line>` (line for NDJSON, array index for JSON). Opaque to callers.
- **Duplicates:** a later event with the same `metadata.uid` **and** the same bytes is a
  redelivery — counted on the file, not stored. Same uid, different bytes: stored under
  `<metadata.uid>#<fileId>` and counted as a conflict. Never silently overwritten.

## 3. DataProducer surface (files only)

```
"/"                          container (root)
└─ "/ocsf-receiver"          container — the receiver instance
   ├─ /inbox                 container → live filesystem per source (x12 §2.9, unchanged)
   ├─ /files                 container → one child per ingested file
   │    └─ /files/<fileId>   ["document","binary"] — files row as document, raw bytes downloadable
   └─ /stats                 document — poller + buffer metrics
```

Events are **not** DataProducer objects: consumers read them only through the OCSF interface,
so there is one way in. The `/files/<fileId>` document carries `status` (`consumed` | `error` |
`partial`), `eventCount`, `rejectedCount`, `duplicateCount`, `conflictCount`, the first 20
rejection reasons with line numbers, and `fileId` = `<path>@<sha256[0:12]>` (x12 rule).

## 4. Ingest

1. Poller lists `pattern` matches per source, flat, stable for `stableForSec`, no symlinks,
   skipping dotfiles, `.done` and `.error` (x12 §4.2). The default pattern is
   `*.{json,ndjson,jsonl,gz}`, so a source without one never sweeps a README or CSV into
   `.error`. Uploads land in `/inbox` the same way.
2. Format by extension: `.ndjson` / `.jsonl` → one event per non-empty line; `.json` → one object
   or an array of objects; a `.gz` suffix is gunzipped first. Anything else → `.error`
   (`unsupported-format`).
3. Per event, **accept** when it is a JSON object with an integer `class_uid` that the interface
   declares. **Reject** (count + reason, file goes on) otherwise: not an object, no/invalid
   `class_uid`, unknown class (e.g. an extension class), event over `maxEventBytes`.
4. One SQLite transaction per file: insert accepted events, the `files` row, the counts. Commit,
   then rename `<file>` → `<file>.done` (or `.error` when the file could not be read at all or
   every event was rejected; `partial` status when some were). A crash before commit leaves the
   file to be picked up again; after commit, before rename, the file's `fileId` is already
   recorded, so the rescan counts a redelivery instead of storing twice.

No OCSF schema validation of event bodies: real events miss required attributes and carry dirty
values (interface `Implementing.md`); validation would drop the evidence the collector needs.

## 5. Buffer

```sql
events(seq INTEGER PRIMARY KEY, event_key TEXT NOT NULL, class_uid INTEGER NOT NULL,
       category_uid INTEGER, time INTEGER, severity_id INTEGER, metadata_version TEXT,
       file_id TEXT NOT NULL, line INTEGER NOT NULL, body TEXT NOT NULL,   -- verbatim JSON
       UNIQUE(class_uid, event_key))
INDEX events_class_time ON events(class_uid, time DESC, seq DESC)
files(file_id TEXT PRIMARY KEY, path TEXT, source TEXT, status TEXT, size INTEGER,
      sha256 TEXT, discovered INTEGER, event_count INTEGER, rejected_count INTEGER,
      duplicate_count INTEGER, conflict_count INTEGER, rejections TEXT, redelivery_count INTEGER)
```

The indexed columns are copies of top-level event fields, extracted at ingest; `body` stays the
source of truth. Retention ages events by **ingest time** (`received_at`), never by the source's
`time` — a backfill of old events must not be evicted on arrival. `maxAge` deletes what was received
before the cutoff; `maxBytes` deletes oldest-received first. `files` rows are never evicted.

## 6. Configuration (`runtimeConfig.yml` `config:`)

```yaml
daemonMode: true
durability:
  - { volumeName: ocsf-buffer, mountPath: /var/lib/module,     access: rw }
  - { volumeName: ocsf-inbox,  mountPath: /var/lib/ocsf/inbox, access: rw }
resources: { memoryMb: 1024 }
config:
  sources:
    - { name: inbox, path: /var/lib/ocsf/inbox, pattern: "*.{json,ndjson,jsonl,gz}",
        pollIntervalSec: 30, stableForSec: 60 }
  consumedSuffix: ".done"
  errorSuffix: ".error"
  allowFileManagement: false        # uploads off unless the deployment opts in
  maxFileBytes: 67108864            # 64 MiB, as x12
  maxEventBytes: 1048576            # 1 MiB per event
  retention: { maxBytes: 10737418240, maxAge: P90D }
```

Fail-fast exactly as x12 §3: unknown keys, wrong types, bad globs or durations exit 1.

## 7. Package

| | |
|---|---|
| Path / npm | `package/ocsf/v1` → `@zerobias-org/module-ocsf-v1`, `zerobias.package: ocsf.v1.module` |
| Build | `zb.java-module`; `java/` Maven project (Javalin, SQLite JDBC, Gson, SnakeYAML); the pom copies the interface's `api.yml` into the jar |
| Dependencies | `@zerobias-org/module-interface-ocsf`, `@zerobias-org/module-interface-dataproducer`, `@zerobias-org/product-auditmation-generic-dataproducer`, `@zerobias-org/product-zerobias-zerobias` (stand-in until an OCSF product exists, §10) |
| Files | `api.yml` (`$ref`s all 159 interface paths + the DataProducer files paths; `ApiSpecTest` fails when the interface's path set changes), `runtimeConfig.yml`, `connectionProfile.yml` (no required field), `Dockerfile`, `nginx*.conf`, `startup.sh`, `DESIGN.md`, `README.md`, `CLAUDE.md` |

## 8. Tests

- **Unit (JUnit, the gate):** `InboxIngestTest` drives the real poller with a fake clock through
  the folder-drop path — stability window, partial and error files, duplicates and conflicts,
  gzip and its inflation cap, dotfiles and other names left alone. `OperationRouterTest` (class
  table, API-class check, caller errors, `isSupported`), `RetentionSweeperTest` (ingest-time
  ageing), `ApiSpecTest` (the spec matches the interface and what the server writes), plus the
  inherited daemon shell tests (config, startup script, health, shutdown order, inbox file management).
- **e2e (gate `testDocker`, `test/e2e/ocsf.test.ts`):** the real container on the shipped config;
  committed hand-written fixtures `docker cp`'d into the inbox; read back through the hub-sdk
  client (`getIamApi().listAuthentication`), verbatim bodies, paging, time window, 404s, files side.
- **e2e (local, `java/scripts/e2e-local.sh`):** the image with uploads on; every OCSF 1.9.0 class
  uploaded through `uploadBinaryContent`, the 1.1.0 samples (Security Lake's version) dropped on
  the folder, bad input, `docker restart`. Sample events from schema.ocsf.io, cached locally.
- **Contract:** the same calls are what any OCSF producer must answer — reusable for Security
  Lake and Splunk modules.

## 9. Out of scope (v1)

- Push ingest (HTTP POST of events, syslog, Kafka): files and uploads only.
- Drain semantics (`take` / `ack`): consumers read by time window; add only if a collector
  needs exactly-once delivery.
- Parquet (Security Lake's custom-source format) and OCSF extensions (`win`, `linux`, …).
- Filters beyond `startTime` / `endTime`: the interface declares only those.
- Mapping events into Auditgraph: the OCSF collector bot's job (task plan #4).

## 10. Open questions

1. **Product package.** hl7/v2 and x12/x12 each depend on their own product
   (`product-hl7-hl7`, `product-x12-x12`). `org/product` has no `ocsf` product (vendor `ocsf`
   exists; no suite): until a productDev ticket creates `ocsf/ocsf`, `api.yml` names the generic
   DataProducer and ZeroBias products.
2. **Duplicate policy** (§2): conflicts are stored under a suffixed key — or keep only the first?
3. **`get<Class>` key** when `metadata.uid` is absent: `<fileId>#<line>` changes if the same
   event arrives in a different file — acceptable for v1?
4. **Retention defaults** — 90 days / 10 GiB copied from x12; right for security events?
