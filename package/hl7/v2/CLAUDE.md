# CLAUDE.md — working on `@zerobias-org/module-hl7-v2`

Guidance for AI sessions working on this package. Read this first; it captures the
non-obvious operational knowledge. For the *what/why* see [`README.md`](README.md)
(purpose), [`DESIGN.md`](DESIGN.md) (canon), [`PLAN.md`](PLAN.md) (live phase
status), [`USERGUIDE.md`](USERGUIDE.md) (operators), [`PLATFORM_UPDATES.md`](PLATFORM_UPDATES.md)
(platform-side dependencies, owned by Chris).

## What this is (and the #1 thing to know)

An **always-on HL7 v2.x MLLP receiver** exposed as a **DataProducer**: hospital
systems push HL7 over MLLP → durable SQLite buffer (ack-on-persist) → typed JSON →
the platform drains via `take`/`ack`. It's a **daemon** (always listening), unlike
every other demand-driven Hub module.

> **CRITICAL:** This is a **Java daemon module**. It does **NOT** follow the
> Yeoman / OpenAPI-connector **agent workflow** that `org/module/CLAUDE.md`
> describes (api-researcher, gates 1–6, TypeScript connector scaffolding). That
> workflow is for TypeScript connectors. Here the implementation is **Java**
> (`java/`), built by the `zb.java-module` gradle plugin → a maven uber jar →
> Docker image. Ignore the connector-agent gates; use the build/validation flow below.

## Layout

```
api.yml              DataProducer paths ($ref'd from the interface) + connect/healthz; x-product-infos
connectionProfile.yml  hl7Version / ackDurability / backpressurePolicy / senderDiscriminator
runtimeConfig.yml    daemonMode + listenerPorts[mllp] + durability + opaque config (read by dataloader)
Dockerfile  nginx.conf  nginx-insecure.conf  startup.sh    container (nginx → java on 8889)
java/
├── pom.xml          uber jar (maven-shade); codegen runs at generate-resources (NOT a profile)
├── codegen/         BUILD-TIME ONLY (depends hapi-structures-v251); emits schemas/ + structure-index
└── src/main/java/com/zerobias/module/hl7/
    ├── Hl7ApiServer.java     entry point: boots daemon (buffer+listener) + Javalin RPC routes
    ├── ModuleConfig.java / ModuleRuntimeConfig.java   env (LISTENER_PORT_MLLP, MODULE_CONFIG)
    ├── listener/    Hl7ListenerService, BufferingApp, AckBuilder, HealthNoOpApplication
    ├── materializer/ Hl7Normalizer, Materializer, StructureIndex, EnvelopeMaterializer, StructureResolver
    ├── buffer/      BufferStore, LeaseManager, RetentionSweeper, BufferRow, Lease, MessageStatus
    ├── filter/      Hl7SqlAdapter, Hl7Filter   (RFC4515 → SQLite)
    ├── producer/    OperationRouter, Hl7ProducerFacade, ObjectTree, SchemaRegistry, Hl7Operations, ProducerException
    ├── ext/         ExtensionLoader, ExtensionManifest, Discriminator
    └── health/      HealthCheck, HealthSelfTest
```

Data path: MLLP → `BufferingApp` → `Materializer` (typed JSON) → `BufferStore` →
ack. Drain: `OperationRouter` → `Hl7ProducerFacade`/`Hl7Operations` over the buffer.

## Wire contract (gotcha)

The module does **not** serve the literal `/objects` paths in `api.yml`. nginx
proxies everything to Javalin, which serves the SQL-module RPC envelope:
`POST /connections/{id}/{method}` with body `{argMap}`, where `{method}` is
`ApiClass.methodName` (e.g. `ObjectsApi.getRootObject`, `FunctionsApi.invokeFunction`).
`api.yml` is the logical contract for type-gen + the operation catalog; the Hub Node
maps operationIds onto the RPC route. (The reference SQL module's api.yml likewise
doesn't match its Javalin routes.)

## Validating changes

**Run the JUnit suite after a change** — it's the canonical test surface (45 tests:
buffer, materializer, listener, filter, producer, ops, health, extensions):

```bash
(cd java && mvn test)        # unit; `mvn verify` adds integration (failsafe). Needs GitHub Packages auth (below).
# or via the gate task:  cd <repo-root> && ./gradlew :hl7:v2:test
```

For container-level exploration (need Docker + a built jar): `java/scripts/e2e-local.sh`
(one-shot receive→drain E2E) and `java/scripts/hl7-live.sh up|send|peek|take|ack|purge|…`
(interactive). These are optional dev tools, not CI.

### The real gate (source of truth)

The hand harness is fast but masks CI-fit issues — **the gate is the truth.** It
needs maven, the gradle wrapper, and GitHub Packages auth. Setup (the env this
sandbox lacked):

```bash
# 1. build creds from vault (zb plugins + lite-filter live in GitHub Packages)
export GITHUB_ACTOR="$(vault kv get -field=username operations-kv/ci/github)"
export READ_TOKEN="$(vault kv get -field=readPackagesToken operations-kv/ci/github)"
# 2. maven (often not installed): brew install maven
# 3. ~/.m2/settings.xml — server id 'github', username ${env.GITHUB_ACTOR}, password ${env.READ_TOKEN}
#    (env-interpolated, so NO secret on disk). Needed for com.zerobias:lite-filter.
# 4. npm auth: ~/.npmrc already has GitHub Packages + pkg.zerobias.org tokens.

cd <repo-root> && ./gradlew :hl7:v2:gate          # gradle path is :hl7:v2 (auto-discovered)
# or individual tasks: mavenBuild | mavenTestUnit | mavenTestIntegration | validate | buildImage
```

`gh` PRs need `GH_TOKEN="$(vault kv get -field=prToken operations-kv/ci/github)"` (or
`gh auth login`).

## Things that will bite you (all fixed once — don't reintroduce)

- **Generated schemas are git-ignored build artifacts** (`schemas/`, `structure-index/`
  under `java/src/main/resources`). The codegen runs in the **default** maven build
  (`generate-resources`), bundled into the jar. The runtime serves them from the
  **classpath** (`SchemaRegistry.fromClasspath` / `StructureIndex.fromClasspath`),
  NOT a filesystem dir. Don't commit the tree; don't make codegen opt-in again.
- **Generic parsing is mandatory.** `Hl7ListenerService` pins `GenericModelClassFactory`;
  the `Materializer` walks a generic message against the index (typed messages expose
  group names, not segment codes). Tests parse with `GenericModelClassFactory` too.
- **HAPI's shared ExecutorService is a trap.** `DefaultHapiContext` shares a singleton
  executor; closing one context shuts it down for all. The listener (and the self-test
  client) each set a **dedicated** executor. If you add another HAPI client, give it
  its own executor. (This bug made the listener silently die after startup.)
- **Real bean names ≠ DESIGN §5's idealized ones.** Materialized JSON uses HAPI's
  actual names: `IDNumber`, `namespaceID`, `dateTimeOfBirth` (TS is a composite →
  dates nest under `time`). The schemas match; §5's `idNumber`/`surname` are illustrative.
- **Error envelope = OpenAPI `errorModelBase`** (`{key, template, timestamp, statusCode}`
  + `noSuchObjectError.{type,id}` / `illegalArgumentError.{msg}`), NOT the
  `{code,message,details}` shown in `Errors.md` prose. When interface docs disagree,
  the **schema** (dist/*.yml) wins.
- **nginx**: two committed configs (`nginx.conf` HTTPS, `nginx-insecure.conf`); `startup.sh`
  selects one — never rewrite nginx.conf with sed at runtime.
- **package.json**: keep `types-core`/`types-core-js` peers in sync with
  `module-interface-dataproducer` (major bumps break npm install); keep the `axios` pin
  (dedupes a transitive-version clash); keep `x-product-infos` +
  `product-auditmation-generic-dataproducer` (the generic DataProducer product, legacy
  name but the only one) + `product-hl7-hl7` (else `dereferenceProductInfos` → no full.yml).
- **pom**: `lite-filter` dep + the `maven.pkg.github.com/zerobias-org/util` `<repositories>`
  block must both be present.

> **Meta-lesson:** every one of the above was found by **running against real deps
> (the gate, a real container, a real MLLP send)** — not by reading code. After a
> change, run the suite; for anything touching the container/listener, send a real
> message to a running container (`e2e-local.sh`), not just unit tests.

## Platform dependencies — NOT this module's job

Two things the daemon needs that live elsewhere; do **not** hack the module to work
around them:

1. **`LISTENER_PORT_MLLP` injection.** The thing starting the container must read
   `runtimeConfig.listenerPorts` and inject the port. Gate/CI side = `org/util`
   build-tools (`DockerRunner` / `startModuleExec`) — **PR #86**
   (`feat/daemon-listener-ports`). Live node side = `com/hub/node-lib` `Container.ts`
   (PLATFORM_UPDATES §5.1, Chris). The module **correctly refuses to boot without the
   port** (DESIGN §3.2) — do not add a Dockerfile default or soften `startup.sh` to
   game the gate.
2. **Dataloader gate step** (`dataloaderExec` → 401 locally) needs the
   platform/dataloader-service token that `zbb` supplies from vault in CI.

## Conventions

- Work on the **`hl7`** branch (never `main`); build-tools changes go on a feature
  branch in `org/util`. Commit/push only when asked.
- Commit footer: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- `PLAN.md` is the live phase-status source of truth — update it when a phase moves.
- Owner: Daniel builds the module; Chris owns `PLATFORM_UPDATES.md`.
