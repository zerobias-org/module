# Platform updates required to support `@zerobias-org/module-hl7-v2`

This module is the first user of three new **platform** capabilities:

1. **Daemon-mode deployments** — containers that don't passivate, auto-start on node boot.
2. **Additional listener ports** — deployments bind extra TCP/UDP ports beyond the standard module operations port.
3. **Durable per-deployment volumes** — named Docker volumes that survive passivation and re-deploy.

These three are genuinely platform-level: the Hub Node must act on them to create
the container (restart policy, `-p`, `-v`). They ride on a single new field —
`Deployment.runtimeConfig` — carried on the existing `EnsureDeployment` message.
No new wire messages, no new operations.

`runtimeConfig` also carries an **opaque `config` object** (jsonb). The platform
stores and transports it verbatim and **never interprets it** — its shape and
meaning are defined by the consuming module. HL7's extension/schema concern lives
here (and in the module image; see below), *not* as a platform artifact type.

> **Not a platform capability (corrected 2026-06-01).** Earlier drafts modelled
> "pluggable extension artifacts" as a fourth platform capability — a typed
> `extensions` array on `runtimeConfig`, a dataloader processor, a catalog table,
> and per-deployment pkg-proxy resolution + mounting. **Removed.** HL7 extension
> packs are HL7-specific; the dataloader neither sees nor cares about those NPM
> artifacts. They reach the module at **`npm install` / image-build time** (baked
> into the module image), and any runtime selection among baked-in packs is
> expressed inside the module-defined opaque `config`. Sections that described the
> platform-typed extension path are struck through below.

This document enumerates every codebase touch required.

Cross-reference: [`DESIGN.md`](DESIGN.md) for the module's design; the discussion that produced both is the canonical rationale.

---

## 0. Summary

| Component | Change kind | Touch surface |
|---|---|---|
| Hydra schemas | additive | 3 new schemas (daemonMode lives inline), 2 schema extensions; `config` is an untyped jsonb |
| Hydra DDL | additive | 2 column adds (no extension catalog table) |
| Hydra DAOs | additive | DAO read/write of new column |
| Hub Server (REST API) | additive | OpenAPI schemas regen; admit-time validation (port collisions only) |
| Hub Server (Dispatcher) | none (auto via schema) | EnsureDeployment auto-carries new field |
| Hub Server (Producers) | refactor + additive | `DeploymentProducerImpl`: resolve + validate; `NodeProducerImpl`: include daemon-mode in fan-out |
| Hub Node (Dispatcher) | refactor | Stop dropping `msg.deployment.runtimeConfig` |
| Hub Node (ContainerManager) | additive | Persist runtimeConfig; auto-start daemon-mode after handshake |
| Hub Node (ContainerDeployment) | refactor | Branch on `daemonMode` to skip `stop()` |
| Hub Node (ContextManager) | refactor | Branch on `daemonMode` to skip idle timer |
| Hub Node (WSHubNode) | none | No protocol change |
| Node-lib (Container.ts) | refactor | Iterate listenerPorts + durability; allocate volumes; inject env; pass opaque `config` to container |
| ~~Node-lib (Extensions)~~ | ~~new~~ | **Dropped** — extensions are baked in at image build, not Node-resolved |
| Dataloader | additive | Read module-root `runtimeConfig.yml` → `module_version.runtime_config`. **No** `hl7_extension` processor |
| Platform API (REST) | additive | OpenAPI regen — runtimeConfig in module-version + deployment endpoints |
| Platform GraphQL | additive | Schema additions; resolver pass-through |
| Hub Client SDK | regen | Codegen only |
| pkg-proxy | none | Not involved — no per-deployment extension fetch |
| Node Alert system | (separate) | A failing `/healthz` raises a Node alert; Node-Alert↔platform integration is a separate topic |
| Portal API / UI | additive (later) | Operator UI for runtimeConfig; out of scope for v1 of this module |

---

## 1. Hydra core schemas (additive)

The data-model layer everything else regenerates from.
Path: [`com/hydra/core/schemas/`](../../../../com/hydra/core/schemas/)

### 1.1 New schemas

#### `hub/deployment/DeploymentRuntimeConfig.yml`

```yaml
description: Runtime configuration for a Deployment — daemon mode, listener ports,
  and durable volumes (platform-interpreted), plus an opaque module-defined config.
  Resolved by Hub Server from ModuleVersion defaults overridden by Deployment-level
  fields, sent on EnsureDeployment.
type: object
properties:
  daemonMode:
    type: boolean
    default: false
    description: When true, container starts at node boot, ignores idle timeouts,
      and does not passivate on connection-going-to-zero.
  listenerPorts:
    type: array
    items: { $ref: './DeploymentListenerPort.yml' }
  durability:
    type: array
    items: { $ref: './DeploymentDurability.yml' }
  config:
    type: object
    additionalProperties: true
    description: Opaque, module-defined configuration. The platform stores and
      transports this jsonb verbatim and NEVER interprets it — its shape and
      meaning are defined by the consuming module. Hub Node delivers it to the
      container at start (see §5.1). HL7 uses it for receiver knobs (e.g. which
      baked-in extension packs to activate, version handling). This replaces the
      former typed `extensions` array.
  defaultLifecycle:
    $ref: '../connection/LifecycleConfig.yml'
    description: Fallback LifecycleConfig used when a Connection omits its own.
```

> `config` is intentionally untyped at the platform layer. Validation of its
> contents is the module's own concern, applied at module boot — not a platform
> admit-time check.

#### `hub/deployment/DeploymentListenerPort.yml`

```yaml
type: object
required: [name, protocol]
properties:
  name:           { type: string, example: mllp }
  port:
    type: integer
    nullable: true
    default: null
    description: Bind port (1:1 host:container). Null = ephemeral; node allocates.
      Pinned values rejected at admit-time if another deployment on the same node
      pins the same port.
  bindAddress:    { type: string, default: "0.0.0.0" }
  protocol:       { type: string, enum: [tcp, udp], default: tcp }
  tls:            { type: boolean, default: false }
```

#### `hub/deployment/DeploymentDurability.yml`

```yaml
type: object
required: [volumeName, mountPath]
properties:
  volumeName:     { type: string }
  mountPath:      { type: string, example: /var/lib/module }
  access:         { type: string, enum: [rw, ro], default: rw }
  retention:
    type: object
    properties:
      maxBytes:   { type: integer }
      maxAge:     { type: string, format: duration }
```

#### ~~`hub/deployment/DeploymentExtensionRef.yml`~~ — **dropped**

There is no platform-typed extension reference. Extension packs are HL7-specific
and reach the module at image-build time (`npm install`); any runtime selection
among them is expressed inside the opaque `config` object above, which the
platform does not parse.

### 1.2 Schema extensions

#### `hub/deployment/Deployment.yml`

Add optional `runtimeConfig`:

```yaml
properties:
  ...
  runtimeConfig:
    $ref: './DeploymentRuntimeConfig.yml'
    description: Effective runtime config (server-resolved from ModuleVersion
      defaults + per-Deployment overrides). Populated by Hub Server before
      sending EnsureDeployment.
```

`DeploymentSlimView.yml` and `DeploymentView.yml` inherit via `allOf`; no further change there.

#### `platform/store/module/ModuleVersion.yml`

Add optional `runtimeConfig`:

```yaml
properties:
  ...
  runtimeConfig:
    $ref: '../../../hub/deployment/DeploymentRuntimeConfig.yml'
    description: Module-author-declared defaults. Populated by dataloader from the
      module package's root `runtimeConfig.yml` file (declared in package.json
      `files`). The file is optional in general but MANDATORY when
      `daemonMode: true`. See §6.3.
```

#### `hub/message/EnsureDeployment.yml`

**No change.** Already carries the full `Deployment.yml`; new field rides through.

---

## 2. Hydra DDL + DAOs (additive)

Path: [`com/hydra/dao/`](../../../../com/hydra/dao/), [`com/hydra/schema/sql/`](../../../../com/hydra/schema/)

### 2.1 New columns

#### Hub schema — deployment

Add a timestamped migration under `com/hydra/schema/sql/scripts/` (or wherever the schema dir routes migrations for hub):

```sql
ALTER TABLE hub.deployment_view
  ADD COLUMN runtime_config JSONB;
```

`runtime_config` is the per-deployment override — what the operator set, not the resolved effective config. Resolution happens in `DeploymentProducerImpl` at send time and is **not** stored. Storing only overrides means changes to `ModuleVersion.runtime_config` defaults flow through to existing deployments without a backfill.

#### Catalog schema — module_version

```sql
ALTER TABLE catalog.module_version_view
  ADD COLUMN runtime_config JSONB;
```

(or wherever the canonical `module_version` table lives — confirm against `com/hydra/schema/sql/`).

### 2.2 DAO read/write

#### `com/hydra/dao/sql/hub/deployment/create.sql`

Add `runtime_config` column + `${runtimeConfig}` value:

```sql
insert into hub.deployment_view (
  id, name, description, owner_id, status, node_id, module_version_id,
  runtime_config
) values (
  ${id}, ${name}, ${description}, ${ownerId}, ${status}, ${nodeId}, ${moduleVersionId},
  ${runtimeConfig}
);
```

Same for `update.sql`, `list.sql`, `getSlim.sql`, `search.sql` — every SELECT/UPDATE that returns or modifies the deployment row.

#### `com/hydra/dao/src/hub/DeploymentDAO.ts`

JSONB roundtrip — the DAO already handles JSON for other fields, so this is a column-name addition in the column list + the param map. Standard pattern.

#### Catalog DAO

Same shape on the `ModuleVersionDAO` (path: depends on where the canonical module-version DAO lives — confirm in `com/hydra/dao/src/store/` or equivalent).

### 2.3 ~~New catalog table for HL7 extensions~~ — **dropped**

There is no extension catalog table. The dataloader does not process HL7 extension
NPM packages, and the platform does no deploy-time extension resolution — so there
is nothing to catalog. Extension packs are baked into the module image at build
time. (Corrected 2026-06-01; see the §7 review note in `DESIGN.md`.)

---

## 3. Hub Server

Path: [`com/hub/server/src/`](../../../../com/hub/server/src/)

### 3.1 `DeploymentProducerImpl.ts` — resolve + validate

Today: [`com/hub/server/src/producer/DeploymentProducerImpl.ts:204`](../../../../com/hub/server/src/producer/DeploymentProducerImpl.ts) calls `dispatcher.ensureDeployment(node, extant)` with the deployment as-loaded.

Add a resolution step before each `ensureDeployment` call site:

```ts
import { resolveRuntimeConfig } from './runtimeConfigResolver.js';

const moduleVersion = await new ModuleDAO(dbTxn).getModuleVersion(deployment.moduleVersionId);
deployment.runtimeConfig = resolveRuntimeConfig(
  moduleVersion.runtimeConfig,
  deployment.runtimeConfig    // the operator-set overrides
);
this.context.dispatcher.ensureDeployment(node, deployment).catch(...);
```

`resolveRuntimeConfig` is a shallow merge — deployment-level wins per field, arrays replace rather than concatenate (operators replace the `listenerPorts`/`durability` list wholesale; merging surprises). The opaque `config` object is merged shallowly at the top level only (deployment override wins per top-level key); the platform does not reach into its nested shape. Lives in a new utility module alongside the producer.

Add **admit-time validation** in `create()` and `update()` (before the deployment is persisted):

```ts
private async validateRuntimeConfig(
  dbTxn: Connection,
  nodeId: UUID,
  deploymentId: UUID | undefined,
  rc: DeploymentRuntimeConfig,
): Promise<void> {
  // Listener port collisions on same node — the ONLY admit-time check.
  const pinned = (rc.listenerPorts ?? [])
    .filter(lp => lp.port != null && lp.port !== -1);
  if (pinned.length) {
    const conflicts = await new DeploymentDAO(dbTxn)
      .findPinnedPortsOnNode(nodeId, pinned.map(p => p.port), deploymentId);
    if (conflicts.length) {
      throw new ConflictError(`port(s) ${pinned.map(p=>p.port).join(',')} already pinned on node`);
    }
  }
  // `rc.config` is opaque — the platform does NOT validate its contents.
  // Module-internal validation (extension packs, HL7 version compat, schema-ID
  // format) happens at module boot, not here.
}
```

`DeploymentDAO.findPinnedPortsOnNode` is one new SELECT:

```sql
SELECT id, jsonb_path_query_array(runtime_config, '$.listenerPorts[*].port') AS pinned
  FROM hub.deployment_view
 WHERE node_id = ${nodeId}
   AND id != ${excludeId}
   AND runtime_config IS NOT NULL
   AND runtime_config @? '$.listenerPorts[*].port ? (@ != null)';
```

(JSONB path syntax assumes PG12+; if older, drop to a join + `jsonb_array_elements`.)

### 3.2 `NodeProducerImpl.ts` — daemon-mode fan-out

[`com/hub/server/src/producer/NodeProducerImpl.ts:717 ensureDeployments`](../../../../com/hub/server/src/producer/NodeProducerImpl.ts) loops over deployments on a node and calls `ensureDeployment` for each. Today it filters on `adminStatus !== Disabled`. No change needed in the loop itself — daemon-mode deployments are just deployments. **But:** the post-handshake watermark path (the optimization that skips already-ensured deployments) must NOT skip daemon-mode deployments whose `runtimeConfig` content has changed even if the deployment row's `updated` timestamp didn't move. The current code uses `resourceUpdated = deployment.updated` which is the deployment row's mtime — that already moves when `runtime_config` is updated, so this is automatic. Confirm with a unit test.

### 3.3 `Dispatcher.ts` — none

[`com/hub/server/src/Dispatcher.ts:823`](../../../../com/hub/server/src/Dispatcher.ts) builds `EnsureDeployment(... deployment)`. Since `Deployment` now carries `runtimeConfig`, the wire payload automatically includes it. No code change.

### 3.4 REST API — OpenAPI schemas

Path: `com/hub/server/src/api/`

- `deployment.yml` — accept and return `runtimeConfig` on create / update / get / search.
- `module.yml` — accept and return `runtimeConfig` on the module-version endpoints.
- Add response code for the new admit-time error (`409` for listener-port collision). No `404`-for-extension — extension resolution is not a platform concern.

Codegen regenerates the client and server stubs.

### 3.5 ~~`ServerContext` — artifact catalog accessor~~ — **dropped**

Was needed only for the (now removed) extension-resolvability check. No artifact
catalog accessor is required; admit-time validation is port-collision only (§3.1).

---

## 4. Hub Node

Path: [`com/hub/node/src/`](../../../../com/hub/node/src/)

### 4.1 `Dispatcher.ts` — stop dropping the payload

[`com/hub/node/src/Dispatcher.ts:528`](../../../../com/hub/node/src/Dispatcher.ts) (and three other `ensureDeployment` call sites at lines 628, 714, 781) currently call:

```ts
await this.containerManager.ensureDeployment(
  this.loggerMap[msg.id.toString()],
  msg.deployment.id,
  location,
  msg.moduleVersion.version
);
```

Widen to:

```ts
await this.containerManager.ensureDeployment(
  this.loggerMap[msg.id.toString()],
  msg.deployment.id,
  location,
  msg.moduleVersion.version,
  msg.deployment.runtimeConfig    // ← new
);
```

### 4.2 `ContainerManager.ts` — persist + auto-start

[`com/hub/node/src/containers/ContainerManager.ts`](../../../../com/hub/node/src/containers/ContainerManager.ts)

- `ensureDeployment` signature widens; passes `runtimeConfig` into `ContainerDeployment` constructor (which stores it).
- New method: `startDaemonDeployments(logger)` — after handshake completes (called from `WSHubNode` after the post-handshake `ensureDeployments` fan-out), iterates `slot.deployments.list()`, finds ones with `runtimeConfig.daemonMode === true`, and calls `ensure(start)` for any not already running. Idempotent.
- The slot deployments table already persists per-deployment state (`port`, `containerId`, etc.). Add `runtimeConfig` to the persisted state so a node restart can rebuild without waiting for the server to re-send `EnsureDeployment`.

### 4.3 `ContainerDeployment.ts` — skip stop() when daemon

[`com/hub/node/src/containers/ContainerDeployment.ts:414`](../../../../com/hub/node/src/containers/ContainerDeployment.ts):

```ts
// today
if (this.connections.size === 0) {
  await this.stop(logger);
}

// becomes
if (this.connections.size === 0 && !this.runtimeConfig?.daemonMode) {
  await this.stop(logger);
}
```

That single branch is the entire passivation opt-out. `start()` already handles "container is already running" gracefully.

### 4.4 `ContextManager.ts` — skip idle timer when daemon

[`com/hub/node/src/ContextManager.ts:99 refreshTimeout`](../../../../com/hub/node/src/ContextManager.ts) — pass `daemonMode` through:

```ts
async ensure(
  logger: LoggerEngine,
  deploymentId: UUID,
  connectionId: UUID,
  node: HubNode,
  lifecycleConfig?: LifecycleConfig,
  daemonMode?: boolean,    // ← new
): Promise<ExecutionContext> {
  ...
  if (!daemonMode) {
    this.refreshTimeout(logger, connectionId, context, timeout);
  }
  return context;
}
```

Callers in `Dispatcher.ts:getExecutionContext` need to look up the deployment's `runtimeConfig.daemonMode` from the slot store before calling `ensure`.

### 4.5 `WSHubNode.ts` — none

[`com/hub/node/src/WSHubNode.ts`](../../../../com/hub/node/src/WSHubNode.ts) is the WebSocket transport. The protocol is unchanged. The only addition is a hook after the post-handshake `ensureDeployments` fan-out completes to call `containerManager.startDaemonDeployments()` (per §4.2). Single line, no protocol implication.

---

## 5. Node-lib

Path: [`com/hub/node-lib/src/`](../../../../com/hub/node-lib/src/)

### 5.1 `docker/Container.ts` — generalize port + volume

[`com/hub/node-lib/src/docker/Container.ts:298-310`](../../../../com/hub/node-lib/src/docker/Container.ts) hardcodes one port:

```ts
const createArgs = ['run', '-d', '--name', this.deploymentId, '--restart', 'unless-stopped'];
for (const [key, value] of Object.entries(labels)) {
  createArgs.push('--label', `${key}=${value}`);
}
createArgs.push('-p', `${port}:8888`);    // ← the only port handling today
createArgs.push(fullImage);
```

Generalize:

```ts
createArgs.push('-p', `${operationsPort}:8888`);

for (const lp of runtimeConfig?.listenerPorts ?? []) {
  const p = lp.port ?? await slot.ports.allocatePort();
  createArgs.push('-p', `${lp.bindAddress}:${p}:${p}/${lp.protocol}`);
  envVars[`LISTENER_PORT_${lp.name.toUpperCase()}`] = String(p);
}

for (const d of runtimeConfig?.durability ?? []) {
  await this.ensureNamedVolume(d.volumeName);
  createArgs.push('-v', `${d.volumeName}:${d.mountPath}:${d.access ?? 'rw'}`);
}

// Opaque module config — delivered verbatim, never interpreted by the Node.
// One generic mechanism for every module that uses `config`.
if (runtimeConfig?.config != null) {
  envVars['MODULE_CONFIG'] = JSON.stringify(runtimeConfig.config);
}

for (const [k, v] of Object.entries(envVars)) {
  createArgs.push('-e', `${k}=${v}`);
}

createArgs.push(fullImage);
```

`MODULE_CONFIG` is the generic delivery channel: the Node serializes the opaque
`config` to JSON and injects it; the module parses it (HL7 reads it in
`ModuleConfig.java`). No extension mount loop, no `EXTENSION_DIR` injection from
the Node — extension packs are baked into the image at build (§7 of `DESIGN.md`),
so `EXTENSION_DIR` (if used) points at an in-image path the module owns.

Also: after start, write the resolved listener-port values back to `slot.deployments.update()` so the server can fetch them via `GET /deployments/{id}` and surface to operators.

### 5.2 ~~New: `docker/Extensions.ts`~~ — **dropped**

No per-deployment extension resolution. pkg-proxy is not involved. Extension packs
arrive via `npm install` at image build, not via a Node-side tarball fetch + mount.

### 5.3 `ensureNamedVolume(name)`

One-liner via `docker volume create --label slot=<name>` (idempotent — returns the volume name if it exists). Volumes are NEVER auto-deleted; lifecycle is tied to deployment undeploy, not passivation. Add an undeploy hook in `ContainerManager` to `docker volume rm` after the container is removed.

---

## 6. Platform — Dataloader

Path: [`com/platform/dataloader/src/`](../../../../com/platform/dataloader/src/)

### 6.1 ~~New processor~~ — **dropped**

There is no `hl7-extension` artifact loader and no `import-artifact: hl7_extension`
loader registration. The dataloader does not see or care about HL7 extension NPM
packages. (Corrected 2026-06-01.) The Validator's `import-artifact` dispatch is
unchanged; no `hl7-extension` entry is added.

### 6.2 ~~Register in `DataLoader.ts`~~ — **dropped**

See §6.1.

### 6.3 Module loader — read `runtimeConfig.yml`

[`com/platform/dataloader/src/processors/module/ModuleArtifactLoader.ts`](../../../../com/platform/dataloader/src/processors/module/ModuleArtifactLoader.ts):

- Read the module package's root **`runtimeConfig.yml`** file (declared in
  `package.json` `files`, so it ships in the tarball).
- The file is **optional in general** but **MANDATORY when `daemonMode: true`** —
  a daemon module with no `runtimeConfig.yml` is a packaging error the dataloader
  rejects. (A daemon with no declared listener ports / durability / config is a
  misconfiguration, not a default.)
- Validate it against the `DeploymentRuntimeConfig.yml` schema (the typed
  `daemonMode`/`listenerPorts`/`durability` fields; `config` is passed through
  un-validated — it's opaque).
- Store as `runtime_config` JSONB on the `module_version` row.

This replaces the earlier "read the `auditmation.runtime` block from package.json"
plan — module-author-declared defaults now live in a dedicated file, not a
package.json sub-block. This is the "module-author-declared defaults" half of the
resolution path.

---

## 7. Platform API (REST) + GraphQL

### 7.1 REST

Path: `com/platform/api/src/api/`

- `module-version.yml` — include `runtimeConfig` on responses.
- `deployment.yml` — accept and return `runtimeConfig` on create/update/get/search.

The platform-side endpoints differ from Hub-Server-side endpoints — both need updating since deployments and module versions are surfaced through both.

### 7.2 GraphQL

Path: `com/platform/graphql-api/src/`

- Schema additions for `DeploymentRuntimeConfig`, `DeploymentListenerPort`, etc.
- Resolvers pass-through (DAO already returns the field once §2 lands).
- No new mutations — the existing `updateDeployment` accepts the new field via input-type extension.

### 7.3 Hub Client SDK

Path: `com/hub/sdk/` + `com/hub/client-server/`. Codegen-only — regenerates from the updated OpenAPI.

---

## 8. Health monitoring — Node-local, via the Node Alert system

**Corrected 2026-06-01.** `/healthz` is **not** wired into the platform event
system. It is a Node-only concern: the Hub Node polls `/healthz` to know whether
the container is healthy, exactly as it would for any container. There is **no**
`DeploymentDegradedReason` enum, **no** `HealthCheckFailing` value, and **no**
`DeploymentInfo.status`/event-handler plumbing for it.

- **Health check poller** — Hub Node grows a 30s interval that probes `/healthz`
  on daemon-mode deployments. New file: `com/hub/node/src/HealthChecker.ts` (or
  co-locate in `ContainerManager`).
- **On failure → the Node Alert system activates.** A failing `/healthz` raises a
  **Node alert**, not a platform event. Per DESIGN §9 the listener port stays open
  and the container is **not** auto-restarted (TCP sessions from sending systems
  are expensive to rebuild; don't restart on a transient health flap) — restart is
  operator-driven.

> **Separate topic.** Cohesive integration between the **Node Alert system** and
> the platform (so operators see Node alerts platform-side) is real and needed, but
> it is a separate effort with its own design — explicitly out of scope for this
> module. Nothing here depends on it landing.

---

## 9. Documentation updates

Update the CLAUDE.md / Architecture.md files that codify each component's current model:

- [`com/hub/Architecture.md`](../../../../com/hub/Architecture.md) — add daemon-mode + listener-port + durability to the deployment lifecycle section.
- [`com/hub/server/CLAUDE.md`](../../../../com/hub/server/CLAUDE.md) — ensureDeployment now carries runtimeConfig; resolution happens server-side.
- [`com/hub/node/CLAUDE.md`](../../../../com/hub/node/CLAUDE.md) — daemon-mode deployments + listener ports + durability + opaque `config` passthrough (`MODULE_CONFIG`). No extension mount.
- [`com/platform/dataloader/CLAUDE.md`](../../../../com/platform/dataloader/CLAUDE.md) — reads module-root `runtimeConfig.yml` → `module_version.runtime_config` (mandatory when `daemonMode`). No `hl7-extension` processor.
- [`org/module/CLAUDE.md`](../../../../org/module/CLAUDE.md) (the module-monorepo doc) — new `runtimeConfig.yml` file convention (in package.json `files`, mandatory for daemon modules). No `auditmation.runtime` block, no `hl7-extension` package type.

---

## 10. Rollout order

Ordered for incremental landing — each step is independently mergeable + revertible.

1. **Hydra schemas** (§1). Generate types; nothing consumes the new fields yet, but downstream codegen sees them.
2. **DDL migration** (§2.1). Add columns; nothing reads them yet.
3. **DAOs** (§2.2). Read/write the new column; surface only via direct queries.
4. **Dataloader module loader** (§6.3). Modules shipping `runtimeConfig.yml` start populating `module_version.runtime_config`.
5. **Hub Server resolution + validation** (§3.1). `EnsureDeployment` starts carrying `runtimeConfig`. Hub Node ignores the new field — no behavior change yet.
6. **Hub Node — pipe through** (§4.1). Node receives + persists `runtimeConfig` (incl. opaque `config`) in slot state, still ignores semantically.
7. **Hub Node — honor `daemonMode`** (§4.3, §4.4). Branches on the flag. First behavioral change. Test against a synthetic always-on module.
8. **Hub Node — listener ports + durability + `MODULE_CONFIG`** (§5.1). `-p` / `-v` loops and opaque-config injection. Test with a contrived module that listens on an extra port.
9. **Hub Node — auto-start daemon-mode** (§4.2). Post-handshake scan.
10. **REST + GraphQL** (§7). Operators can create deployments with runtimeConfig via the platform UI/API.
11. **Health check poller** (§8). Daemon-mode deployments polled; failures raise Node alerts.
12. **HL7 module itself** ([`DESIGN.md`](DESIGN.md)). The first real consumer.

Steps 1–9 are platform infrastructure; an internal "always-on echo" test module proves them out without committing to HL7 semantics. Step 12 is module-side work that depends on all the platform work being in place. (There is no extension-resolution or `hl7-extension`-processor step — extensions are baked into the module image at build, not resolved by the platform.)

---

## 11. Open questions

1. **Backfill on existing deployments.** Existing deployment rows will have `runtime_config IS NULL`. That's correct — no daemon mode, no listener ports. But: should the dataloader, when it sees a module-version update whose `runtimeConfig.yml` changed, surface a "you may want to update existing deployments" prompt to operators? Or is per-deployment override always opt-in? Suggest: opt-in. Module-version defaults flow through unchanged for new deployments; existing deployments keep their (null) override.
2. **`runtime_config` on `ModuleVersion` — visible via which API?** Operators creating a deployment via the UI should see what defaults the module declares (so they know what listener ports to pin, etc.). REST API for `module-version` should surface `runtimeConfig` on GET; UI needs to render it before the deployment-create form. Not in scope for v1 of the HL7 module but worth flagging.
3. **Volume retention vs deployment lifecycle.** §5.3 says "volumes deleted on undeploy, not on passivation." Verify against the existing deployment-removal code path in `ContainerManager` — is there a hook there today that we extend, or does undeploy go through a different path?
4. **Opaque `config` delivery channel.** §5.1 injects `config` as the `MODULE_CONFIG` env var (JSON). Confirm there's no practical size limit that bites (large config → consider a mounted file under the durability volume instead, still generic). Env is simplest for v1; revisit if a module needs a large `config`.
5. **`startDaemonDeployments` ordering.** When the node receives a fan-out of `ensureDeployment` messages at boot, daemon-mode deployments should auto-start as those messages arrive. With extensions baked into the image (no runtime pull), there is no per-extension download to defer — container start is gated only on the image being present and the volume mounted. Still confirm nothing in the handshake/state code path assumes the auto-start completes synchronously.

---

## See also

- [`DESIGN.md`](DESIGN.md) — the HL7 module design that motivates this work.
- [`org/module/package/interface/dataproducer/documentation/`](../../interface/dataproducer/documentation/) — DataProducer interface canon.
- [`com/hub/CLAUDE.md`](../../../../com/hub/CLAUDE.md) — Hub platform context.
- [`com/hydra/core/schemas/hub/connection/LifecycleConfig.yml`](../../../../com/hydra/core/schemas/hub/connection/LifecycleConfig.yml) — the per-connection lifecycle that `runtimeConfig.defaultLifecycle` parallels.
