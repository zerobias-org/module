# CLAUDE.md — working on `@zerobias-org/module-x12-x12`

Guidance for AI sessions working on this package. Read this first; then
[`DESIGN.md`](DESIGN.md) (canon). The structural correlary is
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
api.yml                DataProducer paths ($ref'd from the interface, incl. /download) + connect/healthz; x-product-infos
connectionProfile.yml  informational — the daemon never reads it (publish pipeline requires it)
runtimeConfig.yml      daemonMode + durability[x12-buffer, x12-inbox] + resources + opaque config (sources, suffixes, retention)
Dockerfile  nginx.conf  nginx-insecure.conf  startup.sh    container (nginx → java on 8889)
java/
├── pom.xml            uber jar (maven-shade); codegen runs at generate-resources (NOT a profile)
├── codegen/           BUILD-TIME ONLY — reads imsweb mapping XML → schemas/ + structure-index/ + packs.json
├── scripts/           fetch-x12org-examples.py (local, never committed output), e2e-local.sh, x12-live.sh
└── src/main/java/com/zerobias/module/x12/
    ├── X12ApiServer.java          entry point: boots buffer + pollers + Javalin RPC routes
    ├── ModuleConfig.java / ModuleRuntimeConfig.java / RuntimeConfigFile.java   env (MODULE_CONFIG)
    ├── inbox/        InboxPoller, SourceConfig, FileStability, FileConsumer (parse → buffer → rename)
    ├── parser/       X12Parse (imsweb wrapper), TransactionTypes (GS08 → FileType/display name), EnvelopeSynthesizer
    ├── materializer/ Materializer, EntityGraph (flatten/assemble the object graph), X12Normalizer, StructureIndex, StructureResolver
    ├── buffer/       BufferStore, LeaseManager, RetentionSweeper, TransactionRow, FileRow, Lease, Status
    ├── filter/       X12SqlAdapter, X12Filter   (RFC4515 → SQLite)
    ├── producer/     OperationRouter, X12ProducerFacade, ObjectTree, InboxFiles (live /inbox browse + file mgmt), SchemaRegistry, PackCatalog (content packs), BusinessEntities + BusinessFilter + mapping/EntityMapping (business collections), X12Operations, MaterializerRecastHook, ProducerException
    ├── resources/mappings/<GS08>.json   business entity mappings — CONTENT, not code
    └── health/       HealthCheck
```

## Validating changes

```bash
(cd java && mvn test)          # unit; `mvn verify` adds integration (failsafe). Needs GitHub Packages auth for lite-filter.
cd <repo-root> && ./gradlew :x12:x12:test   # via the gate task
java/scripts/fetch-x12org-examples.py       # once, locally: populates the git-ignored x12org conformance set
java/scripts/e2e-local.sh                   # real container, data loaded THROUGH the DP API → take/ack/purge + file mgmt
cd <repo-root>/package/x12/x12 && zbb --slot <slot> gate   # the truth
```

Auth: `~/.m2/settings.xml` server id `github` with `${env.GITHUB_ACTOR}` / `${env.READ_TOKEN}`
(env-interpolated); `READ_TOKEN` needs `read:packages`. Maven and Docker must be installed.

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
- **Stability window before reading.** Daily drops arrive as partial writes; never parse a file
  whose size/mtime changed within `stableForSec`.
- **Money is `decimal`, never float.** `N2` elements are implied-decimal integers on the wire.
- **The x12.org examples are not ours to commit.** `java/src/test/resources/x12org/` is
  git-ignored on purpose; the fetch script is the only way it gets populated.
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
- **There is no stored document.** `mapped_json` is gone: the graph is the representation and
  every read goes through `EntityGraph.assemble` (`BufferStore.documentFor` / batched
  `documentsFor`). Do not add a document column back "for speed" — that is two
  representations to keep in sync, which is what this replaced. The envelope is overlaid at
  read time by `toElement`, never stored in the body.
- **`value_text` is the value; `value_num` is a comparison key.** Amounts live in
  `entity_values` as exact integer micro-units for filtering and in `value_text` for
  reassembly. Never read an amount back from `value_num` — that is how `450.00` becomes
  `450.0`, and money must not round-trip through a float. Same rule for `transaction_dims`.
- **Wire order is persisted, not implied.** `entities.property_order` and `entity_values.seq`
  exist because a composite is a child row and would otherwise reassemble after every scalar.
  The round-trip test (`EntityGraphTest`, `GraphPersistenceTest`) is what licenses storing rows
  instead of the document — keep it passing or the graph is not a faithful substitute.
- **The graph commits with its transaction.** One SQL transaction for transaction rows + graph +
  dimensions, and the `.done` rename after it returns. Every delete path must drop graph and dim
  rows too: there is no FK enforcement unless the pragma is on.
- **Grain is declared by the anchor.** A mapping's `anchorSchemaId` IS its grain (Claim =
  loop2100). Don't add a business entity without deciding its grain, and remember CAS carries up
  to six (reason, amount, quantity) triplets — anchoring an Adjustment at the segment would hide
  five of them.
- **Business schemas are generated from the mappings**, never hand-written: a collection may not
  advertise a `collectionSchema` the registry cannot serve, and generating it keeps the schema
  and the projection from disagreeing.
- **Two RFC4515 paths, deliberately.** Structural collections compile to SQL via
  `X12SqlAdapter` (a body path is now a scalar subquery over `entity_values`, NOT
  `json_extract` — that column is gone); business collections evaluate `BusinessFilter` over
  projected rows. They are separate parsers today, which is duplication worth collapsing onto
  lite-filter rather than extending twice. `sortBy`/`sortDir` are accepted and ignored on both.
- **The poller scans each source flat** (`newDirectoryStream`, DESIGN §4.2). A file
  uploaded into a subdirectory is browsable and downloadable but will never be consumed
  where it sits — that is what the `ingest` field on a live file node reports. If recursive
  ingest is ever wanted, that is a poller change with its own design, not a browse change.

## Conventions

Branch `feat/module-x12-x12`, PRs to `dev`. Conventional commits. Commit/push only when asked.
