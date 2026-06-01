# `@zerobias-org/module-hl7-v2` — Design

HL7 v2.x real-time message listener, exposed through the standard
**DataProducer** interface. Inbound MLLP messages persist into a durable
buffer; the Hub pipeline drains via the same hierarchical
container/collection/function model used by every other DataProducer
implementation.

This module is the first user of the **`Deployment.runtimeConfig`**
extension on the `EnsureDeployment` wire message — daemon mode, listener
ports, and durability (platform-interpreted), plus an opaque, module-defined
`config` object the platform transports but never parses. Extension/schema
packs are **not** carried here: they are HL7-specific and are baked into the
module image at build time (`npm install`), not resolved by the platform
(see §7).

> The DataProducer interface contract is canonical and lives at
> [`org/module/package/interface/dataproducer/documentation/`](../../interface/dataproducer/documentation/).
> When this doc and the interface docs disagree, the interface docs win.

## 1. Why this module is different

Every other Hub module is demand-driven: pipeline calls `connect()`, node
starts the container, operations run, the connection idles out, the
container stops. State is short-lived and rebuilt from a cached profile
on reactivation
(`com/hub/node/src/containers/ContainerDeployment.ts:412`).

HL7 inverts the call direction. EHR / lab / ADT systems push messages
**to** the listener, on their own schedule, with strict MLLP ACK
semantics (immediate `MSA|AA` on the same TCP connection). The receiver
MUST be:

- always reachable on a known port (sources are statically configured),
- durable across pipeline-call patterns (messages arrive between pipeline drains),
- ack-on-persist (a successful MSH/MSA round-trip means the message survives a node restart).

Standard passivation kills all three. This module declares itself
**daemon-mode** via `runtimeConfig.daemonMode: true`, takes an extra
**listener port** via `runtimeConfig.listenerPorts`, and persists to a
**durable volume** via `runtimeConfig.durability`. From the pipeline's
view, it then behaves like any other DataProducer.

## 2. DataProducer surface

### 2.1 Object hierarchy

The module follows the canonical pattern from
[`mappings/SQL.md`](../../interface/dataproducer/documentation/mappings/SQL.md):
hierarchical containers with collections and functions at the leaves.

```
"/"                                                container (root, mandatory; id == name == "/")
└─ "/hl7-v2-receiver"                              container — the receiver instance
   ├─ "/hl7-v2-receiver/messages"                  collection — every buffered message (heterogeneous)
   ├─ "/hl7-v2-receiver/by-type"                   container — type-discriminated views
   │  ├─ "/hl7-v2-receiver/by-type/ADT_A01"        collection — ADT_A01 only
   │  ├─ "/hl7-v2-receiver/by-type/ORU_R01"        collection
   │  └─ "/hl7-v2-receiver/by-type/<MSG_STRUCT>"   one per configured structure (base + extensions)
   ├─ "/hl7-v2-receiver/by-sender"                 container — sender-discriminated views
   │  └─ "/hl7-v2-receiver/by-sender/<MSH-3>"      collection — same rows filtered by sendingApplication
   ├─ "/hl7-v2-receiver/stats"                     document — backlog metrics
   └─ "/hl7-v2-receiver/ops"                       container — mutating operations
      ├─ "/hl7-v2-receiver/ops/take"               function — lease + return un-acked messages
      ├─ "/hl7-v2-receiver/ops/ack"                function — finalize a lease
      ├─ "/hl7-v2-receiver/ops/release"            function — return a lease early (no consume)
      ├─ "/hl7-v2-receiver/ops/replay"             function — re-mark in-flight rows as new
      └─ "/hl7-v2-receiver/ops/purge"              function — delete acked rows older than X
```

The `/messages` and `/by-type/<X>` collections expose the same buffer
rows; the typed views constrain `collectionSchema` to a single message
structure (and filter rows accordingly), the heterogeneous `/messages`
view uses the abstract `schema:shared:hl7v2.message-envelope`.

`searchCollectionElements` on these collections is **read-only** —
browsing and analytics. The drain primitive is `ops/take`, a Function
that returns + leases rows in one atomic step.

### 2.2 Schema ID namespace

Canonical form (per [`SchemaIds.md`](../../interface/dataproducer/documentation/SchemaIds.md)):
`schema:{type}:{catalog}.{schema}.{name}[:{direction}]`

> **Scope:** these `schema:…` IDs are the module's **internal** schema-content
> addressing, served by `getSchema`. They are **not** platform package/artifact
> identity — the dataloader/catalog identify packages by their standard parsed
> npm name, never by this scheme. Nothing here is consumed by the platform's
> identity layer.

| Schema ID | Purpose |
|---|---|
| `schema:table:hl7v2.v251.ADT_A01` | Collection schema for `/by-type/ADT_A01` |
| `schema:table:hl7v2.v251.ORU_R01` | Collection schema for `/by-type/ORU_R01` |
| `schema:type:hl7v2.v251.MSH` | MSH segment (composite, referenced from messages) |
| `schema:type:hl7v2.v251.PID` | PID segment |
| `schema:type:hl7v2.v251.CX` | CX datatype (extended composite ID with check digit) |
| `schema:type:hl7v2.v251.XPN` | XPN datatype (extended person name) |
| `schema:type:hl7v2.v251.HD` | HD datatype (hierarchic designator) |
| `schema:enum:hl7v2.v251.HL70001` | Administrative Sex value set |
| `schema:enum:hl7v2.v251.HL70003` | Event Type value set |
| `schema:enum:hl7v2.v251.HL70076` | Message Type value set |
| `schema:type:hl7v2.epic.ZPV` | Epic Z-segment (from `@zerobias-org/hl7-extension-epic-adt`) |
| `schema:enum:hl7v2.local.HL79001` | Customer local enum (from a `@<customer-scope>/hl7-extension-*` pack) |
| `schema:shared:hl7v2.message-envelope` | Common envelope fields across all message types (`controlId`, `receivedAt`, `status`, `leaseId`) |
| `schema:function:hl7v2.ops.take:input` / `:output` | `ops/take` I/O |
| `schema:function:hl7v2.ops.ack:input` / `:output` | `ops/ack` I/O |
| `schema:function:hl7v2.ops.purge:input` / `:output` | `ops/purge` I/O |

The catalog token is always `hl7v2`. The schema slot is `v251`, `v25`, … for
HL7-spec content; for extensions, it's a namespace the extension package
declares (`epic`, `cerner`, `nhs-uk`, `local`). This is an internal addressing
convention only — the module keeps the namespaces from colliding (below); there
is no platform-level "namespace ownership" rule. Names mirror HAPI's
structure-class names — `ADT_A01`, `PID`, `CX`, `HL70001`.

Extension packages contribute schemas in **their own namespace**.
There's no merging into base schemas — `schema:type:hl7v2.epic.ZPV` and
`schema:type:hl7v2.v251.PID` coexist as peers. A message structure that
includes Z-segments is a *new* schema in the extension's namespace
(e.g. `schema:table:hl7v2.epic.ADT_A01_with_ZPV`), referencing the base
segments plus the Z-segment. Collisions are impossible by construction;
provenance is the namespace.

### 2.3 References — composition all the way down

The DataProducer interface lets a property reference another schema two
ways (per [`SchemaIds.md`](../../interface/dataproducer/documentation/SchemaIds.md)):
**foreign-key** (`references.propertyName` present) and **composition**
(`references.propertyName` absent — embeds the referenced shape).

HL7 is composition-heavy. A worked traversal:

```yaml
Schema:
  id: schema:table:hl7v2.v251.ADT_A01
  dataTypes:
    - { name: string }
    - { name: date-time }
  properties:
    - name: msh
      required: true
      references:
        schemaId: schema:type:hl7v2.v251.MSH
    - name: evn
      required: true
      references:
        schemaId: schema:type:hl7v2.v251.EVN
    - name: pid
      required: true
      references:
        schemaId: schema:type:hl7v2.v251.PID
    - name: pv1
      references:
        schemaId: schema:type:hl7v2.v251.PV1
    # ... and the envelope fields from the shared schema:
    - name: controlId
      dataType: string
      primaryKey: true                          # MSH-10 — unique per source feed
      required: true
    - name: receivedAt
      dataType: date-time
      required: true
    - name: status
      dataType: string
      references:
        schemaId: schema:enum:hl7v2.ops.MessageStatus    # new | in_flight | acked

Schema:
  id: schema:type:hl7v2.v251.PID
  dataTypes: [{ name: string }, { name: date-time }]
  properties:
    - name: patientIdentifierList
      multi: true
      required: true
      references:
        schemaId: schema:type:hl7v2.v251.CX
    - name: patientName
      multi: true
      required: true
      references:
        schemaId: schema:type:hl7v2.v251.XPN
    - name: dateOfBirth
      dataType: date-time
    - name: administrativeSex
      dataType: string
      references:
        schemaId: schema:enum:hl7v2.v251.HL70001

Schema:
  id: schema:type:hl7v2.v251.CX
  dataTypes: [{ name: string }]
  properties:
    - name: idNumber
      dataType: string
      required: true
    - name: assigningAuthority
      references:
        schemaId: schema:type:hl7v2.v251.HD
    - name: identifierTypeCode
      dataType: string
      references:
        schemaId: schema:enum:hl7v2.v251.HL70203
```

A consumer walking from `schema:table:hl7v2.v251.ADT_A01` discovers
the full bean graph (MSH → PID → CX → HD → HL70203 enum), caches each
`getSchema` response, and ends up with the same typed view a HAPI Java
client gets from `msg.getPID().getPatientIdentifierList(0).getAssigningAuthority().getNamespaceId()`.

### 2.4 Core dataType mapping for HL7 primitives

Per [`CoreDataTypes.md`](../../interface/dataproducer/documentation/CoreDataTypes.md):
never `dataType: string + format: <thing>` when a core type exists.

| HL7 primitive | Core `dataType` | Notes |
|---|---|---|
| ST, TX, FT | `string` | |
| NM | `decimal` | Clinical values — never float. |
| SI (sequence ID) | `integer` | |
| ID / IS (table-bound code) | `string` + `references` to `schema:enum:…` | The enum schema lists the value set. |
| DT (`YYYYMMDD`) | `date` | Producer normalizes to ISO 8601 at materialization. |
| TM (`HHMMSS[.s+/-ZZZZ]`) | `string` | No core "time-only" type; `format: time` hint. |
| DTM / TS (`YYYYMMDDHHMMSS[.s][+/-ZZZZ]`) | `date-time` | Normalized to ISO 8601. |
| EI (entity identifier) | composition `references` to `schema:type:hl7v2.v251.EI` | |
| HD, CX, XPN, XAD, CE, CWE, CNE, CWE, … | composition `references` to `schema:type:hl7v2.v251.<name>` | Each composite gets its own schema. |
| Repeating fields | parent property + `multi: true` | E.g. `patientName: { multi: true, references: schemaId: schema:type:hl7v2.v251.XPN }`. |

The producer normalizes wire-format values at materialization time —
ETL never sees HL7 date formats, never has to re-parse pipe-encoded
escapes, never decodes ER7 by hand. The DataProducer surface is
post-normalized JSON, by design.

### 2.5 Function objects — `take`, `ack`, `release`, `replay`, `purge`

`searchCollectionElements` is read-only browsing per the interface
contract. Mutating "drain a batch and lease it" is a Function:

```yaml
# Function Object
Object:
  id: "/hl7-v2-receiver/ops/take"
  name: take
  objectClass: [function]
  description: Lease un-acked messages from the buffer, returning them and
    marking them in_flight until acked, released, or the lease TTL elapses.
  inputSchema:  schema:function:hl7v2.ops.take:input
  outputSchema: schema:function:hl7v2.ops.take:output
  throws:
    lease_capacity_exceeded:  schema:shared:hl7v2.ops.lease_capacity_error
    backpressure:             schema:shared:hl7v2.ops.backpressure_error

# Input
Schema:
  id: schema:function:hl7v2.ops.take:input
  dataTypes: [{ name: integer }, { name: string }, { name: duration }]
  properties:
    - name: filter
      dataType: string
      description: RFC4515 filter applied to candidate rows (status=new OR expired in_flight).
    - name: max
      dataType: integer
      description: Upper bound on rows returned. Default 100, capped at 1000.
    - name: leaseTtl
      dataType: duration
      description: How long until in_flight rows revert to new. Default PT5M.

# Output
Schema:
  id: schema:function:hl7v2.ops.take:output
  dataTypes: [{ name: uuid }, { name: integer }]
  properties:
    - name: leaseId
      dataType: uuid
      required: true
    - name: messages
      multi: true
      required: true
      description: Each message conforming to its message-type schema in the catalog.
    - name: remaining
      dataType: integer
      description: Approximate backlog after this lease — for the consumer to pace.
```

Equivalent shapes for `ack`, `release`, `replay`, `purge`. `ack`
accepts `leaseId` + an optional subset of `controlIds` (partial acks
allowed — un-acked rows in the lease revert at TTL).

### 2.6 Filter semantics — RFC4515 against schema property names

Filter attributes are **schema property names** per
[`FilterSyntax.md`](../../interface/dataproducer/documentation/FilterSyntax.md),
not HL7 positional codes (no `MSH-3` on the wire — that's the
backend's column name). Consumers see clean, traversable names:

```
# All ADT messages from EPIC since a date, un-acked
(&(msh.sendingApplication.namespaceId=EPIC)
  (msh.messageType.messageCode=ADT)
  (receivedAt>=2026-05-27)
  (status=new))

# Multiple message types
(|(msh.messageType.messageCode=ADT)(msh.messageType.messageCode=ORM))

# Patient lookup across messages (case-insensitive prefix)
(pid.patientIdentifierList.idNumber=5551*)

# Time-of-day precision / relative windows use the date extensions, NOT >=
(receivedAt:withinDays:1)
```

> **Parser constraint (verified Phase 5).** lite-filter's RFC4515 parser
> treats the first `:` in a clause as the start of a `:function:` operator,
> so a **full ISO datetime** value (`2026-05-27T00:00:00Z`) in a `>=`/`<=`
> clause is mis-read (operator `:00:`) and rejected. Use the **date-only**
> form (`receivedAt>=2026-05-27`, as the interface's `FilterSyntax.md`
> documents) for absolute cutoffs, or the colon-delimited
> `:withinDays:`/`:year:` extensions for windows. Note also that the
> in-memory `matches()` evaluator's `>`/`>=` are numeric-only and throw on
> date strings — so absolute-date `>=` is a **SQL-adapter-only** capability
> here (fine for this producer, which always pushes the filter to SQLite).
> A fuller fix (time-of-day in comparisons) belongs in `org/util/lite-filter`.

The producer parses with `@zerobias-org/util-lite-filter`
(`org/util/packages/lite-filter`), which provides the RFC4515 parser,
the `Expression` AST, the in-memory `matches()` evaluator, and the
**adapter framework** (`Expression.addAdapter(key, desc, impl)` +
`expression.as("SQL")`). The package does **not** ship a built-in SQL
adapter — each consumer registers its own. The implementation is
`filter/Hl7SqlAdapter`: ANSI operators (`=`, `!=`, `>=`, `<=`, `LIKE`,
`IS NOT NULL`, AND/OR/NOT grouping, `:contains:` / `:startsWith:` /
`:endsWith:` / `:between:`) all run against SQLite.

> **Implementation note (Phase 5).** The SQL generic module's
> [`SqlAdapter.java`](../../../../auditlogic/module/package/auditmation/generic/sql/java/src/main/java/com/zerobias/module/sql/SqlAdapter.java)
> reads `Clause`/`Grouping` **private fields** by reflection and casts
> `expressions` to `Expression[]`. Against this lite-filter version that
> field is a `List<Expression>` (and the public getters return the public
> `ComparisonOperator`/`LogicalOperator` enums), so a verbatim lift would
> `ClassCastException`. `Hl7SqlAdapter` instead reflects the **public
> getters** and switches on the enums — the same SQL output, but robust to
> the AST's internal shape. (`Clause`/`Grouping` are package-private, so
> reflection is still required to reach them from our package.)

Three HL7-specific behaviors on top of the ANSI core:

- **Nested composite paths.** `msh.sendingApplication.namespaceId=EPIC`
  emits a JSON path against the `mapped_json` column using SQLite's
  JSON1: `json_extract(mapped_json, '$.msh.sendingApplication.namespaceId') = 'EPIC' COLLATE NOCASE`.
  Only the four envelope properties (`controlId`, `receivedAt`, `status`,
  `leaseId`) resolve to real columns; everything else is a `mapped_json`
  path. String equality is emitted `COLLATE NOCASE` to match the in-memory
  evaluator's default case-insensitive `MatchOptions`.
- **Date extensions over epoch-millis storage.** `received_at` is stored as
  an epoch-millis INTEGER, so the naive `datetime('now',…)` forms don't
  compare. We emit: `:withinDays:30` → `received_at >= (unixepoch('now', '-30 days') * 1000)`;
  `:year:2026` → `strftime('%Y', received_at / 1000, 'unixepoch') = '2026'`;
  and an absolute `receivedAt>=2026-05-27` → `received_at >= (unixepoch('2026-05-27') * 1000)`.
  (For `mapped_json` date paths, which hold ISO strings, the extensions
  fall back to `unixepoch(json_extract(…))` / `strftime('%Y', json_extract(…))`.)
  These let the pipeline issue "drain everything in the last day" without
  client-side date math — and without tripping the parser constraint noted above.

### 2.7 Errors

Per [`Errors.md`](../../interface/dataproducer/documentation/Errors.md):

- `noSuchObjectError` (404) — unknown object ID, unknown schema ID, unknown leaseId on `ack`/`release`.
- `UnsupportedOperationError` (400) — e.g. `addCollectionElement` on `/messages` (it's receive-only).
- `illegalArgumentError` (400) — malformed RFC4515 filter, `max > 1000`, both `keywords` and `filter` on a search.
- `preconditionFailedError` (412) — not used here (no ETag-driven updates).
- Function-declared (`throws` map) — `lease_capacity_exceeded`, `backpressure` on `take`; `lease_expired` on `ack`/`release`; `not_found` for unknown control IDs.

## 3. Runtime shape — single container, two long-lived processes

Mirrors the `package/auditmation/generic/sql` pattern: nginx fronts an
internal Java process. The Java process boots two long-lived subsystems:

```
PID 1 ── startup.sh                      (supervisor, signal forwarding)
        ├── nginx                        (binds 0.0.0.0:8888 → 127.0.0.1:8889)
        └── java -jar hl7-listener.jar
              ├── HTTP server on 8889    (DataProducer operations)
              ├── HAPI HL7Service on $LISTENER_PORT_MLLP   (MLLP receiver)
              └── SQLite writer thread   (single-writer; readers via separate connections)
```

Both Java subsystems share one process, one `BufferStore`, one JDBC
connection pool. No IPC. WAL mode handles concurrent reader/writer
cleanly.

### 3.1 Dockerfile

```Dockerfile
FROM eclipse-temurin:17-jre
LABEL org.opencontainers.image.source="https://github.com/zerobias-org/module"

RUN apt-get update && apt-get install -y --no-install-recommends \
        nginx openssl ca-certificates && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/module

COPY java/target/hl7-listener-1.0.0.jar /opt/module/hl7-listener.jar
COPY *.yml /opt/module/
COPY nginx.conf /opt/module/nginx.conf
COPY startup.sh /opt/module/startup.sh
RUN chmod +x /opt/module/startup.sh

VOLUME /var/lib/module                   # buffer.db lives here (durable)
                                         # extension packs are baked into the image
                                         # at build (see §7), NOT in this volume
EXPOSE 8888                              # producer (Hub-facing, fronted by nginx)
                                         # NOTE: MLLP port is NOT EXPOSE'd — see §3.3

ENV INTERNAL_PORT=8889
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV HUB_NODE_INSECURE=false

CMD ["/opt/module/startup.sh"]
```

`startup.sh` is structurally identical to the sql module's
([`package/auditmation/generic/sql/startup.sh`](../../auditmation/generic/sql/startup.sh)):
optional self-signed cert generation, nginx in background, Java in
foreground with signal forwarding.

### 3.2 Environment contract from Hub Node

The Java process reads its ports and config from env:

| Env | Source | Required |
|---|---|---|
| `INTERNAL_PORT` (default 8889) | Dockerfile | yes |
| `LISTENER_PORT_MLLP` | Hub Node injects at container-create time from `runtimeConfig.listenerPorts[name=mllp].port` | yes — module refuses to start without it |
| `MODULE_CONFIG` | Hub Node injects the opaque `runtimeConfig.config` (JSON) verbatim; the module parses it | optional |
| `EXTENSION_DIR` (default `/opt/module/extensions`) | An **in-image** path the module owns — extension packs are baked in at build (§7). **Not** Node-mounted. | optional |

No implicit defaults for `LISTENER_PORT_MLLP`. A daemon listener with no
port is a misconfiguration, not a fallback case. `EXTENSION_DIR` is no longer
a per-deployment mount point — the platform does not deliver extensions at
runtime (§7).

### 3.3 Listener port publishing — Hub Node responsibility

The Dockerfile does **not** `EXPOSE` the MLLP port. `EXPOSE` is
documentation-only and is ignored when the orchestrator passes explicit
port maps — and Hub Node always does. Listing it would imply ownership
we don't have.

The Hub Node generalizes `com/hub/node-lib/src/docker/Container.ts:310`
(today: `createArgs.push('-p', \`${port}:8888\`)`) to iterate
`runtimeConfig.listenerPorts`:

```ts
createArgs.push('-p', `${operationsPort}:8888`);
for (const lp of runtimeConfig?.listenerPorts ?? []) {
  const p = lp.port ?? await slot.ports.allocatePort();
  createArgs.push('-p', `${lp.bindAddress}:${p}:${p}/${lp.protocol}`);
  env[`LISTENER_PORT_${lp.name.toUpperCase()}`] = String(p);
}
for (const d of runtimeConfig?.durability ?? []) {
  await docker.volumeEnsure(d.volumeName);
  createArgs.push('-v', `${d.volumeName}:${d.mountPath}:${d.access ?? 'rw'}`);
}
```

Container port = host port (1:1, no NAT). Operator pins
`runtimeConfig.listenerPorts[name=mllp].port: 2575` for HL7 sources
that dial a known endpoint; ephemeral (`null`) is fine for test feeds.

## 4. Receiver — HAPI HL7v2

### 4.1 Why HAPI, and what we use

HL7's wire layer (MLLP framing, lenient parsing of real-world feeds,
HL7 escape handling, multi-version dispatch) is the part that's hard
to get right and has 20+ years of beating-by-real-hospital-feeds
sunk-cost in HAPI. We pull `hapi-base` and use:

- `MinLowerLayerProtocol` — MLLP framer (handles partial frames, charset detection, vendor quirks).
- `HL7Service` — the long-running TCP server with connection management.
- `ReceivingApplication` — callback contract for incoming messages + ACK building.
- `GenericParser` — lenient ER7 → tree (segments → fields → components → sub-components, all as opaque strings).
- `Terser` — XPath-like accessor over the parsed tree.

We do **NOT** pull `hapi-structures-vNN` jars at runtime. The typed model
classes (`ADT_A01`, `PID`, etc.) are unnecessary for the receive +
buffer path. They're used at **build time** to generate JSON schemas
(see §6).

### 4.2 BufferingApp — the ack contract

```java
HapiContext ctx = new DefaultHapiContext();
ctx.getParserConfiguration().setAllowUnknownVersions(true);
ctx.getParserConfiguration().setValidating(false);          // accept real-world dirty feeds

int port = Integer.parseInt(System.getenv("LISTENER_PORT_MLLP"));
HL7Service server = ctx.newServer(port, /*tls*/ false);
server.registerApplication("*", "*", new BufferingApp(bufferStore, structureIndex));
server.registerConnectionListener(new MetricsConnectionListener(metrics));
server.startAndWait();

// BufferingApp.processMessage()
public Message processMessage(Message in, Map<String, Object> meta) throws HL7Exception {
    Terser t = new Terser(in);
    BufferRow row = BufferRow.builder()
        .controlId(t.get("/MSH-10"))
        .messageStructure(t.get("/MSH-9-3"))                // e.g. ADT_A01
        .messageCode(t.get("/MSH-9-1"))
        .triggerEvent(t.get("/MSH-9-2"))
        .sendingApp(t.get("/MSH-3"))
        .sendingFacility(t.get("/MSH-4"))
        .hl7Version(t.get("/MSH-12"))
        .receivedAt(Instant.now())
        .rawEr7(in.encode())                                // canonical ER7 for audit/replay
        .mappedJson(materializer.toTypedJson(in))           // see §5
        .build();
    try {
        bufferStore.insert(row);                            // INSERT … ON CONFLICT(control_id) DO NOTHING
    } catch (Exception e) {
        return ackBuilder.error(in, e);                     // MSA|AE → sender retries
    }
    return ackBuilder.accept(in);                           // MSA|AA only after row durable
}
```

The MSA is built and returned **after** the SQLite commit returns. A
`MSA|AA` on the wire means the row will survive a container restart.

Duplicate `controlId` on `INSERT` is silently dropped (sender still
gets `AA`). HL7 re-sends are routine; treating them as new rows would
poison the pipeline.

## 5. Materializer — generic parse to typed JSON

The DataProducer surface exposes typed JSON, not ER7. The materializer
walks the generic-parsed message against a **structure index** (a
data-driven map of "PID-3 → patientIdentifierList, multi, type CX") and
emits typed JSON with HAPI-bean-conventional field names. The structure
index is shipped in the module's classpath (see §6) and merged at boot
with extension-supplied entries (see §7).

```
generic-parsed:  PID|||5551212^^^EPIC&...^MR||SMITH^JOHN||19800101|M
structure idx :  PID-3 patientIdentifierList CX[] required
                 PID-5 patientName XPN[] required
                 PID-7 dateOfBirth DTM optional
                 PID-8 administrativeSex IS optional table=HL70001
materializer →
{
  "patientIdentifierList": [
    { "idNumber": "5551212",
      "assigningAuthority": { "namespaceId": "EPIC", "universalIdType": "ISO" },
      "identifierTypeCode": "MR" }
  ],
  "patientName": [
    { "familyName": { "surname": "SMITH" }, "givenName": "JOHN" }
  ],
  "dateOfBirth": "1980-01-01T00:00:00Z",
  "administrativeSex": "M"
}
```

Normalization happens here: HL7 dates (`19800101`) → ISO 8601;
escape sequences (`\F\`, `\R\`, …) → literal chars; nulls
(`""` vs unset components) → `null` vs absent keys.

Composites recurse — CX → HD; tables get tagged but not resolved (the
consumer follows the `schema:enum:…` reference to get display values).

## 6. Schema content — build-time generation

HL7's spec content (segments, datatypes, tables, message structures) is
authoritative in HAPI's structures jars. Pulling them at runtime adds
~25-35MB per HL7 version for data that never changes. Instead:

```
java/
├── codegen/                                       # build-time-only Maven project
│   ├── pom.xml                                    # depends on hapi-structures-vNN
│   └── src/main/java/.../SchemaGenerator.java     # walks the class graph, emits JSON
├── src/main/
│   ├── java/...                                   # listener + materializer + buffer
│   └── resources/
│       ├── schemas/v251/
│       │   ├── messages/ADT_A01.json              # schema:table:hl7v2.v251.ADT_A01
│       │   ├── segments/PID.json                  # schema:type:hl7v2.v251.PID
│       │   ├── datatypes/CX.json                  # schema:type:hl7v2.v251.CX
│       │   └── tables/HL70001.json                # schema:enum:hl7v2.v251.HL70001
│       └── structure-index/v251.json              # runtime materializer driver
└── pom.xml                                        # exec-maven-plugin: runs codegen before package
```

`/schemas/{schemaId}` is static file serving from the classpath plus
in-memory merging with extension contributions. No reflection at request
time. Generated JSON is checked into git — diffs across HL7 versions are
reviewable.

A separate maintenance task can republish the generated schemas as
`@zerobias-org/hl7-v2-schemas` for consumers that want the schemas without
deploying the receiver module.

## 7. Extensions — published NPM artifacts

Customer / vendor-specific HL7 content (Z-segments, custom tables,
augmented message structures) lives in versioned NPM packages.

> **✅ Delivery model resolved (2026-06-01).** Install-time wins. HL7
> extension/schema packages are **not** processed by the dataloader, are **not**
> platform catalog artifacts, and are **not** resolved per-deployment by the
> platform — "they're their own deal; they just get into the module via
> `npm install`." Delivery is **build/install-time**: extension packs are npm
> dependencies baked into the module image, with the package's `extensions/`
> content laid down under `EXTENSION_DIR` at build. Consequently the dataloader
> processor (former PLATFORM_UPDATES §6.1–§6.2), the pkg-proxy fetch/mount flow
> (former §5.2), and the typed `runtimeConfig.extensions` field are all
> **dropped**. The tradeoff is accepted: one image serves one extension set;
> serving a different vendor pack means a different image build. Any *runtime
> selection* among baked-in packs (which to activate) is expressed in the opaque
> `runtimeConfig.config` (DESIGN §3.2 `MODULE_CONFIG`), which the platform
> transports but never parses. The schema *content* design (§7.1–§7.2) is
> unchanged — only how the files reach the container changed. §7.3 below is
> rewritten to the install-time model.

### 7.1 Package shape

Extension packs are **ordinary npm packages** — they are consumed by the module
build (`npm install`), not by the platform dataloader, so they carry **no**
`zerobias.import-artifact` declaration and are subject to no dataloader naming
rule. The naming below is a module-author convention for readability only, not a
platform-parsed identity.

```
@zerobias-org/hl7-extension-epic-adt/      # convention: vendor=epic, name=adt
├── package.json                           #   a plain npm dependency of the module
└── extensions/
    ├── manifest.json
    ├── messages/ADT_A01_with_ZPV.json    # schema:table:hl7v2.epic.ADT_A01_with_ZPV
    ├── segments/ZPV.json                 # schema:type:hl7v2.epic.ZPV
    ├── segments/ZIN.json                 # schema:type:hl7v2.epic.ZIN
    ├── tables/HL79001.json               # schema:enum:hl7v2.epic.HL79001
    └── structure-index.json              # extension's materializer entries
```

`package.json`:

```json
{
  "name": "@zerobias-org/hl7-extension-epic-adt",
  "version": "1.2.4",
  "auditmation": {
    "hl7": {
      "version": "2.5.1",
      "vendor": "epic",
      "namespace": "epic",
      "messageGroups": ["ADT"]
    }
  }
}
```

> No `zerobias.import-artifact` block — the dataloader never sees these packages.
> The module's own Docker build pulls the declared extension deps and lays their
> `extensions/` content under `EXTENSION_DIR` in the image. The `auditmation.hl7`
> block is read by the **module** at boot (version compat, namespace), not by the
> platform. Scope is `@zerobias-org` (open content), not `@auditlogic`.

`namespace` is the module's internal schema slot
(`schema:type:hl7v2.<namespace>.<name>`) — see the §2.2 scope note. The module
keeps namespaces from colliding at load time (§7.2). There is **no** platform
"namespace ownership" validation: the platform identifies a package only by its
standard parsed name, with no namespace-subpath rule.

### 7.2 How extensions appear in the receiver

Extensions don't modify base schemas. They contribute *new* schemas
under their own namespace. `schema:table:hl7v2.epic.ADT_A01_with_ZPV`
references base segments from `hl7v2.v251.*` plus its own ZPV segment:

```yaml
Schema:
  id: schema:table:hl7v2.epic.ADT_A01_with_ZPV
  description: "Epic-augmented ADT_A01 with ZPV after PV1"
  dataTypes: [...]
  properties:
    - name: msh
      references: { schemaId: schema:type:hl7v2.v251.MSH }       # base
    - name: pid
      references: { schemaId: schema:type:hl7v2.v251.PID }       # base
    - name: pv1
      references: { schemaId: schema:type:hl7v2.v251.PV1 }       # base
    - name: zpv
      references: { schemaId: schema:type:hl7v2.epic.ZPV }       # extension
    # ... plus envelope fields
```

Collisions impossible — `epic.ADT_A01_with_ZPV` and `v251.ADT_A01` are
peer schemas in the module's in-memory schema registry (not the platform catalog).

The receiver's hierarchy reflects what's loaded. With the Epic extension
pulled in, `/hl7-v2-receiver/by-type` lists both:

```
/hl7-v2-receiver/by-type/ADT_A01              ← base, schema:table:hl7v2.v251.ADT_A01
/hl7-v2-receiver/by-type/ADT_A01_with_ZPV     ← extension, schema:table:hl7v2.epic.ADT_A01_with_ZPV
```

Message routing at materialization time picks the most-specific
matching structure. If MSH-9-3 doesn't disambiguate (it usually doesn't
for Z-extended structures), discriminator rules in the extension's
`manifest.json` decide (e.g. "if MSH-3 == 'EPIC' and ADT, use
`epic.ADT_A01_with_ZPV`").

### 7.3 Delivery — install-time, baked into the image

Extensions are **not** resolved per-deployment by the platform. They are npm
dependencies of the module, baked into the image at build, and (optionally)
selected at runtime via the opaque `config`.

**At image build** — the module declares its extension packs as npm dependencies
and the Docker build lays each pack's `extensions/` content under `EXTENSION_DIR`,
one subdirectory per pack:

```dockerfile
# (module Dockerfile, build stage)
# npm install pulled the declared @*/hl7-extension-* deps into node_modules;
# copy each pack's extensions/ into the image under EXTENSION_DIR.
COPY extensions-staging/ /opt/module/extensions/
#   /opt/module/extensions/<vendor-name>/extensions/manifest.json …
```

**`runtimeConfig` carried on `EnsureDeployment`** — no `extensions` field. The
platform-typed part is daemon/ports/durability; everything HL7-specific (incl.
which baked-in packs to activate, if the image ships more than it enables by
default) is the opaque `config`:

```yaml
runtimeConfig:
  daemonMode: true
  listenerPorts:
    - { name: mllp, protocol: tcp, port: 2575 }
  durability:
    - { volumeName: hl7-buffer, mountPath: /var/lib/module,
        retention: { maxBytes: 10737418240, maxAge: P7D } }
  config:                       # OPAQUE to the platform — module-defined shape
    activeExtensions: [epic-base, epic-adt]   # optional; default = all baked-in
    senderDiscriminator: MSH-3
```

The Hub Node injects `config` verbatim as `MODULE_CONFIG` (DESIGN §3.2); it does
**not** fetch or mount anything for extensions.

**Module at boot:**

1. Loads `${EXTENSION_DIR}/*/extensions/manifest.json` — one match per baked-in
   pack (the inner `extensions/` is the package's own directory from §7.1).
2. Filters to the packs named in `config.activeExtensions` (if present; otherwise
   all baked-in packs are active).
3. Validates (module-internal, not platform identity): HL7 version compatibility
   (`hl7.version` matches the module's configured version), schema-ID format, and
   no duplicate schema IDs across loaded packs. (No "namespace ownership" check —
   that rule does not exist.)
4. Merges schemas into the in-memory schema registry.
5. Merges structure-index entries into the materializer driver.
6. Registers additional `/by-type/<name>` collection objects.

There is **no** server-side / deploy-time extension validation — the platform has
nothing to validate (the image either contains the packs or it doesn't). All
extension validation is module-internal, applied at boot.

## 8. Buffer — SQLite + WAL

One file at `/var/lib/module/buffer.db`. Schema:

```sql
CREATE TABLE messages (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  received_at     TIMESTAMP NOT NULL,
  control_id      TEXT NOT NULL UNIQUE,
  message_structure TEXT NOT NULL,     -- ADT_A01, ORU_R01, …
  message_code    TEXT NOT NULL,       -- ADT, ORU, …
  trigger_event   TEXT NOT NULL,       -- A01, R01, …
  sending_app     TEXT,
  sending_facility TEXT,
  hl7_version     TEXT,
  schema_id       TEXT NOT NULL,       -- which collection schema this row claims
  raw_er7         BLOB NOT NULL,
  mapped_json     TEXT NOT NULL,       -- typed JSON per §5
  status          TEXT NOT NULL DEFAULT 'new',  -- new | in_flight | acked
  lease_id        TEXT,                -- non-null when in_flight
  in_flight_until TIMESTAMP,
  acked_at        TIMESTAMP
);
CREATE INDEX messages_drain ON messages(schema_id, status, received_at);
CREATE INDEX messages_lease ON messages(lease_id) WHERE lease_id IS NOT NULL;

PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;           -- see §8.1
```

### 8.1 Durability mode

`PRAGMA synchronous=NORMAL` is the default — fsync at WAL checkpoints,
not every write. Kernel crashes inside the WAL window lose a few
seconds of un-checkpointed messages for which `MSA|AA` was already
sent. That's a real durability gap. Operators with clinical feeds set
`connectionProfile.ackDurability: full` for `PRAGMA synchronous=FULL`
(fsync per row, ~2× slower) at deployment time.

### 8.2 Drain semantics — implemented as `ops/take`

```sql
BEGIN IMMEDIATE;
UPDATE messages
   SET status='in_flight',
       lease_id = :lease_id,
       in_flight_until = now() + :ttl
 WHERE id IN (
   SELECT id FROM messages
    WHERE schema_id = :schema_id
      AND (status='new' OR (status='in_flight' AND in_flight_until < now()))
      AND <RFC4515-translated WHERE>
    ORDER BY received_at ASC
    LIMIT :max
 )
 RETURNING *;
COMMIT;
```

Lease TTL default 5 minutes (capped at 1 hour). Expired in-flight rows
revert at the next `take`. Consumer must treat its drain as idempotent
— the same `controlId` can re-appear if it crashed mid-process.

### 8.3 Retention sweeper

Background thread every 10 minutes:

```sql
DELETE FROM messages
 WHERE status='acked'
   AND (acked_at < now() - :retention
        OR id IN (
          SELECT id FROM messages
           WHERE status='acked'
           ORDER BY received_at ASC
           LIMIT (SELECT COUNT(*) FROM messages WHERE status='acked')
                   - (SELECT max_count FROM retention_config)
        ));
```

Both `maxBytes` and `maxAge` apply from `runtimeConfig.durability.retention`;
whichever fires first wins.

## 9. Health

Daemon-mode deployments can't use "operations succeed → healthy" — a
daemon may sit with zero pipeline calls for hours. Two layers:

- **`/healthz` on the operations port (8888).** Returns 200 with:
  ```json
  { "listener": { "up": true, "lastReceived": "...", "bufferDepth": 142, "oldestUnackedSec": 3 },
    "db":       { "walBytes": 12345, "lastCheckpoint": "..." },
    "extensions": [ { "artifact": "@zerobias-org/hl7-extension-epic-adt@1.2.4", "schemasLoaded": 7 } ] }
  ```
  Hub Node polls every 30s for daemon deployments — purely to know whether the
  container is healthy, the same way it would for any container.

- **MLLP self-test at startup.** Module dials `127.0.0.1:$LISTENER_PORT_MLLP`,
  sends a synthetic `MSH|...|HEALTHCHECK|...|ZZZ^X01`, asserts the row
  is rejected by a `*/X01` no-op handler that never persists. Confirms
  the receive loop is wired end-to-end without polluting the buffer.

**`/healthz` is a Node-only concern — it does not feed the platform event
system.** There is no `DeploymentDegradedReason` / platform deployment-status
plumbing for it (corrected 2026-06-01). When `/healthz` fails (for ~2 consecutive
minutes), the failure activates the **Node Alert system**; the listener port stays
open (TCP connections from sending systems are expensive to rebuild — don't
restart on a transient health-check flap) and restart is **operator-driven**.
Cohesive Node-Alert↔platform integration (surfacing Node alerts platform-side) is
a real, needed, and **separate** effort — out of scope here. See
PLATFORM_UPDATES.md §8.

## 10. Out of scope

- **Outbound MLLP.** This module receives. Outbound HL7 is a separate
  module (likely a normal, passivating one).
- **Routing / transformation.** The module materializes HL7 → typed
  JSON conforming to schemas in the catalog. Mapping that typed JSON
  into AuditgraphDB objects is a downstream collectorbot.
- **HL7 v3 / FHIR.** Different module — `@zerobias-org/module-hl7-v3` or
  `@zerobias-org/module-fhir`.
- **TLS-MLLP.** v1: `tls: false`. v2: terminate TLS in HAPI with a
  cert from a separately-mounted secret.
- **High availability.** Single-instance, single-host. Multi-node
  failover needs a shared buffer (Postgres / Kafka) and is a separate
  design.

## 11. Open questions

1. **One listener port or many?** Some networks want one MLLP port per
   sending system (cleaner ACL, easier troubleshooting). The
   `runtimeConfig.listenerPorts` array supports N — but each pinned
   port is a host-port commitment per deployment. Leaning toward "one
   deployment per feed" so blast radius matches operator mental model;
   need to confirm against actual customer topologies.
2. **Lease semantics for `take`.** Should partial-ack-with-lease-revert be
   the default (current design), or full-or-nothing? Partial is more
   flexible but harder to reason about. Need to know the pipeline's
   crash semantics.
3. **Backpressure when buffer fills.** Three policies, set via
   `connectionProfile.backpressurePolicy`: (a) `reject` → `MSA|AE`,
   sender retries; (b) `evict-acked` → drop oldest acked regardless of
   retention; (c) `evict-unacked` → drop oldest un-acked (data loss).
   Default `reject` for clinical feeds. Decision logged.
4. **`/by-sender` discriminator.** MSH-3 (sendingApplication) is the
   obvious key, but real-world feeds sometimes set MSH-3 to a generic
   "EPIC" and discriminate via MSH-4 (sendingFacility). Should the
   discriminator be configurable, or always MSH-3? Default MSH-3,
   override via `connectionProfile.senderDiscriminator`.
5. **HL7 version pinning.** The module declares its target HL7 version
   in `connectionProfile.hl7Version`. Receiver still accepts other
   versions (HAPI's `setAllowUnknownVersions(true)`) but materializes
   them against the configured version's structure index. Mismatched
   feeds will produce partially-populated typed JSON. Worth surfacing
   per-message via a `hl7VersionMismatch` envelope field.
6. **lite-filter date-extension translation.** The sql generic module's
   `SqlAdapter` (which we lift) doesn't implement `:withinDays:` /
   `:year:` — the sql module hasn't needed them. We need SQLite-native
   emissions (`datetime('now','-N days')`, `strftime('%Y', col)`) wired
   into the adapter, plus parity tests against lite-filter's in-memory
   evaluator semantics so wire results match what `expression.matches()`
   would compute. Worth deciding whether the SQLite-flavored adapter
   lives in this module or gets contributed back to lite-filter as a
   reference adapter alongside the example one in the README.

## 12. Implementation order

1. **Land the `runtimeConfig` data-model extension** in
   `com/hydra/core/schemas/hub/` + server resolution + node honor
   (daemon mode, listener ports, durability, and the opaque `config`
   passthrough — **no** typed extensions). This module is unbuildable
   without it.
2. **(Dropped — resolved 2026-06-01.)** The dataloader does **not** process HL7
   extension/schema packages; they reach the module via `npm install` (baked into
   the image, §7). There is no `hl7_extension` dataloader processor and no
   extension catalog. The dataloader's only module-side change is reading the
   module's root `runtimeConfig.yml` → `module_version.runtime_config`
   (PLATFORM_UPDATES §6.3).
3. **Build-time schema codegen.** Maven sub-module that walks
   `hapi-structures-v251`, emits `schemas/v251/*.json` and
   `structure-index/v251.json`. Standalone — verifiable against the
   spec without the rest of the module.
4. **Materializer + buffer + producer in isolation.** Unit tests:
   synthetic MLLP client → assert buffer rows + typed JSON shape +
   `/schemas/` resolution + `take`/`ack` round-trip.
5. **Wire Dockerfile + startup.sh** (copy from sql, swap the jar,
   add `LISTENER_PORT_MLLP` reading + `EXTENSION_DIR` scanning).
6. **End-to-end against a local Hub Node** with one pinned listener
   port, a `simhospital`-generated feed (or HAPI's test data),
   pipeline-driven `take`/`ack` cycle, one extension package loaded.
7. **Operator docs.** Pinning the listener port, sizing the durability
   volume, declaring extensions, reading `/healthz`, finding the
   ephemeral listener port when not pinned.

## See also

- DataProducer interface contract: [`org/module/package/interface/dataproducer/documentation/`](../../interface/dataproducer/documentation/)
- SQL DataProducer mapping (reference for the runtime container shape): [`auditlogic/module/package/auditmation/generic/sql/`](../../../../auditlogic/module/package/auditmation/generic/sql/)
- `Deployment.runtimeConfig` data model (proposed): the discussion that produced this doc.
- HAPI HL7v2: https://hapifhir.github.io/hapi-hl7v2/
