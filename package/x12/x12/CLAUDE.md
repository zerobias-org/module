# CLAUDE.md — working on `@zerobias-org/module-x12-x12`

Guidance for AI coding assistants working on this package. Read this first; then
[`DESIGN.md`](DESIGN.md) (canon). The structural corollary is
[`../../hl7/v2`](../../hl7/v2) — its `CLAUDE.md` "things that will bite you" list applies
here verbatim except for the MLLP/HAPI items.

## What this is

An **always-on X12 EDI file receiver** exposed as a **DataProducer**: a poller watches
mounted inbox directories → parses each stable file with `com.imsweb:x12-parser` → durable
SQLite buffer, one row per transaction set → renames the file `.done` → typed JSON; the
platform drains via `take`/`ack`. A **daemon**, like hl7/v2, unlike demand-driven modules.

> **CRITICAL:** This is a **Java daemon module**. It does **NOT** follow the Yeoman /
> OpenAPI-connector agent workflow in `org/module/CLAUDE.md`. Implementation is Java
> (`java/`), built by the `zb.java-module` gradle plugin → maven uber jar → Docker image.

## Layout

```
api.yml                DataProducer paths ($ref'd from the interface, incl. /download; no /search) + connect/healthz; x-product-infos
connectionProfile.yml  informational — the daemon never reads it (publish pipeline requires it)
runtimeConfig.yml      daemonMode + durability[x12-buffer, x12-inbox] + resources + opaque config (sources, suffixes, durability, maxFileBytes, retention)
Dockerfile  nginx.conf  nginx-insecure.conf  startup.sh    container (uid 10001; nginx :8888 → java :8889)
build.gradle.kts       zb.java-module + the gate-stamp source/test dirs (java/scripts is not hashed)
test/e2e/              testDocker suite: describeModule<X12> + hub-sdk client against the real container, fed by docker cp
java/
├── pom.xml            uber jar (maven-shade); codegen runs at generate-resources (NOT a profile)
├── codegen/           BUILD-TIME ONLY — reads guides.txt + imsweb mapping XML → schemas/ + structure-index/
├── scripts/           dev tools, not the gate: e2e-local.sh + x12-common.sh (real container), check-x12-structure.py (fixture checks)
└── src/main/
    ├── resources/
    │   ├── buffer/schema.sql                              buffer DDL
    │   └── com/zerobias/module/x12/parser/guides.txt      THE guide table (runtime + codegen)
    └── java/com/zerobias/module/x12/
        ├── X12ApiServer.java      entry point: boots buffer + sweeper + pollers + Javalin RPC routes, /healthz, binary streaming
        ├── ModuleConfig.java      env (INTERNAL_PORT, BUFFER_DB, RUNTIME_CONFIG_YML)
        ├── ModuleRuntimeConfig.java / RuntimeConfigFile.java   MODULE_CONFIG → runtime config file → defaults; fail-fast validation
        ├── SourceConfig.java      one config.sources[] entry; boot rename probe; isEntryOf containment
        ├── PollerHandle.java      running pollers: PollerStatus + rescan + close
        ├── inbox/        InboxPoller, X12InboxPollerFactory, FileStability, FileConsumer (read → identity → parse → buffer → rename)
        ├── parser/       X12Parse (imsweb wrapper, per-group guide), X12ParseException, TransactionTypes (guides.txt), Separators, EnvelopeSynthesizer
        ├── materializer/ Materializer, TransactionJson, X12Normalizer, StructureIndex, StructureResolver
        ├── buffer/       BufferStore, SqlTransaction, DuplicateElementKeyException, LeaseManager, Lease, RetentionSweeper,
        │                 RetentionConfig, TransactionRow, FileRow, FileStatus, Status
        ├── filter/       X12Filter, X12SqlAdapter   (RFC4515 → SQLite)
        ├── producer/     OperationRouter, X12ProducerFacade, ObjectTree, SchemaRegistry, X12Operations, BinaryContent,
        │                 RecastHook, MaterializerRecastHook, ProducerException
        └── health/       HealthCheck, PollerStatus
```

## Validating changes

```bash
(cd java && mvn verify)                             # receiver + codegen JUnit suites. Needs GitHub Packages auth for lite-filter.
cd <repo-root> && ./gradlew :x12:x12:test           # via the gate task
zbb --slot <slot> testDocker                        # test/e2e through the hub-sdk client (~1 min inbox wait = stableForSec)
READ_TOKEN=$(gh auth token) java/scripts/e2e-local.sh   # real container + real file drops → browse, take/ack/purge, download
cd <repo-root>/package/x12/x12 && zbb --slot <slot> gate   # the truth
```

Auth: `~/.m2/settings.xml` server id `github` with `${env.GITHUB_ACTOR}` / `${env.READ_TOKEN}`
(env-interpolated); `READ_TOKEN` needs `read:packages`. The e2e script refuses to run without
`READ_TOKEN` (or `GITHUB_TOKEN`) — it never fetches a token itself. Maven and Docker must be
installed.

## Things that will bite you

- Everything in `../../hl7/v2/CLAUDE.md`: RPC envelope not literal `/objects` paths;
  **`PagedResults` wrapper** on every paginated op; error envelope = `errorModelBase`; two
  committed nginx confs, never rewritten; `x-product-infos` + `product-auditmation-generic-dataproducer`
  + `product-x12-x12` must stay in `package.json`; `lite-filter` dep + GitHub Packages
  `<repositories>` block both required; generated schemas are git-ignored build artifacts served
  from the classpath.
- **Rename is the ack.** `.done` only after the SQLite commit returns; never before. A rename
  failure after commit is logged and the row keeps the file from being re-consumed: the next
  scan re-hashes it, finds the same `fileId` (`<path>@<hash12>`) and counts a redelivery.
- **Nothing is skipped by path.** `fileId` is `<absolute path at discovery>@<first 12 hex of
  sha256>`; a reused name with new bytes is a new file (its `.done` gets the discovery time
  interposed), the same bytes again is a redelivery. Never add a path-keyed skip.
- **The element key carries ISA13**: `<fileId>:<ISA13>:<GS06>:<ST02>`. One file can hold several
  interchanges that restart GS06/ST02; a key collision inside one file rolls the whole file back
  (`duplicate-element-key`) instead of silently dropping a transaction set.
- **Stability window before reading.** Daily drops arrive as partial writes; never parse a file
  whose size/mtime changed within `stableForSec`, and never follow a symlink in the inbox.
- **`guides.txt` is the one guide table.** The parser and the codegen both read it, so adding a
  guide is one row there (plus an imsweb `FileType`), never a second list in code.
- **Wire names are exact.** `OperationRouter` matches interface operationIds only — no aliases,
  and every declared parameter is honoured or rejected with 400, never ignored.
- **Function inputs are schema-checked.** `SchemaRegistry.INPUTS` is both the published input
  schema and what `X12Operations` enforces; add a parameter there, not just in the handler.
- **`/by-type/<TS>` is always a container.** An object id must never change class when a second
  guide lands.
- **Fail fast on config.** Unknown keys, bad values and unusable sources stop the boot; never add
  a silent fallback to defaults.
- **Money is `decimal`, never float.** `N2` elements are implied-decimal integers on the wire;
  control numbers (ISA13, GS06, ST02) stay strings.
- **The container is unprivileged (uid 10001).** Anything it writes at runtime must be a path
  that user owns; host-path inboxes must be writable by it.
- **Test seams live in test fixtures** (`TestRows.insert`/`key`, `InboxFixtures`, `SourceStatuses`):
  production classes keep only what production calls. `FileConsumerSmallHeapTest` runs in its
  own `-Xmx64m` surefire execution (`small-heap` in `pom.xml`) and skips itself in the default fork.
- **testDocker needs a module secret in the slot.** `describeModule` runs once per
  `zbb secret` whose `_module` is this package and skips everything when there is none; the
  profile is informational, so any secret works:
  `zbb --slot <slot> secret create x12 --module @zerobias-org/module-x12-x12 x12Version=005010`.
- **No listener ports.** Do not add `listenerPorts` to `runtimeConfig.yml` or a
  `LISTENER_PORT_*` precondition to `startup.sh`; the inbox is a volume, not a socket.
- **`/inbox` must never be cached.** It is the live volume (DESIGN §2.9): every
  `getChildren` is a readdir, every `getObject` a `stat`. Do not memoize listings, and do
  not "optimize" it by joining against the `files` table — the whole point is that it shows
  files the buffer has never heard of. `/files` is the consumed projection; keep the two
  distinct.
- **File management is gated and stays gated.** `uploadBinaryContent`,
  `createChildObject` (mkdir) and `deleteObject` are refused unless
  `config.allowFileManagement` is literally `true` (default false; `e2e-local.sh` turns it
  on). Do not default it on, do not add a second way to enable it, and keep
  `isSupported` answering from the same flag. Upload never replaces an existing name
  (that would fork a path's `fileId`), and delete never recurses.
- **Every generated schema belongs to exactly one pack.** The codegen attributes each
  `write()` to the current pack and emits `packs.json` + `schemas/index.json`; the codegen
  test asserts the two reconcile (every emitted id is indexed, and the pack schema counts sum
  to the index size). Add a new emission phase without setting `currentPack` and that test
  fails — which is the point. `x12-core` is the module's own contract and is marked
  `core: true`; never let content supersede it. External packs will need namespaced ids
  (`schema:type:x12.<vendor>.<gs08>.<xid>`), so don't widen the id shape in the meantime.
- **The poller scans each source flat** (`newDirectoryStream`, DESIGN §4.2). A file
  uploaded into a subdirectory is browsable and downloadable but will never be consumed
  where it sits — that is what the `ingest` field on a live file node reports. If recursive
  ingest is ever wanted, that is a poller change with its own design, not a browse change.

## Conventions

PRs to `dev`. Conventional commits. Commit/push only when asked.
