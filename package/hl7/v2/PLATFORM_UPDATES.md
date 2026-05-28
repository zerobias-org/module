# Platform updates required to support `@auditlogic/module-hl7-v2`

This module is the first user of three new platform capabilities:

1. **Daemon-mode deployments** — containers that don't passivate, auto-start on node boot.
2. **Additional listener ports** — deployments bind extra TCP/UDP ports beyond the standard module operations port.
3. **Durable per-deployment volumes** — named Docker volumes that survive passivation and re-deploy.
4. **Pluggable extension artifacts** — separate NPM packages that contribute additional schema content (Z-segments, custom tables, augmented message structures) at deploy time.

All four ride on a single new field — `Deployment.runtimeConfig` — carried on the existing `EnsureDeployment` message. No new wire messages, no new operations. This document enumerates every codebase touch required.

Cross-reference: [`DESIGN.md`](DESIGN.md) for the module's design; the discussion that produced both is the canonical rationale.

---

## 0. Summary

| Component | Change kind | Touch surface |
|---|---|---|
| Hydra schemas | additive | 4 new schemas, 2 schema extensions |
| Hydra DDL | additive | 2 column adds, 1 new catalog table |
| Hydra DAOs | additive | DAO read/write of new column; 1 new DAO |
| Hub Server (REST API) | additive | OpenAPI schemas regen; admit-time validation |
| Hub Server (Dispatcher) | none (auto via schema) | EnsureDeployment auto-carries new field |
| Hub Server (Producers) | refactor + additive | `DeploymentProducerImpl`: resolve + validate; `NodeProducerImpl`: include daemon-mode in fan-out |
| Hub Node (Dispatcher) | refactor | Stop dropping `msg.deployment.runtimeConfig` |
| Hub Node (ContainerManager) | additive | Persist runtimeConfig; auto-start daemon-mode after handshake |
| Hub Node (ContainerDeployment) | refactor | Branch on `daemonMode` to skip `stop()` |
| Hub Node (ContextManager) | refactor | Branch on `daemonMode` to skip idle timer |
| Hub Node (WSHubNode) | none | No protocol change |
| Node-lib (Container.ts) | refactor | Iterate listenerPorts + durability; allocate volumes; inject env |
| Node-lib (Extensions) | new | Pkg-proxy resolution + tarball extraction + per-deployment mount |
| Dataloader | additive | New `hl7_extension` artifact-loader entry; one new processor class |
| Platform API (REST) | additive | OpenAPI regen — runtimeConfig in module-version + deployment endpoints |
| Platform GraphQL | additive | Schema additions; resolver pass-through |
| Hub Client SDK | regen | Codegen only |
| pkg-proxy | none | Generic npm proxy is artifact-type-agnostic |
| Portal API / UI | additive (later) | Operator UI for runtimeConfig; out of scope for v1 of this module |

---

## 1. Hydra core schemas (additive)

The data-model layer everything else regenerates from.
Path: [`com/hydra/core/schemas/`](../../../../com/hydra/core/schemas/)

### 1.1 New schemas

#### `hub/deployment/DeploymentRuntimeConfig.yml`

```yaml
description: Runtime configuration for a Deployment — daemon mode, listener ports,
  durable volumes, and extension artifacts. Resolved by Hub Server from ModuleVersion
  defaults overridden by Deployment-level fields, sent on EnsureDeployment.
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
  extensions:
    type: array
    items: { $ref: './DeploymentExtensionRef.yml' }
  defaultLifecycle:
    $ref: '../connection/LifecycleConfig.yml'
    description: Fallback LifecycleConfig used when a Connection omits its own.
```

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

#### `hub/deployment/DeploymentExtensionRef.yml`

```yaml
type: object
required: [artifact, version]
properties:
  artifact:       { type: string, example: "@auditlogic/hl7-extensions-epic-adt" }
  version:        { type: string, example: "^1.2.0" }
```

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
    description: Module-author-declared defaults. Populated by dataloader from
      the module package's `auditmation.runtime` block in package.json.
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

### 2.3 New catalog table for HL7 extensions

The platform's content distribution already handles arbitrary `import-artifact` types; HL7 extensions just need a row to live in. Two options:

- **A** — Reuse `catalog.artifact` (or similar generic table) keyed by `import_artifact='hl7-extension'`. No DDL.
- **B** — Add a dedicated `catalog.hl7_extension_view` table with the extension's `hl7Version`, `vendor`, `namespace`, `messageGroups` for fast search.

(B) is worth it if operators will search/filter extensions by HL7 version or vendor. (A) is sufficient if discovery is via package name only.

**Pick (A) for v1.** Resolution at deploy time is by `(artifact, version)`; richer search is a later concern.

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

`resolveRuntimeConfig` is a shallow merge — deployment-level wins per field, arrays replace rather than concatenate (operators replace the extension list wholesale; merging surprises). Lives in a new utility module alongside the producer.

Add **admit-time validation** in `create()` and `update()` (before the deployment is persisted):

```ts
private async validateRuntimeConfig(
  dbTxn: Connection,
  nodeId: UUID,
  deploymentId: UUID | undefined,
  rc: DeploymentRuntimeConfig,
): Promise<void> {
  // 1. Listener port collisions on same node
  const pinned = (rc.listenerPorts ?? [])
    .filter(lp => lp.port != null && lp.port !== -1);
  if (pinned.length) {
    const conflicts = await new DeploymentDAO(dbTxn)
      .findPinnedPortsOnNode(nodeId, pinned.map(p => p.port), deploymentId);
    if (conflicts.length) {
      throw new ConflictError(`port(s) ${pinned.map(p=>p.port).join(',')} already pinned on node`);
    }
  }
  // 2. Extension artifacts resolvable in the catalog
  for (const ext of rc.extensions ?? []) {
    const exists = await this.context.artifactCatalog.has(ext.artifact, ext.version);
    if (!exists) throw new NotFoundError(`extension ${ext.artifact}@${ext.version} not in catalog`);
  }
  // 3. Extension HL7 version compatibility checked here too if connectionProfile.hl7Version is on the deployment
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
- Add response codes for new admit-time errors (`409` for port collision, `404` for unknown extension).

Codegen regenerates the client and server stubs.

### 3.5 `ServerContext` — artifact catalog accessor

The validation step needs `this.context.artifactCatalog.has(artifact, version)`. Confirm the existing module-version lookup serves this; if not, a thin facade over `ModuleVersionDAO` + extension catalog lookup.

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

for (const ext of runtimeConfig?.extensions ?? []) {
  const dir = await this.resolveExtension(ext);    // pkg-proxy fetch + extract; §5.2
  createArgs.push('-v', `${dir}:/opt/module/extensions/${path.basename(dir)}:ro`);
}
envVars['EXTENSION_DIR'] = '/opt/module/extensions';

for (const [k, v] of Object.entries(envVars)) {
  createArgs.push('-e', `${k}=${v}`);
}

createArgs.push(fullImage);
```

Also: after start, write the resolved listener-port values back to `slot.deployments.update()` so the server can fetch them via `GET /deployments/{id}` and surface to operators.

### 5.2 New: `docker/Extensions.ts` (or similar)

```ts
async resolveExtension(ext: DeploymentExtensionRef): Promise<string> {
  // 1. Build cache dir: /var/lib/zerobias/extensions/<deployment-id>/<artifact>@<version>/
  // 2. If exists, return.
  // 3. Otherwise: ask pkg-proxy for the tarball URL of <artifact>@<version>
  //    Existing pkg-proxy client lives in node-lib for module fetches; reuse.
  // 4. curl tarball → extract → return dir.
  // 5. Validate: manifest.json present + parseable.
}
```

No changes to pkg-proxy itself — it serves arbitrary npm tarballs.

### 5.3 `ensureNamedVolume(name)`

One-liner via `docker volume create --label slot=<name>` (idempotent — returns the volume name if it exists). Volumes are NEVER auto-deleted; lifecycle is tied to deployment undeploy, not passivation. Add an undeploy hook in `ContainerManager` to `docker volume rm` after the container is removed.

---

## 6. Platform — Dataloader

Path: [`com/platform/dataloader/src/`](../../../../com/platform/dataloader/src/)

### 6.1 New processor

`processors/hl7Extension/HL7ExtensionArtifactLoader.ts`:

```ts
import { ArtifactLoader } from '../ArtifactLoader.js';

export class HL7ExtensionArtifactLoader implements ArtifactLoader<HL7Extension> {
  // ...
  async processFiles(path: string) {
    // 1. Read extensions/manifest.json
    // 2. Validate namespace ownership against package scope
    // 3. Validate schema JSON files conform to canonical SchemaId format
    // 4. Insert into catalog (see §2.3)
  }
}
```

### 6.2 Register in `DataLoader.ts`

[`com/platform/dataloader/src/importer/DataLoader.ts:732`](../../../../com/platform/dataloader/src/importer/DataLoader.ts) is where loaders register. Add:

```ts
this.loaderMap['hl7-extension'] = new HL7ExtensionArtifactLoader(
  transactionLogger, dbTxn, nullZerobiasPackageInfo, this.versions[dep], this.isContentDev
);
```

The Validator at [`com/platform/dataloader/src/importer/Validator.ts:186-188`](../../../../com/platform/dataloader/src/importer/Validator.ts) reads `import-artifact` from `package.json` and looks up the loader — no change needed there once registered.

### 6.3 Module loader — read `runtime_config` from package.json

[`com/platform/dataloader/src/processors/module/ModuleArtifactLoader.ts`](../../../../com/platform/dataloader/src/processors/module/ModuleArtifactLoader.ts):

- Read `package.json` → `auditmation.runtime` block.
- Validate against `DeploymentRuntimeConfig.yml` schema.
- Store as `runtime_config` JSONB on the `module_version` row.

This is the "module-author-declared defaults" half of the resolution path.

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

## 8. Events + monitoring

Path: [`com/platform/events/src/`](../../../../com/platform/events/src/)

Daemon-mode deployments don't follow the standard "ops succeed → healthy" signal. Two additions:

- **`DeploymentDegradedReason` enum** (in `com/hydra/core/schemas/hub/deployment/`) — add `HealthCheckFailing` value for the case where `/healthz` polling fails on a daemon deployment but the container is up.
- **Health check poller** — Hub Node grows a 30s interval that probes `/healthz` on daemon-mode deployments and updates `DeploymentInfo.status` / `degradedReason` accordingly. New file: `com/hub/node/src/HealthChecker.ts` (or co-locate in `ContainerManager`).

The event handlers already react to `DeploymentInfo` changes; no new handler logic needed.

---

## 9. Documentation updates

Update the CLAUDE.md / Architecture.md files that codify each component's current model:

- [`com/hub/Architecture.md`](../../../../com/hub/Architecture.md) — add daemon-mode + listener-port + durability to the deployment lifecycle section.
- [`com/hub/server/CLAUDE.md`](../../../../com/hub/server/CLAUDE.md) — ensureDeployment now carries runtimeConfig; resolution happens server-side.
- [`com/hub/node/CLAUDE.md`](../../../../com/hub/node/CLAUDE.md) — daemon-mode deployments + extension mount + listener ports.
- [`com/platform/dataloader/CLAUDE.md`](../../../../com/platform/dataloader/CLAUDE.md) — new `hl7-extension` processor.
- [`org/module/CLAUDE.md`](../../../../org/module/CLAUDE.md) (the module-monorepo doc) — new `auditmation.runtime` block in module package.json; new `import-artifact: hl7-extension` package type.

---

## 10. Rollout order

Ordered for incremental landing — each step is independently mergeable + revertible.

1. **Hydra schemas** (§1). Generate types; nothing consumes the new fields yet, but downstream codegen sees them.
2. **DDL migration** (§2.1). Add columns; nothing reads them yet.
3. **DAOs** (§2.2). Read/write the new column; surface only via direct queries.
4. **Dataloader module loader** (§6.3). Module packages with `auditmation.runtime` start populating `module_version.runtime_config`.
5. **Hub Server resolution + validation** (§3.1). `EnsureDeployment` starts carrying `runtimeConfig`. Hub Node ignores the new field — no behavior change yet.
6. **Hub Node — pipe through** (§4.1). Node receives + persists `runtimeConfig` in slot state, still ignores semantically.
7. **Hub Node — honor `daemonMode`** (§4.3, §4.4). Branches on the flag. First behavioral change. Test against a synthetic always-on module.
8. **Hub Node — listener ports + durability** (§5.1). `-p` and `-v` loops. Test with a contrived module that listens on an extra port.
9. **Hub Node — auto-start daemon-mode** (§4.2). Post-handshake scan.
10. **Hub Node — extension resolution** (§5.2). Pkg-proxy fetch + mount.
11. **Dataloader — `hl7-extension` processor** (§6.1, §6.2). Extensions become loadable.
12. **REST + GraphQL** (§7). Operators can create deployments with runtimeConfig via the platform UI/API.
13. **Health check poller** (§8). Daemon-mode deployments report degraded state on `/healthz` failure.
14. **HL7 module itself** ([`DESIGN.md`](DESIGN.md)). The first real consumer.

Steps 1–11 are platform infrastructure; an internal "always-on echo" test module proves them out without committing to HL7 semantics. Step 14 is module-side work that depends on all the platform work being in place.

---

## 11. Open questions

1. **Backfill on existing deployments.** Existing deployment rows will have `runtime_config IS NULL`. That's correct — no daemon mode, no listener ports, no extensions. But: should the dataloader, when it sees a module-version update that adds `auditmation.runtime`, surface a "you may want to update existing deployments" prompt to operators? Or is per-deployment override always opt-in? Suggest: opt-in. Module-version defaults flow through unchanged for new deployments; existing deployments keep their (null) override.
2. **`runtime_config` on `ModuleVersion` — visible via which API?** Operators creating a deployment via the UI should see what defaults the module declares (so they know what listener ports to pin, etc.). REST API for `module-version` should surface `runtimeConfig` on GET; UI needs to render it before the deployment-create form. Not in scope for v1 of the HL7 module but worth flagging.
3. **Volume retention vs deployment lifecycle.** §5.3 says "volumes deleted on undeploy, not on passivation." Verify against the existing deployment-removal code path in `ContainerManager` — is there a hook there today that we extend, or does undeploy go through a different path?
4. **Extension HL7 version compatibility.** §3.1 mentions validating `ext.auditmation.hl7.version` against the deployment's configured HL7 version. The deployment doesn't know its own HL7 version today — that lives in `connectionProfile.hl7Version`, which is per-secret, not per-deployment. Where does the version live for validation? Options: (a) duplicate it onto `runtimeConfig.hl7Version` for the receiver module's case; (b) defer the check to module boot (rejected — too late); (c) require the operator to declare it explicitly on the deployment. (c) is cleanest.
5. **`startDaemonDeployments` ordering.** When the node receives a fan-out of `ensureDeployment` messages at boot, daemon-mode deployments should auto-start as those messages arrive — but extensions referenced from those deployments may take seconds to pull from pkg-proxy. The handshake-recovery path needs to not block the WebSocket while extensions download. Background-task each extension fetch, defer container start until extensions are ready. Affects `WSHubNode` only insofar as it kicks off a non-blocking async; verify nothing in the handshake/state code paths assumes synchronous completion.

---

## See also

- [`DESIGN.md`](DESIGN.md) — the HL7 module design that motivates this work.
- [`org/module/package/interface/dataproducer/documentation/`](../../interface/dataproducer/documentation/) — DataProducer interface canon.
- [`com/hub/CLAUDE.md`](../../../../com/hub/CLAUDE.md) — Hub platform context.
- [`com/hydra/core/schemas/hub/connection/LifecycleConfig.yml`](../../../../com/hydra/core/schemas/hub/connection/LifecycleConfig.yml) — the per-connection lifecycle that `runtimeConfig.defaultLifecycle` parallels.
