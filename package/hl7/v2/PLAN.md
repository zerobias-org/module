# Build Plan — `@zerobias-org/module-hl7-v2`

Module-side implementation plan. Owner: Daniel. Platform updates
(daemon mode, listener ports, durability, opaque `config` passthrough) are
owned separately — see [`PLATFORM_UPDATES.md`](PLATFORM_UPDATES.md) — and this
plan is deliberately structured so that **none of the module work below
blocks on the platform work**, except the single live-Hub-Node E2E in
Phase 12.

Design canon: [`DESIGN.md`](DESIGN.md). Interface canon:
[`../../interface/dataproducer/documentation/`](../../interface/dataproducer/documentation/).
Runtime-shape reference: [`auditlogic/module/package/auditmation/generic/sql/`](../../../../auditlogic/module/package/auditmation/generic/sql/).

## Guiding principles

1. **Build in isolation, simulate the node.** Everything the Hub Node
   eventually does for us — inject `LISTENER_PORT_MLLP`, mount
   `/var/lib/module`, mount `EXTENSION_DIR` — is reproducible with a
   hand-written `docker run -p … -v …`. We never wait on the platform to
   exercise the module.
2. **Bottom-up by data-dependency.** Schemas → buffer → materializer →
   receiver → producer → functions → packaging. Each phase has a
   standalone test that needs nothing above it.
3. **Inherit the contract, implement the behavior.** The DataProducer
   operation set is `$ref`'d from
   `@zerobias-org/module-interface-dataproducer`. We never redefine it;
   we implement it in Java, mirroring the SQL module's
   `SqlModuleFacade` + `OperationRouter`.
4. **Generated content is checked in.** HL7 schemas + structure-index are
   produced at build time but committed, so diffs are reviewable and
   request-time has zero reflection.

## Contract seams — lock with Chris before Phase 6

These are the only points where the module and platform meet. Agree them
in writing first; everything else is independent.

| Seam | Module side | Platform side | Source |
|---|---|---|---|
| Env: `LISTENER_PORT_MLLP` | Java reads it, refuses to boot without it | `Container.ts` emits `LISTENER_PORT_${name.toUpperCase()}` | DESIGN §3.2, PLATFORM §5.1 |
| Env: `MODULE_CONFIG` | Java parses the opaque module `config` (JSON) | Node injects `runtimeConfig.config` verbatim | DESIGN §3.2, PLATFORM §5.1 |
| Env: `EXTENSION_DIR` | Java scans it at boot | **In-image path** (extensions baked at build); Node does NOT mount it | DESIGN §3.2, §7.3 |
| Extension delivery | declare extension packs as npm deps; build lays `extensions/` under `EXTENSION_DIR` | **none** — platform does not resolve/mount/catalog extensions | DESIGN §7.3 — **resolved 2026-06-01**: install-time/baked-in won; per-deployment mount dropped |
| Durability mount | `buffer.db` under `/var/lib/module` | mounts named volume there | DESIGN §3.1, §8 |
| `runtimeConfig.yml` file | we author it in the module root, declare in `package.json` `files`; mandatory (daemonMode) | dataloader reads → `module_version.runtime_config` | PLATFORM §6.3 |
| `runtimeConfig` schema | we declare defaults conforming to it (typed daemon/ports/durability + opaque `config`) | `DeploymentRuntimeConfig.yml` defines it | PLATFORM §1.1 |

## Target file tree (mirrors the SQL module)

```
hl7/v2/
├── package.json            implementationType: java-http; deps: interface-dataproducer, util-connector
├── build.gradle.kts        zb.java-module
├── api.yml                 $ref DataProducer paths + connect
├── connectionProfile.yml   hl7Version, ackDurability, backpressurePolicy, senderDiscriminator
├── nginx.conf  startup.sh  Dockerfile     (copied from sql, swapped)
├── src/index.ts            re-export generated types
└── java/
    ├── pom.xml             parent; exec-maven-plugin runs codegen before package
    ├── codegen/            BUILD-TIME ONLY — depends hapi-structures-v251
    │   └── …/SchemaGenerator.java
    └── src/main/
        ├── java/com/zerobias/module/hl7/
        │   ├── Hl7ApiServer.java  OperationRouter.java  Hl7ProducerFacade.java
        │   ├── listener/   Hl7ListenerService, BufferingApp, AckBuilder, MetricsConnectionListener
        │   ├── materializer/  Materializer, StructureIndex, normalize/
        │   ├── buffer/     BufferStore, BufferRow, LeaseManager, RetentionSweeper
        │   ├── filter/     Hl7SqlAdapter   (lifted from sql module + 2 extras)
        │   ├── schema/     SchemaRegistry, ObjectTree
        │   ├── ops/        Take/Ack/Release/Replay/Purge Function
        │   ├── ext/        ExtensionLoader
        │   ├── health/     HealthCheck
        │   └── ModuleConfig.java
        └── resources/
            ├── schemas/v251/            (generated, committed)
            └── structure-index/v251.json (generated, committed)
```

---

## Phases

Legend: 🟢 fully independent · 🔵 needs a contract seam agreed · 🔴 blocked on platform.

### Phase 0 — Scaffolding & build pipeline 🟢 ✅ *(done 2026-05-29)*
- ✅ Package config from the SQL module: `package.json` (`@zerobias-org/module-hl7-v2`,
  java-http, moduleId, interface-dataproducer dep), `build.gradle.kts`
  (`zb.java-module`), `tsconfig.json`, `src/index.ts`, `.gitignore`.
- ✅ `api.yml` ($ref's DataProducer paths + connect/disconnect/metadata/healthz),
  `connectionProfile.yml` (hl7Version/ackDurability/backpressurePolicy/senderDiscriminator).
- ✅ `runtimeConfig.yml` (module root, in `package.json` `files`) — daemonMode +
  listenerPorts[mllp] + durability + opaque `config` defaults; mandatory for this
  daemon module, read by dataloader → `module_version.runtime_config`. *(added
  2026-06-01 per platform correction; replaces the planned `auditmation.runtime`
  package.json block.)*
- ✅ Container: `Dockerfile` (temurin-17, VOLUME /var/lib/module, EXPOSE 8888 only,
  EXTENSION_DIR), `startup.sh` (refuses to boot without `LISTENER_PORT_MLLP`),
  `nginx.conf` (8888→8889, ops port only).
- ✅ Java: `java/pom.xml` (listener uber jar `hl7-listener-1.0.0.jar`,
  mainClass `Hl7ApiServer`, hapi-base/javalin/gson/sqlite-jdbc; lite-filter
  deferred to Phase 5; `regen-schemas` profile), `java/codegen/pom.xml`
  (build-time-only, hapi-structures-v251). Stubs: `Hl7ApiServer`, `ModuleConfig`,
  `SchemaGenerator`, `src/main/resources/README.md`.
- ✅ Verified: `ModuleConfig` + `SchemaGenerator` compile with javac; YAML/JSON parse clean.
- **Remaining to fully close (needs the repo build toolchain — mvn/gradle not on this box):**
  run `npm i` + `npm run generate` (emits `generated/` TS types from `api.yml`)
  and a full `gradle`/`mvn package` to confirm the uber jar builds end-to-end.

### Phase 1 — Build-time schema codegen 🟢 🚧 *(in progress, started 2026-05-29)*
- ✅ Generator implemented in `java/codegen/`:
  - Pure helpers — `SchemaIds` (id construction + canonical-pattern validation),
    `CoreTypes` (§2.4 HL7-primitive → core dataType + DataType definitions),
    `HapiNames` (parses HAPI positional accessors `getPid3_PatientIdentifierList`
    → `{3, patientIdentifierList}`, the contract-accurate property naming).
  - JSON model — `Schema`/`Property`/`Reference`/`DataType` (wire-ordered,
    nullable Booleans so false/absent fields are omitted) + `StructureIndex`
    (materializer driver: messages, groups, segments, datatypes layouts).
  - `StructureWalker` — composition-all-the-way-down traversal (message → segments
    /groups → composite datatypes → primitives), enum refs for ID/IS table-bound
    fields, dedup by simple name, cycle-safe.
  - `SchemaGenerator` — orchestration: appends the buffer envelope to message
    table schemas (§2.3), fills `dataTypes[]`, emits enum stubs + the shared
    `message-envelope`, writes the tree via pretty Gson.
- ✅ **Validated against real HAPI v2.5.1** (jars fetched from Maven Central, run
  with javac/java + the JUnit console): `PureHelpersTest` (4) + `StructureWalkerIT`
  (1, the §2.3 `ADT_A01 → PID → CX → HD → HL70203` traversal) pass. The generator
  runs end-to-end and emits **254 schemas, 0 non-canonical ids** for ADT_A01+ORU_R01
  (2 messages, 9 groups, 33 segments, 47 datatypes, 162 table stubs) + the shared
  envelope + structure-index. Spot-checked: CX/PID/ADT_A01 match the design.
- 🐞 **Bug found + fixed by this run:** HAPI emits a `getPidN_FieldReps()` count
  accessor (returns `int`) alongside the field accessor; reflection was picking
  it up nondeterministically, giving repeating fields `...Reps` names. Fixed by
  filtering positional accessors to those returning an HL7 `Type`.
- **Decision (2026-05-29):** the generated tree is a **build artifact, NOT
  committed** (git-ignored) — a deliberate deviation from DESIGN §6's "checked
  into git." Implication: the build/CI MUST run the generator before packaging
  so `schemas/`+`structure-index/` land in the jar's resources (runtime serves
  them from the classpath).
- 🐞 **CI-correctness fix (2026-06-01):** the codegen was wired only under the
  opt-in `-Pregen-schemas` profile while the tree is git-ignored — so the standard
  `mvn package` (what `zbb`/CI runs) would have shipped an uber jar with **no
  schemas**, silently degrading the producer + materializer (SchemaRegistry empty
  → EnvelopeMaterializer fallback) while CI stayed green. Moved the codegen exec to
  the **default build** (`generate-resources` phase); removed the profile. NOT
  locally exercisable (no mvn/gradle/zbb here) — the real gradle gate confirms it.
- **Remaining:** widen the message list beyond the `ADT_A01`/`ORU_R01` default
  to the target coverage (the walk discovers all transitively-referenced
  structures, so this is just the seed list).
- **Follow-up (flagged):** HL7 **table value-sets** are emitted as stubs — HAPI's
  structure jars carry the table *number* (captured) but not the code lists;
  populating `tables/HL7nnnn.json` values needs a table data source.
- Optional: republish generated schemas as `@zerobias-org/hl7-v2-schemas` (DESIGN §6).

### Phase 2 — Buffer (SQLite + WAL) 🟢 ✅ *(done & validated 2026-05-29)*
- ✅ `BufferStore` — opens SQLite, applies `buffer/schema.sql` (+ `auto_vacuum`
  before tables), `ackDurability` toggles `synchronous=NORMAL|FULL` (§8.1),
  `insert` = `ON CONFLICT(control_id) DO NOTHING` (ack-on-persist), counts,
  purge, deletion primitives. Timestamps stored as epoch-millis (correct
  ordering; avoids ISO lexicographic mis-sort).
- ✅ `LeaseManager` — `take` (select→mark in_flight→fetch, race-free via single
  synchronized connection; TTL default PT5M cap 1h; returns `Lease{leaseId,
  messages, remaining}`), `ack` (full + partial subset), `release`,
  `reclaimExpired` (TTL revert). DESIGN §8.2.
- ✅ `RetentionSweeper` — `sweep()` (maxAge + maxBytes via incremental_vacuum,
  acked-only) + scheduled `start(interval)`/`stop()`. DESIGN §8.3.
- ✅ **Validated against real SQLite (sqlite-jdbc 3.45):** `BufferStoreTest` —
  **8 tests pass** covering insert/dedup + round-trip, lease ordering, full +
  partial ack, release, TTL revert + re-take, purge (acked-only), retention by
  max-age (un-acked never evicted), schema-id filtering. `MutableClock` drives
  TTL/retention deterministically.
- **Deferred (not blocking):** `take`'s RFC4515 filter (Phase 5 — currently
  filters by schema-id only); a reader connection pool for browse concurrency
  (Phase 4/6); precise byte-bounded eviction is implemented but only the
  max-age path is unit-tested.

### Phase 3 — Materializer 🟢 ✅ *(done & validated 2026-06-01)*
- ✅ `materializer/Hl7Normalizer` — pure, verified (30 checks + `Hl7NormalizerTest`):
  HL7 DT/DTM/TM → ISO 8601 (precision-preserving, no fabricated midnight/zone),
  escape decoding (`\F\ \S\ \T\ \R\ \E\ \.br\ \Xhh\`, formatting toggles stripped),
  and the `""` explicit-null sentinel.
- ✅ Full `materializer/Materializer` (+ runtime `StructureIndex` POJO/loader, a
  twin of the codegen model that consumes the generated JSON). Walks a
  **generic-parsed** message against the index → complete typed JSON keyed by
  HAPI-bean names: per-segment objects (`msh`/`pid`/…), composites recursed
  (CX→HD, XPN→FN), repeating fields/segments arrayed, table-bound values emitted
  as raw codes (tagged not resolved), dates precision-preserving via the
  normalizer. Group nesting flattened to top-level segments (v1). Replaces
  `EnvelopeMaterializer` in `Hl7ApiServer` (which falls back to it if the index
  isn't on the classpath). `Hl7ListenerService` now pins `GenericModelClassFactory`
  — the index-driven walk requires generic parsing (typed messages expose group
  names, not segment codes), and production carries no typed jars anyway.
- ✅ **Validated against the REAL generated index** (`MaterializerIT`, 3 tests):
  manual-test.sh runs the codegen to emit `structure-index/v251.json` (exactly as
  the build does) and the test loads it + parses generically. §5 worked example
  (PID-3 CX→{IDNumber, assigningAuthority:{namespaceID,universalIDType},
  identifierTypeCode}; PID-5 XPN→{familyName:{surname},givenName}; PID-7
  dateTimeOfBirth→{time:"1980-01-01"}; PID-8 "M"); ORU round-trip (pid arrayed
  from its repeating group, two OBX arrayed, observationValue); explicit-null vs
  absent. **37 tests green** overall.
- 🐞 **Bug found by running it:** an under-delimited composite (e.g. `SMITH` for an
  FN-typed field, no `&` subcomponents) parses as a bare primitive, so keying
  composite-ness off the *parsed* type emitted a string. Fixed: drive
  composite-ness off the *index type* and map a bare value to the composite's
  first component → `familyName:{surname:"SMITH"}`, schema-consistent.
- **Doc discrepancies (impl is the contract, schemas agree):** real HAPI bean
  names are `IDNumber`/`namespaceID`/`dateTimeOfBirth` (not §5's idealized
  `idNumber`/`namespaceId`/`dateOfBirth`); `TS` is a composite in v2.5.1 so dates
  nest under `time`; precision-preserving dates (no fabricated `T00:00:00Z`).
  §5's flat literal is illustrative — the generated schemas are the contract, and
  the materializer matches them (same index).
- **Deferred:** numeric typing (NM/SI emitted as strings, not JSON numbers);
  group hierarchy is flattened (segments keyed at top level) rather than nested.

### Phase 4 — MLLP receiver / BufferingApp 🟢 ✅ *(done & validated 2026-05-29)*
- ✅ `Hl7ListenerService`: HAPI `DefaultHapiContext` + `noValidation` (lenient
  parse), `HL7Service` on the listener port, registers `*`/`*` → `BufferingApp`.
- ✅ `BufferingApp.processMessage`: Terser-extracts the envelope → materializes
  JSON → `buffer.insert` → **ack after commit** (`MSA|AA` only once durable;
  `MSA|AE` on failure → sender retries). Duplicate `controlId` deduped, still AA.
- ✅ `AckBuilder` (AA/AE via HAPI `generateACK`); interim `EnvelopeMaterializer`
  wired in (Phase 3 will replace with the index-driven walk).
- ✅ **Validated against real HAPI MLLP:** `Hl7ListenerIT` — a real client sends
  ADT^A01, asserts AA + echoed control id, the buffered row's envelope +
  normalized JSON (`dateOfBirth: 1980-01-01`), and duplicate dedup. Also
  runnable by hand: `java/scripts/manual-test.sh listener`.
- **Deferred (not blocking):** `MetricsConnectionListener`; mapping
  `connectionProfile.hl7Version` → schema slot (hardcoded `v251` for now);
  the `MSA|AE` failure path is implemented but not yet unit-tested; reading the
  port from `LISTENER_PORT_MLLP` env happens in `Hl7ApiServer` wiring (Phase 6).

### Phase 5 — lite-filter SQL adapter 🟢 ✅ *(done & validated 2026-05-29)*
- ✅ `filter/Hl7SqlAdapter` — NOT a verbatim lift. The SQL module's
  `SqlAdapter` reflects `Clause`/`Grouping` **private fields** and casts
  `expressions` to `Expression[]`; against this lite-filter version that field
  is a `List<Expression>`, so the verbatim cast would `ClassCastException`.
  Ours reflects the **public getters** (the classes are package-private but
  their getters return the public `ComparisonOperator`/`LogicalOperator`
  enums) and switches on the enums — same SQL, robust to AST shape. ANSI ops
  + `:contains:`/`:startsWith:`/`:endsWith:`/`:between:`/`:matches:`/`~=`,
  AND/OR/NOT, presence/null.
- ✅ HL7 extras (DESIGN §2.6): envelope props (`controlId`/`receivedAt`/
  `status`/`leaseId`) → real columns, everything else → `json_extract(
  mapped_json,'$.…')`; string `=` emitted `COLLATE NOCASE` to match the
  evaluator's case-insensitive default; date extensions emitted for
  **epoch-millis** storage (`received_at` is INTEGER): `:withinDays:N` →
  `received_at >= (unixepoch('now','-N days')*1000)`, `:year:Y` →
  `strftime('%Y', received_at/1000,'unixepoch')`, absolute
  `receivedAt>=2026-05-27` → `(unixepoch('2026-05-27')*1000)`.
  `filter/Hl7Filter` registers the adapter + parse/render facade;
  `BufferStore.search(where, limit)` is the read-only browse primitive.
- ✅ **Validated** (lite-filter compiled from source — see toolchain note —
  no GH auth needed): `Hl7SqlAdapterIT` 4 tests — 14-filter parity vs the
  in-memory `matches()` evaluator over a shared seeded buffer, the three §2.6
  examples executed on SQLite, the epoch-millis `:withinDays:` form, and a
  locked-in test of the parser constraint below. **18 tests green** overall.
- 🐞 **Finding (parser constraint, DESIGN §2.6 updated):** lite-filter's
  RFC4515 parser treats the first `:` as a `:function:` operator, so a full
  ISO datetime value (`2026-05-27T00:00:00Z`) in a `>=` clause is rejected
  (operator `:00:`). Use **date-only** `>=` (as the interface FilterSyntax.md
  documents) or `:withinDays:`/`:year:`. Also: the evaluator's `>`/`>=` are
  numeric-only and throw on date strings → absolute-date `>=` is
  **SQL-adapter-only** here (fine; the producer always pushes to SQLite). A
  fuller fix belongs in `org/util/lite-filter`.
- Decision (DESIGN §11.6): SQLite adapter lives **here** for v1 (the
  epoch-millis + json_extract specifics are module-local).

### Phase 6 — DataProducer HTTP surface 🔵 ✅ *(done & validated 2026-05-29)*
- ✅ **Wire contract discovered + locked:** the java-http module surface is NOT
  the literal api.yml `/objects` paths — nginx proxies everything to Javalin,
  which serves the SQL module's RPC envelope: `GET /`, `POST /connections`,
  `PUT /connections/{id}/disconnect`, `GET /connections/{id}/metadata`,
  `GET /connections/{id}/isSupported/{op}`, `POST /connections/{id}/{method}`
  (`{method}` = `ApiClass.methodName`, body `{argMap}`). api.yml is the logical
  contract for type-gen + the platform's operation catalog; the Hub Node's
  invoker maps operationIds onto that RPC route. (The SQL module's own api.yml
  likewise doesn't match its Javalin routes — same split.)
- ✅ `producer/OperationRouter` (mirrors SQL dispatch) + `Hl7ProducerFacade`
  (read ops), `ObjectTree` (DESIGN §2.1 hierarchy: root → `/hl7-v2-receiver` →
  `/messages`, `/by-type/<X>` from `SchemaRegistry`, `/by-sender/<X>` from
  distinct `sending_app`, `/stats` doc, `/ops` functions), `SchemaRegistry`
  (scans the generated tree, indexes by parsed `id`, serves `getSchema` +
  enumerates message structures), `ProducerException` (errorModelBase envelope
  `{key,template,timestamp,statusCode}` + subtype fields — see the fix below).
  `Hl7ApiServer` rewritten: boots the daemon (buffer + listener) and registers
  the RPC routes + exception handlers.
- 🐞 **Fixed (2026-06-01):** the error envelope first shipped as Errors.md's
  prose `{code,message,details}`, but the OpenAPI `errorModelBase` schema (and
  the reference SQL module) require `{key,template,timestamp,statusCode}` +
  `noSuchObjectError.{type,id}` / `illegalArgumentError.{msg}`. Rewrote to the
  schema shape; `Hl7ProducerIT.errorMapping` now asserts the full body, not just
  status. Keys: `err.no.such.object` (404), `err.unsupported.operation` /
  `err.illegal.argument` (400). When two interface docs disagree, the schema wins.
- ✅ `searchCollectionElements` is read-only over the buffer: collection scope
  (`message_structure`/`sending_app`) AND the Phase-5 RFC4515 filter, composed
  into `BufferStore.search(where, limit, offset)`. Write surface
  (`addCollectionElement`/update/delete) → 400 `UnsupportedOperationError`
  (receive-only). `invokeFunction` deferred to Phase 7. Error mapping:
  unknown object/schema → 404 `noSuchObjectError`, bad input → 400
  `illegalArgumentError`.
- ✅ **Validated:** `Hl7ProducerIT` — 8 tests through
  `OperationRouter.executeOperation` (the exact HTTP code path) against a real
  seeded SQLite buffer + a generated schema tree: root/containers,
  collection+size, by-type (from registry), by-sender (from buffer), scoped +
  filtered + json-path search, element-by-controlId, getSchema, and all four
  error mappings (404/400). **26 tests green** overall. `manual-test.sh
  producer` drives the read ops from the terminal.
- **Deferred:** the Javalin/Jetty route-glue itself is validated under the real
  gradle build (~30 lines mirroring the proven SQL module; the jetty+kotlin jar
  fan-out isn't worth fetching by hand). `sortBy`/`sortDir` accepted but
  ignored (received_at DESC default); `pageToken` cursor paging (offset only);
  `getDocumentData` for `/stats` (Phase 9).

### Phase 7 — Function objects 🟢 ✅ *(done & validated 2026-06-01)*
- ✅ `producer/Hl7Operations` — the five drain functions behind `invokeFunction`,
  thin I/O-shaping wrappers over the Phase-2 lease primitives:
  - `take` {filter?, max=100 cap 1000, leaseTtl=PT5M} → {leaseId, messages[],
    remaining}; the RFC4515 `filter` renders via the Phase-5 adapter and narrows
    the drainable candidates (new ∪ TTL-expired in_flight). New buffer primitive
    `takeWhere`.
  - `ack` {leaseId, controlIds?} → {acked}; partial subset supported (§11.2
    partial-ack-with-revert is the default).
  - `release` {leaseId, controlIds?} → {released}.
  - `replay` {filter?} → {replayed} — forces in_flight → new (consumer recovery),
    ignoring TTL. New primitive `replayInFlight`.
  - `purge` {olderThan=PT0S} → {purged} — deletes acked rows; default purges all
    acked.
- ✅ `Hl7ProducerFacade.invokeFunction` dispatches by the `/ops/<fn>` name,
  rejects non-function objects with `UnsupportedOperationError`, and serializes
  function output with `serializeNulls` so `take`'s required `leaseId` is present
  (JSON null) on an empty lease.
- ✅ **Validated:** `Hl7OperationsIT` — 8 tests through
  `OperationRouter.executeOperation` (the HTTP path) over a real seeded SQLite
  buffer: take→ack, take-with-filter, partial-ack, release→re-take,
  replay→re-take, purge-deletes-acked, empty-take (null leaseId present), and the
  error mappings. **34 tests green** overall. `manual-test.sh producer` now shows
  the take→ack→purge drain cycle.
- 🐞 **Two findings fixed by running it:** (1) `purge(PT0S)` deleted 0 — the
  delete used strict `acked_at < cutoff`, excluding rows acked at `now`; changed
  to `<=` so "older than 0" = all acked. (2) empty-take dropped the required
  `leaseId` field — `new Gson()` omits nulls; function output now uses
  `serializeNulls`.
- **Deferred (declared, not enforced in v1):** `take.throws`
  (`lease_capacity_exceeded`/`backpressure`) — no artificial outstanding-lease
  cap, and buffer-full backpressure is the receive path (§11.3 `MSA|AE`), not
  `take`. `ack`/`release` report affected-row counts rather than raising
  `lease_expired`/`not_found` (a 0 count is the signal). Function I/O **schemas**
  (`schema:function:hl7v2.ops.*`) are served once the codegen emits them.

### Phase 8 — Extension boot-loader 🟢 ✅ *(done & validated 2026-06-01)*
- ✅ `ext/ExtensionLoader` scans `EXTENSION_DIR/<pack>/extensions/manifest.json`
  (in-image, baked at build §7.3), filters to `MODULE_CONFIG.activeExtensions`
  (empty = all), validates HL7 version compat + no-dup-schema-id across packs
  (**no** namespace-ownership check — that rule doesn't exist), merges the pack's
  schemas into `SchemaRegistry` and its `structure-index.json` into the
  materializer `StructureIndex`, and returns the discriminators + per-pack health
  info. `ext/ExtensionManifest` (module-defined manifest shape: namespace,
  hl7Version, vendor, discriminators[]) + `ext/Discriminator` (record:
  matches(code,sender) for routing, whereClause() for by-type scope).
- ✅ Integration seams (kept decoupled — no `ext` import in producer/materializer,
  so no package cycle): `SchemaRegistry.mergeExtension` (dup-id → fail fast) +
  `messageStructureIds()` (namespace-agnostic name→id); `StructureIndex.merge`
  (base wins on collision); `Materializer` takes a `StructureResolver` (routes an
  EPIC ADT → `ADT_A01_with_ZPV`); `ObjectTree` takes `extensionScopes`
  (structure→discriminator WHERE) and lists by-type across all namespaces;
  `ModuleRuntimeConfig` parses `MODULE_CONFIG` for `activeExtensions`.
  `Hl7ApiServer` wires it all at boot; `HealthCheck.extensions[]` now populated.
- ✅ **Validated:** `ExtensionLoaderIT` (4) against the REAL base index — stages a
  sample `epic-adt` pack and asserts: merge surfaces
  `/by-type/ADT_A01_with_ZPV` (scoped by `message_code='ADT' AND sending_app='EPIC'`,
  not message_structure), an EPIC ADT routes to it and materializes the `ZPV`
  segment (`zpv.epicVisitId`), the activeExtensions filter excludes, duplicate
  schema-id across packs is rejected, version mismatch is rejected. **45 tests
  green** overall. No bugs surfaced on first run.
- **Deferred:** the actual `npm install` → `extensions-staging/` → image bake is a
  Dockerfile step (Phase 10). Discriminator key is MSH-3 only (§11.4 default;
  MSH-4 override not implemented). One image = one extension set (§7 tradeoff).

### Phase 9 — Health & MLLP self-test 🟢 ✅ *(done & validated 2026-06-01)*
- ✅ `health/HealthCheck` builds the DESIGN §9 `/healthz` payload from the buffer:
  `listener{up,lastReceived?,bufferDepth,oldestUnackedSec?}`, `db{walBytes}`,
  `extensions[]` (empty until Phase 8). Optional fields omitted when empty (no
  misleading zeros). New buffer metrics: `walBytes` (WAL sidecar file size),
  `lastReceivedMillis`, `oldestUnackedSeconds` (+ a NULL-aware query helper).
  `Hl7ApiServer` `/healthz` serves it: 200 healthy / 503 degraded, gated on the
  listener being up. Node-only (no platform event plumbing, per Kevin 2026-06-01).
- ✅ `health/HealthSelfTest` + `listener/HealthNoOpApplication`: at startup the
  module dials `127.0.0.1:<mllpPort>` with a synthetic `ZZZ^X01` and asserts AA.
  The no-op is registered for trigger `X01` **before** the wildcard (HAPI returns
  the first matching binding) so it intercepts the self-test without persisting.
  `Hl7ListenerService` now also pins `GenericModelClassFactory` (carried over from
  Phase 3). `Hl7ApiServer` runs the self-test after `listener.start()` and logs
  the result.
- ✅ **Validated:** `HealthCheckTest` (3, real SQLite — payload fields present/
  omitted, healthy/degraded gate) + `HealthSelfTestIT` (1, real HAPI MLLP — X01
  round-trips AA and does NOT persist). That the X01 binding doesn't shadow real
  messages is covered by `Hl7ListenerIT` (sends a real ADT^A01 through the same
  now-X01-bound listener, still buffers). **41 tests green** overall.
- 🐞 **Bug found by compiling:** javadoc `{@code */X01}` — the `*/` prematurely
  closed the block comment. Reworded to "X01-trigger". (Also pinned
  `-encoding UTF-8` in manual-test.sh for the em-dash comments.)
- **Deferred:** `db.lastCheckpoint` (we don't track WAL checkpoints); `extensions`
  populated by Phase 8.

### Phase 10 — Containerization 🔵 ✅ *(done & validated 2026-06-01)*
- ✅ `Dockerfile` finalized (DESIGN §3.1/§3.3): temurin-17-jre + nginx/openssl,
  copies the uber jar + `*.yml` + nginx.conf + startup.sh, `VOLUME /var/lib/module`
  (buffer only — comment corrected; extensions are NOT in the volume), `EXPOSE 8888`
  only (MLLP port deliberately not EXPOSE'd, §3.3), `ENV EXTENSION_DIR` + `mkdir`'d
  empty (base image ships no packs; a vendor image is a derived build that
  `npm install`s packs + `COPY extensions-staging/` — documented inline, §7.3).
  No `LISTENER_PORT_MLLP` default (§3.2).
- ✅ `startup.sh` (already scaffolded, now validated): refuses to boot without
  `LISTENER_PORT_MLLP`; HUB_NODE_INSECURE toggles HTTPS↔HTTP nginx blocks via the
  `INCLUDE_HTTP_SERVER` sed; cert-gen → nginx (bg) → java (fg) with a TERM/INT trap
  forwarding shutdown. `nginx.conf`: 8888 (ssl, default) → 127.0.0.1:8889; ops port
  only (MLLP not proxied).
- ✅ **Validated (no Docker daemon up locally, so logic-level + a real-container
  script):** `startup.sh` exercised with stubbed nginx/java/openssl — (A) refusal
  without the port (exit 1 + message), (B) the insecure-mode nginx rewrite produces
  a correct single HTTP server (ssl line commented, one active `default_server`),
  (C) secure path: cert → nginx → java launch order → SIGTERM → clean trap exit 0.
  `java/scripts/container-smoke.sh` does the full **real-container** check (build
  image with a JDK-HttpServer stub jar → run: refusal without port, then
  `/healthz` 200 through nginx) — runnable once the Docker daemon is up.
- **Deferred:** the full `docker run` with the **real** uber jar (needs the
  maven/gradle build) — that's Phase 11's job; smoke covers the plumbing.

### Phase 11 — Standalone E2E (simulated node) 🟢 ✅ *(done 2026-06-01)*
- ✅ `java/scripts/e2e-local.sh` (dev tool): builds the image, runs the real
  container (simulating the node — `docker run` with `LISTENER_PORT_MLLP`
  injected), sends a real ADT^A01 into the MLLP listener over the wire, then
  drives the full pipeline-style cycle over the DataProducer API:
  receive → ack-on-persist (`MSA|AA`) → materialize (typed JSON in `/messages`)
  → `take` → `ack` → `purge`, with `bufferDepth` 0→1→0. `hl7-live.sh` is the
  interactive variant. **Validated live** with the real shaded jar.
- 🐞 This is where the **executor-poisoning bug** was caught (commit 657d0b7) —
  only sending a real message to a running container after startup exposed it.
- **Deferred:** running it as the gate's own `testDocker` step needs the
  listener-port injection (see status note below); a packaged TS `test/e2e/` +
  `simhospital` feed is optional polish.

### Phase 12 — Live Hub Node E2E 🔴 *(blocked on PLATFORM_UPDATES §10 steps 1–9)*
- Real `EnsureDeployment` carrying `runtimeConfig`; node injects ports +
  `MODULE_CONFIG`, mounts the durable volume; daemon auto-start; `/healthz`
  polling (Node-local; failures raise Node alerts, not platform events).
  Extensions are already baked into the image — nothing extension-related to
  resolve at deploy time. (DESIGN §12 step 6, full version.)
- Coordinate go-time with Chris once the platform reaches its step 9.

### Phase 13 — Operator docs 🟢 ✅ *(done 2026-06-01)*
- ✅ `README.md` — overview, why-daemon, architecture, the DataProducer surface
  (collections + `/ops` functions), RFC4515 filtering, deployment via
  `runtimeConfig.yml`, connection profile, extensions, `/healthz`, and the
  local `manual-test.sh`/`container-smoke.sh` validation entry points.
- ✅ `USERGUIDE.md` — the §12-step-7 operator runbook in depth: (1) pinning the
  MLLP port (2575, 1:1 host map, refuses without it), (2) sizing the durable
  volume (retention evicts acked-only; size for peak undrained backlog;
  ackDurability), (3) reading `/healthz` (field-by-field; oldestUnackedSec = the
  backlog signal; Node-alert-not-restart), (4) baking + selecting extensions
  (derived-image `npm install`+`COPY`; `MODULE_CONFIG.activeExtensions`),
  (5) finding the ephemeral port (`docker port` / `LISTENER_PORT_MLLP` / node
  deployment view). Plus a draining how-to for pipeline authors (take→ack lease
  cycle). Grounded in the real bean names / ops I/O / config.

---

## Critical path & parallelism

```
Phase 0
  └─ Phase 1 (schemas) ──┐
  └─ Phase 2 (buffer) ───┼─ Phase 3 (materializer, needs 1)
                         ├─ Phase 5 (filter) ─┐
  Phase 4 (receiver, needs 2+3) ──────────────┤
                                              └─ Phase 6 (producer, needs 1,2,5)
                                                   └─ Phase 7 (functions)
                                                   └─ Phase 8 (extensions)
                                                   └─ Phase 9 (health)
                                                        └─ Phase 10 (container)
                                                             └─ Phase 11 (sim E2E)
                                                                  └─ Phase 12 (live E2E) 🔴
```
Phases 1, 2, and 5 can run concurrently right after Phase 0. The whole
left side (1–11) is independent of the platform track.

## Module-owner decisions to settle (DESIGN §11)

- **§11.2 lease semantics** — partial-ack-with-revert (current default) vs
  all-or-nothing. Resolve before Phase 7.
- **§11.3 backpressure** — default `reject` for clinical feeds; log the call.
- **§11.4 `/by-sender` discriminator** — default MSH-3, override via
  `connectionProfile.senderDiscriminator`. Resolve before Phase 6.
- **§11.6 date-extension adapter home** — here vs lite-filter. Phase 5.
- **§11.1 (ports) / §11.5 (version pinning)** — overlap the platform
  `runtimeConfig`/`connectionProfile` design; coordinate with Chris.

## Gate / CI status (verified 2026-06-01)

The real `./gradlew :hl7:v2:gate` was run end-to-end (maven + vault-sourced
GitHub Packages auth; see [`CLAUDE.md`](CLAUDE.md) for the setup). It clears:
**validate → lint → generate → transpile → mavenBuild (codegen + shade) →
45 tests (0 skipped) → buildImage → startModule (daemon boots healthy)** — and
stops only at **`dataloaderExec` (401)**, which needs the platform/dataloader-service
token `zbb` resolves from vault in CI (not a code issue).

Running the gate (and a real container) surfaced — and fixed — a series of CI-fit
defects the hand-harness had masked: codegen wired into the default build (not an
opt-in profile); `types-core` peer major-bump; `x-product-infos` + product deps;
`axios` pin; the `lite-filter` dep + GitHub-Packages `<repositories>` block (was
commented out); schemas served from the **classpath** not a filesystem dir; the
nginx insecure-mode rewrite; and the **executor-poisoning** bug (self-test killed
the listener). All committed on `hl7`.

**Remaining, both platform-side (NOT module code):**
1. **Listener-port injection** — the gate's `startModule` (and the live Hub Node)
   must read `runtimeConfig.listenerPorts` and inject `LISTENER_PORT_MLLP`. Gate
   side = `org/util` **PR #86** (`feat/daemon-listener-ports`, build-tools
   `DockerRunner`); live side = `com/hub/node-lib/Container.ts` (PLATFORM_UPDATES
   §5.1). Validated locally via `publishToMavenLocal` — with it, `startModule`
   boots the daemon and the gate advances.
2. **`dataloaderExec` token** — supplied by CI/`zbb`.

## Definition of done (module v1)

Receive → durable buffer → ack-on-persist → typed-JSON DataProducer
surface → `take`/`ack` drain → extensions by namespace → `/healthz`, all
green in Phase 11's simulated-node E2E, with Phase 12 wired the moment the
platform track lands.
