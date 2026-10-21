# CLAUDE.md — working on `@zerobias-org/module-ocsf-v1`

Guidance for AI sessions working on this package. Read this first; then [`DESIGN.md`](DESIGN.md)
(canon) and the interface it serves, [`../../interface/ocsf`](../../interface/ocsf) (its
`CLAUDE.md` and `documentation/Implementing.md`). The daemon shell comes from
[`../../x12/x12`](../../x12/x12); where this package is silent, its "things that will bite you"
list applies, minus everything about X12 parsing, the object graph and take/ack.

## What this is

An **always-on OCSF Receiver**: a poller watches mounted inbox directories → splits each stable
file into OCSF events → durable SQLite buffer, one row per event, body verbatim → renames the
file `.done` → serves the events through the **OCSF interface** (`listClasses`,
`list<Class>`, `get<Class>`). The files (not the events) are a DataProducer tree.

> **CRITICAL:** This is a **Java daemon module**. It does **NOT** follow the Yeoman /
> OpenAPI-connector agent workflow in `org/module/CLAUDE.md`. Implementation is Java (`java/`),
> built by the `zb.java-module` gradle plugin → maven uber jar → Docker image.

## Layout

```
api.yml                $refs ALL interface-ocsf paths + DataProducer files paths + connect/metadata/healthz
connectionProfile.yml  informational — the daemon never reads it (publish pipeline requires it); ackDurability only
runtimeConfig.yml      daemonMode + durability[ocsf-buffer, ocsf-inbox] + resources + opaque config (sources, suffixes, retention, …)
Dockerfile  nginx.conf  nginx-insecure.conf  startup.sh    container (nginx 8888 → java 8889), runs as uid 10001
build.gradle.kts       zb.java-module + gate-stamp source/test dirs (test/ is hashed: the e2e suite; java/scripts is NOT)
test/e2e/              testDocker suite: hub-sdk client against the real container, fed by docker cp (fixtures/ hand-written)
java/
├── pom.xml            uber jar (shade); copies node_modules/@zerobias-org/module-interface-ocsf/api.yml → classpath ocsf/api.yml
├── scripts/           e2e-local.sh + e2e_checks.py — the real image with uploads on; dev tool, not gated
└── src/main/java/com/zerobias/module/ocsf/
    ├── OcsfApiServer.java   entry: config → source validation → OcsfClasses → buffer → sweeper → pollers → facade → Javalin
    ├── OcsfClasses          the class table, read from the bundled interface spec (only classes with list operations)
    ├── ModuleConfig / ModuleRuntimeConfig / RuntimeConfigFile / SourceConfig   env + MODULE_CONFIG (fail-fast)
    ├── inbox/        InboxPollerFactory, InboxPoller, FileStability, FileConsumer, EventSplitter (file → events)
    ├── buffer/       BufferStore (files + events, dup/conflict rules), RetentionSweeper, EventRow, FileRow, FileStatus
    ├── producer/     OperationRouter (generic OCSF branch + DataProducer subset), ProducerFacade, ObjectTree,
    │                 InboxFiles (live /inbox + file management), BinaryContent, ProducerException
    └── health/       HealthCheck, PollerStatus
```

## Validating changes

```bash
npm install                                   # the interface package: the jar cannot build without its api.yml
(cd java && mvn verify)                       # JUnit (the gate's test step)
java/scripts/e2e-local.sh                     # the real image, uploads on, ~1 min
./gradlew :ocsf:v1:gate                       # from the repo root: spec, codegen, jar, image, testDocker, dataloader
```

## Things that will bite you

- **The class table is the interface spec.** `OcsfClasses` reads the `api.yml` bundled into the
  jar. A class is served only if the interface has a `list<Model>` operation for it; `base_event`
  (class_uid 0) has none, so its events are **rejected** at ingest — nothing could read them.
  An OCSF upgrade is an interface bump, then `npm install`; never per-class code here.
- **`api.yml` must `$ref` exactly the interface's paths.** `ApiSpecTest` fails when the
  interface adds or drops one: regenerate the OCSF block of `api.yml` from the interface's
  `dist/module-interface-ocsf.yml` path list.
- **The API class is checked.** `IamApi.listAuthentication` is served; `SystemApi.listAuthentication`
  is unsupported, not silently answered. The API class is the tag in PascalCase + `Api`
  (`unmanned_systems` → `UnmannedSystemsApi`, as the hub-sdk names it).
- **Bodies are spliced, never re-serialized.** `listEvents` joins the stored strings into the
  `PagedResults` body; `getEvent` returns the stored string. Re-parsing would change number text
  and key order — the evidence must arrive as it was written.
- **Paging is in the body.** The Hub's java-http invoker wants `{items, count, pageSize,
  pageNumber}`, not the interface's `count`/`link` headers. `pageToken` is refused (400).
- **Strict JSON only.** `EventSplitter.strictParse` uses Gson's type adapter on a non-lenient
  reader; `JsonParser.parseReader` would silently make it lenient (comments, single quotes, NaN).
- **No OCSF schema validation.** Real events miss required attributes; dropping them would drop
  the evidence (interface `Implementing.md`). Accept = JSON object + known integer `class_uid` +
  under `maxEventBytes`.
- **Duplicates have three levels.** Same file bytes → `duplicate` file status (checksum, before
  splitting). Same `metadata.uid` + same body in a new file → counted on `duplicateCount`, not
  stored. Same uid, different body → stored under `<uid>#<fileId>`. Never an overwrite.
- **Retention ages by ingest time** (`received_at`), never the event's `time`: a backfill of old
  events must survive its own arrival. `files` rows are never evicted.
- **The default pattern is event files.** A source without `pattern` watches
  `*.{json,ndjson,jsonl,gz}`; `*` would sweep a README or CSV into `.error`.
- **Rename is the ack**, `/inbox` is never cached, file management stays gated, sources are
  scanned flat, no listener ports — all inherited from x12/x12 and unchanged.
- **Nested objects are untyped in the client.** The interface links models by `x-ocsf-object`
  (not `$ref`), so `event.user` is `object` in TypeScript: read nested fields as records.
- **No OCSF product package yet.** `api.yml` names the generic DataProducer and ZeroBias
  products until an `ocsf/ocsf` product exists (DESIGN §10).

## Conventions

Branch `task/dev/contextDev-10`, PRs to `dev`. Conventional commits (`feat(ocsf-v1): …`).
Commit/push only when asked.
