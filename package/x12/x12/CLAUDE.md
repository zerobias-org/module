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
build.gradle.kts       zb.java-module + gate-stamp source/test dirs (test/ is hashed: the e2e suite)
.mocharc.json  test/e2e/   testDocker suite: describeModule<X12> + hub-sdk client against the real container, fed by docker cp
java/
├── pom.xml            uber jar (maven-shade); codegen runs at generate-resources (NOT a profile)
├── codegen/           BUILD-TIME ONLY — reads imsweb mapping XML → schemas/ + structure-index/ + packs.json
├── scripts/           e2e-local.sh, x12-live.sh
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
zbb --slot <slot> testDocker                # test/e2e through the hub-sdk client (~1 min: the inbox wait is stableForSec)
java/scripts/e2e-local.sh                   # real container, data loaded THROUGH the DP API → take/ack/purge + file mgmt
cd <repo-root>/package/x12/x12 && zbb --slot <slot> gate   # the truth
```

### The e2e suite (`test/e2e`, run by `testDocker`)

- **It needs a module secret in the slot, once:**
  `zbb --slot <slot> secret create x12 --module @zerobias-org/module-x12-x12 x12Version=005010`.
  `describeModule` (module-test-client) runs once per `zbb secret` whose module is this package,
  and with none it registers a single *skipped* test — a green `testDocker` that ran nothing. The
  suite checks for that first and fails with the command above instead. The profile is
  informational (the daemon never reads it), so any valid one works; `SECRET_NAME=<name>` picks
  an existing one.
- **Fed by `docker cp`, not the API.** Gradle's `startModuleExec` starts the image with the
  committed `runtimeConfig.yml` `config` as `MODULE_CONFIG`, so `allowFileManagement` is false and
  there is no upload path (and the hub-sdk `uploadBinaryContent(objectId, body)` cannot carry the
  `fileName` the receiver needs anyway). The suite copies the 835 / 837P / 837I fixtures plus
  `test/e2e/fixtures/835-two-interchanges.x12` into the source directory and waits, bounded by the
  container's own `stableForSec + pollIntervalSec`, nudging with `ops/rescan`. The file-management
  tests read the flag from the container's `MODULE_CONFIG` and assert whichever way it is set.
- **It needs a fresh container.** The receiver de-duplicates by content, so re-dropping the same
  bytes records `status:duplicate`; the wait fails fast saying so. Gradle starts a new container
  (new anonymous volumes) per run. Outside gradle: `X12_CONTAINER=<name> CONTAINER_URL=https://localhost:<port>
  TEST_MODE=docker MODULE_DIR=$PWD npx mocha --config .mocharc.json 'test/e2e/**/*.test.ts'`
  against a container you started with `MODULE_CONFIG` and connected as `e2e`.
- **Decimals through the client lose their scale.** The wire carries `"chargedAmount":300.00`;
  the hub-sdk docker client (axios `JSON.parse`) hands back the number `300`. The suite asserts
  both — the value through the client, the scale on the raw wire. Do not "fix" the assertion to
  `300.00`: that is a transport property, not a receiver bug.
- The drain cycle (take → ack → purge) runs last on purpose: purge removes the 837P rows.

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
  whose size/mtime changed within `stableForSec` — or changed since the window saw it, or while
  it was being read (the consumer re-stats before and after).
- **One file never stops the scan; a broken buffer always does.** Size is checked from `stat`
  against `maxFileBytes` before reading; anything a single file throws (OOM included) is that
  file's `.error` or a retry, and the scan moves on. Only a buffer failure (I/O, NOT NULL/CHECK —
  the schema no longer matches) ends the scan, leaves the file in place and turns `/healthz`
  red. Do not widen `FileConsumer.rejectsThisFile` to NOT NULL: that sends every file to
  `.error` on a schema bug. Symlinks are never followed, and renames never overwrite.
- **Money is `decimal`, never float.** `N2` elements are implied-decimal integers on the wire.
- **The x12.org examples are not ours to copy.** They are ASC X12 IP: link to them, never
  fetch, store, commit or test against them (a scraper was removed for exactly this). Fixtures
  are authored from scratch.
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
- **The buffer outlives the image; `schema.sql` is not a migration.** The `x12-buffer` volume
  survives every redeploy, and `CREATE TABLE IF NOT EXISTS` never alters a table that is already
  there. Any column change to `schema.sql` needs a step in `BufferStore.migrate` (probe the real
  shape, bump `SCHEMA_VERSION`) and a frozen copy of the old DDL under
  `src/test/resources/buffer/` exercised like `LegacyBufferUpgradeTest`. Dropping `mapped_json`
  without one stopped ingest on every upgraded receiver while health stayed green. Rows that
  predate the graph are rebuilt from `raw_x12` by `GraphBackfill` at startup, before the pollers
  and routes open.
- **`value_text` is the value; `value_num` is a comparison key.** Amounts live in
  `entity_values` as exact integer micro-units for filtering and in `value_text` for
  reassembly. Never read an amount back from `value_num` — that is how `450.00` becomes
  `450.0`, and money must not round-trip through a float. Same rule for `transaction_dims`,
  and for business projection: `EntityMapping.typed` parses `value_text`, never `Value.num()`.
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
  five of them. An anchor-grain collection belongs to ONE guide: 835 claims (`/claims`,
  adjudicated) and 837 claims (`/professional-claims`, `/institutional-claims`, submitted) are
  different collections with different schemas — do not fold them into a union schema. Read
  inherited data with an ancestor step (`^loop2000B.loop2010BA.nm1.nm109`), not a dimension.
  A party that spans guides (`/payers`) is `"grain": "dimension"` (DESIGN §8.5.2); keep its
  identity rule (id first, name-only joins a unique same-name id) and keep every guide's
  declaration of it identical, or the merge drops the odd one out.
- **A dimension is unambiguous or absent.** `EntityMapping.dimensions` follows every branch and
  keeps a value only when they all agree; an 837 batch with two payers has no `payerName`.
  Never "fix" that by taking the first match — it files claims under the wrong payer. Segment
  values are narrowed to those that scope a row of the collection itself, because dimension
  names are shared across guides.
- **Business schemas are generated from the mappings**, never hand-written: a collection may not
  advertise a `collectionSchema` the registry cannot serve, and generating it keeps the schema
  and the projection from disagreeing.
- **ONE RFC4515 parser: lite-filter.** Both paths parse with it. Structural collections
  compile the expression to SQL via `X12SqlAdapter` (a body path is a scalar subquery over
  `entity_values`, NOT `json_extract` — that column is gone); business collections evaluate it
  with `Expression.matches(row)` over the projected row. Never hand-roll a filter parser here:
  a second grammar is a second set of accepted syntaxes to keep in sync, and the extensions
  come free. `BusinessFilter` adds only attribute validation, since the library cannot know an
  entity's columns. `sortBy`/`sortDir` are honored on both: SQL `ORDER BY` for structural
  collections, a typed comparator for business rows, NULLs last in both directions, and a 400
  for an unknown attribute or direction. A sort property is resolved by the adapter, never
  interpolated into SQL raw.
- **The poller scans each source flat** (`newDirectoryStream`, DESIGN §4.2). A file
  uploaded into a subdirectory is browsable and downloadable but will never be consumed
  where it sits — that is what the `ingest` field on a live file node reports. If recursive
  ingest is ever wanted, that is a poller change with its own design, not a browse change.

## Conventions

Branch `feat/module-x12-x12`, PRs to `dev`. Conventional commits. Commit/push only when asked.
