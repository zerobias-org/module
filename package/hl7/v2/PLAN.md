# Build Plan — `@auditlogic/module-hl7-v2`

Module-side implementation plan. Owner: Daniel. Platform updates
(daemon mode, listener ports, durability, extension resolution) are owned
separately — see [`PLATFORM_UPDATES.md`](PLATFORM_UPDATES.md) — and this
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
| Env: `EXTENSION_DIR` | Java scans it at boot | Node sets it after mounting | DESIGN §3.2 |
| Extension mount layout | scan `/opt/module/extensions/*/extensions/manifest.json` | per-artifact subdir: `-v <dir>:/opt/module/extensions/<name>:ro` | DESIGN §7.3 / PLATFORM §5.1 — **resolved 2026-05-29**: one subdir per extension; PLATFORM §5.1 was correct, DESIGN step 3 fixed |
| Durability mount | `buffer.db` + `extensions/` under `/var/lib/module` | mounts named volume there | DESIGN §3.1, §8 |
| `auditmation.runtime` block | we author it in `package.json` | dataloader reads → `module_version.runtime_config` | PLATFORM §6.3 |
| `runtimeConfig` schema | we declare defaults conforming to it | `DeploymentRuntimeConfig.yml` defines it | PLATFORM §1.1 |

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
- ✅ Package config from the SQL module: `package.json` (`@auditlogic/module-hl7-v2`,
  java-http, moduleId, interface-dataproducer dep), `build.gradle.kts`
  (`zb.java-module`), `tsconfig.json`, `src/index.ts`, `.gitignore`.
- ✅ `api.yml` ($ref's DataProducer paths + connect/disconnect/metadata/healthz),
  `connectionProfile.yml` (hl7Version/ackDurability/backpressurePolicy/senderDiscriminator).
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
  them from the classpath). The listener pom's `regen-schemas` profile currently
  shells to the codegen; wire it (or a CI step) into the normal build when the
  toolchain is available, and confirm the resources end up in `target/classes`.
- **Remaining:** widen the message list beyond the `ADT_A01`/`ORU_R01` default
  to the target coverage (the walk discovers all transitively-referenced
  structures, so this is just the seed list).
- **Follow-up (flagged):** HL7 **table value-sets** are emitted as stubs — HAPI's
  structure jars carry the table *number* (captured) but not the code lists;
  populating `tables/HL7nnnn.json` values needs a table data source.
- Optional: republish generated schemas as `@auditlogic/hl7-v2-schemas` (DESIGN §6).

### Phase 2 — Buffer (SQLite + WAL) 🟢 🚧 *(foundation laid 2026-05-29)*
- ✅ Foundation (pure, compile-verified): `buffer/schema.sql` (the §8 DDL +
  indexes + WAL pragmas, verbatim, as a committed resource), `MessageStatus`
  enum (wire `new|in_flight|acked` + `fromWire`), `BufferRow` record.
- **Remaining (needs sqlite-jdbc to test):**
  - `BufferStore`: load `schema.sql`, `insert` = `ON CONFLICT(control_id) DO NOTHING`.
  - `LeaseManager`: the `BEGIN IMMEDIATE … UPDATE … RETURNING` drain
    (DESIGN §8.2); TTL default PT5M, cap 1h.
  - `RetentionSweeper`: 10-min thread, `maxBytes`/`maxAge` whichever fires first (§8.3).
  - `ackDurability` toggles `synchronous=NORMAL|FULL` (§8.1).
- **Done when:** unit tests cover insert/dedup, take→lease, ack, release,
  TTL revert, purge, retention eviction — against a temp SQLite file, no HL7.

### Phase 3 — Materializer 🟢 🚧 *(normalization kernel done 2026-05-29)*
- ✅ `materializer/Hl7Normalizer` — pure, verified (30 checks + `Hl7NormalizerTest`):
  HL7 DT/DTM/TM → ISO 8601 (precision-preserving, no fabricated midnight/zone),
  escape decoding (`\F\ \S\ \T\ \R\ \E\ \.br\ \Xhh\`, formatting toggles stripped),
  and the `""` explicit-null sentinel.
- **Remaining (needs HAPI generic parser + generated structure-index from Phase 1):**
  `Materializer` walks a generic-parsed message against `StructureIndex` → typed
  JSON with HAPI-bean field names; composites recurse (CX→HD); tables tagged,
  not resolved.
- **Done when:** the §5 worked example (`PID|||5551212^^^EPIC…`) produces the
  exact JSON shown; round-trips for ADT_A01 + ORU_R01 fixtures.

### Phase 4 — MLLP receiver / BufferingApp 🟢
- `Hl7ListenerService`: HAPI `DefaultHapiContext` (allowUnknownVersions,
  non-validating), `HL7Service` on `LISTENER_PORT_MLLP`, register `*`/`*`.
- `BufferingApp.processMessage`: Terser-extract envelope fields →
  `materializer.toTypedJson` → `bufferStore.insert` → **ack after commit**
  (`MSA|AA` only once durable; `MSA|AE` on failure). (DESIGN §4.2)
- `AckBuilder`, `MetricsConnectionListener`.
- **Done when:** a synthetic MLLP client sends ADT/ORU; assert buffer rows,
  typed JSON, `AA`/`AE` semantics, and duplicate-controlId silent-drop.
  For standalone runs, default `LISTENER_PORT_MLLP` via test config.

### Phase 5 — lite-filter SQL adapter 🟢
- Lift `SqlAdapter.java` from
  [`…/generic/sql/java/…/SqlAdapter.java`](../../../../auditlogic/module/package/auditmation/generic/sql/java/src/main/java/com/zerobias/module/sql/SqlAdapter.java)
  verbatim (ANSI ops, `:contains:`/`:startsWith:`/`:endsWith:`/`:between:`).
- Add the two HL7 extras (DESIGN §2.6): nested-composite JSON paths via
  `json_extract(mapped_json, '$.…')`, and `:withinDays:`/`:year:` →
  SQLite-native `datetime('now','-N days')` / `strftime`.
- **Done when:** parity tests vs lite-filter's in-memory `matches()`
  evaluator; the three §2.6 example filters compile and run on SQLite.
- Decision (DESIGN §11.6): does the SQLite adapter live here or get
  contributed back to lite-filter? Default: here for v1.

### Phase 6 — DataProducer HTTP surface 🔵 *(seam: contract dep `module-interface-dataproducer`)*
- `Hl7ApiServer` (Javalin, internal 8889) + `OperationRouter` mirroring
  the SQL module; `Hl7ProducerFacade` implements the read ops.
- `SchemaRegistry` serves `/schemas/{id}` from classpath. `ObjectTree`
  builds the hierarchy (DESIGN §2.1): root → `/hl7-v2-receiver` →
  `/messages`, `/by-type/<X>`, `/by-sender/<X>`, `/stats`, `/ops`.
- `searchCollectionElements` is read-only over the buffer, using the
  Phase-5 filter; `addCollectionElement` on `/messages` → 400
  `UnsupportedOperationError` (DESIGN §2.7).
- Error mapping per DESIGN §2.7 / `Errors.md`.
- **Done when:** HTTP tests cover getRootObject/getObject/getChildren/
  searchCollectionElements/getSchema against a seeded buffer.

### Phase 7 — Function objects 🟢
- `ops/take|ack|release|replay|purge` behind `invokeFunction`, with
  input/output schemas (DESIGN §2.5). `take` = lease primitive (wraps
  Phase 2); `ack` supports partial controlId subsets; declared `throws`
  (`lease_capacity_exceeded`, `backpressure`, `lease_expired`, `not_found`).
- **Done when:** take→ack and take→TTL-revert round-trips through the HTTP
  surface; backpressure policy honored (DESIGN §11.3 default `reject`).

### Phase 8 — Extension boot-loader 🔵 *(seam: extension mount layout)*
- `ExtensionLoader` scans `EXTENSION_DIR`, validates (namespace ownership,
  HL7 version compat, SchemaId format, no dup IDs), merges schemas +
  structure-index, registers extra `/by-type/<name>` objects (DESIGN §7.3
  "Module at boot"). Discriminator rules from `manifest.json` (DESIGN §7.2).
- **Done when:** mounting a sample `@auditlogic/hl7-extensions-epic-adt`
  dir by hand surfaces `/by-type/ADT_A01_with_ZPV` and routes EPIC ADT to
  it. (The deploy-time *tarball pull* is platform — not tested here.)

### Phase 9 — Health & MLLP self-test 🟢
- `/healthz` on the ops port with the DESIGN §9 payload (listener up,
  bufferDepth, oldestUnacked, WAL bytes, extensions loaded).
- Startup MLLP self-test: dial `127.0.0.1:$LISTENER_PORT_MLLP`, send a
  `*/X01` no-op that never persists; confirms the receive loop end-to-end.

### Phase 10 — Containerization 🔵 *(seam: env contract)*
- Finalize `Dockerfile` (DESIGN §3.1 — note: MLLP port **not** EXPOSE'd,
  §3.3), `startup.sh` (copy sql, add `LISTENER_PORT_MLLP` read +
  `EXTENSION_DIR` scan), `nginx.conf` (8888→8889).
- **Done when:** `docker run -p 8888:8888 -p 2575:2575 -e
  LISTENER_PORT_MLLP=2575 -v hl7-buffer:/var/lib/module …` boots both
  subsystems and serves `/healthz`.

### Phase 11 — Standalone E2E (simulated node) 🟢
- TS e2e in `test/e2e/`: launch the container by hand (the `docker run`
  above), drive a `simhospital`/HAPI test feed into MLLP, run the
  pipeline-style `take`→`ack` cycle over HTTP, load one extension via a
  mounted dir. This is DESIGN §12 step 6 **minus** the real node.
- **Done when:** full receive→buffer→take→ack→purge cycle passes against
  the running container with zero platform code present.

### Phase 12 — Live Hub Node E2E 🔴 *(blocked on PLATFORM_UPDATES §10 steps 1–11)*
- Real `EnsureDeployment` carrying `runtimeConfig`; node injects ports,
  mounts volume, pulls + mounts extensions; daemon auto-start; `/healthz`
  polling drives deployment status. (DESIGN §12 step 6, full version.)
- Coordinate go-time with Chris once the platform reaches its step 11.

### Phase 13 — Operator docs 🟢
- USERGUIDE / README: pinning the listener port, sizing the durability
  volume, declaring extensions, reading `/healthz`, finding the ephemeral
  listener port when unpinned (DESIGN §12 step 7).

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

## Definition of done (module v1)

Receive → durable buffer → ack-on-persist → typed-JSON DataProducer
surface → `take`/`ack` drain → extensions by namespace → `/healthz`, all
green in Phase 11's simulated-node E2E, with Phase 12 wired the moment the
platform track lands.
