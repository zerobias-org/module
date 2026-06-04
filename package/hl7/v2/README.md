# `@zerobias-org/module-hl7-v2` — HL7 v2.x MLLP Receiver

An **always-on HL7 v2.x receiver** exposed through the standard **DataProducer**
interface. Hospital / lab / ADT systems push messages over MLLP; the module
persists them to a durable buffer, materializes them to typed JSON, and the Hub
pipeline drains them via the same container/collection/function model every other
DataProducer uses.

> Design canon: [`DESIGN.md`](DESIGN.md) · Build plan + phase status: [`PLAN.md`](PLAN.md) ·
> Operator runbook: [`USERGUIDE.md`](USERGUIDE.md) · Working on the code? [`CLAUDE.md`](CLAUDE.md) ·
> Platform-side dependencies: [`PLATFORM_UPDATES.md`](PLATFORM_UPDATES.md) ·
> Interface canon: [`../interface/dataproducer/documentation/`](../interface/dataproducer/documentation/).

## Core purpose

ZeroBias collects compliance evidence from API-enabled systems. **Hospitals don't
work that way** — their clinical systems (EHRs, labs, ADT feeds) emit **HL7 v2.x**
messages over **MLLP**, pushing them out on their own schedule rather than exposing
a REST API to poll. This module is the bridge: it makes a stream of pushed HL7
messages look, to the rest of the platform, like an ordinary pollable
**DataProducer**.

Concretely, it is **the ZeroBias ingestion endpoint for HL7 v2**. Its one job:

1. **Accept** HL7 v2 messages over MLLP, continuously, on a fixed port.
2. **Never lose one** — persist to a durable buffer and only then send the `MSA|AA`
   the sending system is waiting for (ack-on-persist).
3. **Normalize** the cryptic pipe-encoded ER7 into clean, typed JSON (`pid.patientName[0].familyName.surname`, ISO dates, …).
4. **Offer** the messages to the platform's pipeline through the standard
   DataProducer surface, with an at-least-once **lease/drain** cycle.

What it is **not**: it is not a parser library, not a router/forwarder, and not a
write target — it's receive-only (messages enter via MLLP, never via the API). The
*module* ends at "durably buffered and drainable"; *what* consumes the messages,
*how often*, and *where they go* is a separate platform pipeline/collector config
(see [Consuming](#how-a-pipeline-consumes-it) below).

## Why this module is different

Every other Hub module is demand-driven: the pipeline calls `connect()`, the node
starts the container, operations run, it idles out. HL7 inverts the call
direction — EHR systems dial **in**, on their own schedule, expecting an immediate
MLLP `MSA|AA` on the same TCP connection. So this module is **daemon-mode**:

- **always reachable** on a known listener port (sources are statically configured),
- **durable** across pipeline-call gaps (messages arrive between drains),
- **ack-on-persist** — an `AA` is sent only once the message is safely on disk, so a
  successful ACK survives a container restart.

From the pipeline's view it then behaves like any other DataProducer.

## Architecture

```
single container
├── nginx                      0.0.0.0:8888  → 127.0.0.1:8889   (operations, Hub-facing)
└── java -jar hl7-listener.jar
      ├── DataProducer HTTP     127.0.0.1:8889                   (browse / drain / schemas)
      ├── HAPI MLLP listener    0.0.0.0:$LISTENER_PORT_MLLP      (receives HL7, ACKs)
      └── SQLite buffer (WAL)   /var/lib/module/buffer.db        (durable, single writer)
```

One process, one buffer. The MLLP port is **not** fronted by nginx — sending
systems speak MLLP directly to it. Only the operations port (8888) is.

## The DataProducer surface

```
/                                       container (root)
└─ /hl7-v2-receiver                      container
   ├─ /messages                          collection — every buffered message (heterogeneous)
   ├─ /by-type/<STRUCTURE>               collection — one per message structure (ADT_A01, ORU_R01, …)
   ├─ /by-sender/<APP>                   collection — filtered by sending application (MSH-3)
   ├─ /stats                             document   — backlog metrics
   └─ /ops                               container
      ├─ /ops/take      function — lease un-acked messages (returns + marks in_flight)
      ├─ /ops/ack       function — finalize a lease (durably consume)
      ├─ /ops/release   function — return a lease early, unconsumed
      ├─ /ops/replay    function — re-mark in_flight rows as new (consumer recovery)
      └─ /ops/purge     function — delete acked rows older than a duration
```

- **Browsing** (`searchCollectionElements`) is **read-only** — analytics and inspection.
- **Draining** is the `ops/take` → `ops/ack` cycle (at-least-once, lease-based).
- Messages are materialized to typed JSON keyed by HAPI bean names, e.g.
  `pid.patientIdentifierList[].IDNumber`, `pid.patientName[].familyName.surname` —
  see [`DESIGN.md` §5](DESIGN.md). Schemas are served from `/schemas/{id}`.

### Filtering

Collections accept **RFC4515** filters (the platform-canonical syntax). Attribute
names are schema property names (dotted into the typed body), not HL7 positions:

```
(&(msh.sendingApplication.namespaceID=EPIC)(msh.messageType.messageCode=ADT)(status=new))
(pid.patientIdentifierList.IDNumber=5551*)
(receivedAt>=2026-05-27)          # date-only; see DESIGN §2.6 on time-of-day
```

## How a pipeline consumes it

The module has **two independent halves** that the durable buffer decouples:

```
HOSPITAL SIDE (push in)                         PLATFORM SIDE (pull out)
EHR / lab / ADT system  ── MLLP ──▶  [ buffer ]  ──DataProducer ops──▶  pipeline
(continuous, their schedule)                     (scheduled, via the Hub)
```

- **Receiving** is driven by the hospital systems — configured to dial the listener
  port; nothing on the ZeroBias side triggers it.
- **Consuming** is a **scheduled pipeline / collector bot** on the platform that, each
  run, calls (through Hub Server → Hub Node → this module's RPC):
  `connect` → `ops/take` (lease a batch of typed messages) → process them (e.g. load
  into AuditgraphDB) → `ops/ack` (confirm). A crash before `ack` simply re-delivers
  on the next run — at-least-once, no lost data.

The drain cadence, filter, and destination live in **that pipeline config, not in
this module**. The local tools below stand in for both halves so you can exercise
the whole flow by hand.

## Deployment

This is a daemon module — its deployment shape is declared in
[`runtimeConfig.yml`](runtimeConfig.yml) (read by the dataloader at publish time)
and carried on `EnsureDeployment`:

```yaml
daemonMode: true
listenerPorts:
  - { name: mllp, protocol: tcp, port: 2575 }      # pin for statically-configured feeds; null = ephemeral
durability:
  - { volumeName: hl7-buffer, mountPath: /var/lib/module,
      retention: { maxBytes: 10737418240, maxAge: P7D } }
config:                                            # opaque to the platform (MODULE_CONFIG)
  activeExtensions: []                             # empty = all baked-in packs
  hl7Version: "2.7"
```

The Hub Node injects `LISTENER_PORT_MLLP`, maps the port 1:1 (no NAT), mounts the
durable volume, and passes `config` verbatim as `MODULE_CONFIG`. See
[`USERGUIDE.md`](USERGUIDE.md) for pinning ports, sizing the volume, baking
extensions, and reading health.

### Connection profile

[`connectionProfile.yml`](connectionProfile.yml) tunes the always-on listener (there
is no remote system to dial):

| Field | Meaning |
|---|---|
| `hl7Version` | target version for materialization (default `2.7`) |
| `ackDurability` | `normal` (fsync at WAL checkpoint) or `full` (fsync per row, zero-loss) |
| `backpressurePolicy` | what to do when the buffer fills — default `reject` (`MSA|AE`, sender retries) |
| `senderDiscriminator` | `/by-sender` key — default MSH-3 |

## Extensions (vendor / customer content)

Z-segments, custom tables, and augmented structures ship as ordinary npm packages
(`@zerobias-org/hl7-extension-<vendor>-<name>`) **baked into the image at build
time** — not resolved by the platform. At boot the module scans `EXTENSION_DIR`,
merges their schemas + structure index, and surfaces extra `/by-type/<X>`
collections (e.g. `/by-type/ADT_A01_with_ZPV`). `MODULE_CONFIG.activeExtensions`
selects among baked-in packs. See [`DESIGN.md` §7](DESIGN.md) and the
[USERGUIDE](USERGUIDE.md#extensions).

## Health

`GET /healthz` (operations port) returns the daemon's state — the Hub Node polls it
every 30s:

```json
{ "listener": { "up": true, "lastReceived": "…", "bufferDepth": 142, "oldestUnackedSec": 3 },
  "db":       { "walBytes": 12345 },
  "extensions": [ { "artifact": "hl7-extension-epic-adt", "schemasLoaded": 7 } ] }
```

`200` healthy, `503` degraded. A failing probe raises a **Node alert** (it does not
feed the platform event system); the listener port stays open and restart is
operator-driven.

## Local development & validation

(Working on the code? Read [`CLAUDE.md`](CLAUDE.md) first.)

**Tests** — the JUnit suite (45 tests) is the canonical surface, run via maven/gradle:

```bash
(cd java && mvn test)                  # unit tests; `mvn verify` adds the integration (failsafe) tests
cd <repo-root> && ./gradlew :hl7:v2:test    # the same via the gate's task (needs GitHub Packages auth — see CLAUDE.md)
```

**Run the real container** (needs Docker + the built jar):

```bash
(cd java && mvn -DskipTests package)   # build the uber jar (needs GitHub Packages auth — see CLAUDE.md)
java/scripts/e2e-local.sh              # one-shot E2E: real MLLP message → receive → materialize → take → ack → purge
java/scripts/hl7-live.sh up            # interactive: bring the listener up and drive it by hand (send/peek/take/ack/...)
```

**The real CI gate** (the source of truth — needs maven, the gradle wrapper, and
GitHub Packages auth; see [`CLAUDE.md`](CLAUDE.md) for the exact vault/env setup):

```bash
cd <repo-root> && ./gradlew :hl7:v2:gate     # validate → build → 45 tests → image → start container → dataloader
```

The production build is gradle (`zb.java-module`) → maven uber jar (schema codegen
runs at `generate-resources`) → Docker image, published by the shared `zbb` pipeline
like every other module. **Status**: the gate passes through container-start; the
`dataloader` step needs a platform credential CI supplies, and starting the daemon
in the gate needs `org/util` PR #86 (listener-port injection). See `CLAUDE.md`.
