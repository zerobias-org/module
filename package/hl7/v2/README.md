# `@zerobias-org/module-hl7-v2` — HL7 v2.x MLLP Receiver

An **always-on HL7 v2.x receiver** exposed through the standard **DataProducer**
interface. Hospital / lab / ADT systems push messages over MLLP; the module
persists them to a durable buffer, materializes them to typed JSON, and the Hub
pipeline drains them via the same container/collection/function model every other
DataProducer uses.

> Design canon: [`DESIGN.md`](DESIGN.md). Interface canon:
> [`../interface/dataproducer/documentation/`](../interface/dataproducer/documentation/).
> Operator runbook: [`USERGUIDE.md`](USERGUIDE.md).

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
  hl7Version: "2.5.1"
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
| `hl7Version` | target version for materialization (default `2.5.1`) |
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

There is no local mvn/gradle in every environment, so the Java behavior is
validated with a self-contained harness that fetches public deps and runs the
JUnit suites + demos:

```bash
java/scripts/manual-test.sh test       # full JUnit suite (buffer, materializer, listener, filter, producer, ops, health, extensions)
java/scripts/manual-test.sh listener   # start the MLLP listener, send a real HL7 message, see the ACK + stored JSON
java/scripts/manual-test.sh producer   # drive the DataProducer read ops + the take→ack→purge drain cycle
java/scripts/manual-test.sh filter     # show RFC4515 → SQLite WHERE → matching rows
java/scripts/container-smoke.sh        # build the image + smoke-test the container plumbing (needs Docker)
```

The production build is gradle (`zb.java-module`) → maven uber jar (with the schema
codegen run at `generate-resources`) → Docker image, published by the shared
`zbb` pipeline like every other module.
