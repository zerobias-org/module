# HL7 v2 Receiver — Operator Guide

Day-to-day operation of the `@zerobias-org/module-hl7-v2` daemon: pinning the
listener port, sizing the durable volume, baking in extensions, reading health,
and finding the listener port when it's ephemeral. For architecture see
[`README.md`](README.md); for the full design see [`DESIGN.md`](DESIGN.md).

Everything an operator controls lives in the deployment's `runtimeConfig` (defaults
declared in [`runtimeConfig.yml`](runtimeConfig.yml); per-deployment overrides
resolved by Hub Server at `EnsureDeployment`).

---

## 1. Pinning the listener port

HL7 sources (EHR, lab, ADT feeds) are **statically configured** to dial a fixed
host:port. So in production you almost always **pin** the MLLP port:

```yaml
listenerPorts:
  - { name: mllp, protocol: tcp, port: 2575, bindAddress: "0.0.0.0", tls: false }
```

- The Hub Node maps the container port **1:1 to the host** (no NAT) and injects it
  as the `LISTENER_PORT_MLLP` env var. The module **refuses to start** without it —
  a daemon listener with no port is a misconfiguration, not a default.
- `2575` is the IANA-registered HL7-over-MLLP port and a sensible default; any free
  host port works. Point your sending systems at `<node-host>:2575`.
- One feed per deployment is the recommended topology (clean ACLs, blast radius =
  one feed). Run multiple deployments for multiple feeds rather than many ports on
  one container.

**Leave it unpinned** (`port: null`) only for test feeds — see §5.

---

## 2. Sizing the durable volume

The buffer (`buffer.db`, SQLite + WAL) lives on a **named volume** so messages
survive container restarts and re-deploys. Ack-on-persist means an `MSA|AA` the
sender received is guaranteed on this volume.

```yaml
durability:
  - volumeName: hl7-buffer
    mountPath: /var/lib/module
    access: rw
    retention:
      maxBytes: 10737418240    # 10 GiB
      maxAge: P7D              # 7 days
```

Sizing guidance:

- **Retention sweeps only `acked` rows.** Un-acked (new / in-flight) messages are
  **never** evicted — they are undrained clinical data. If the pipeline stops
  draining, the buffer grows until `maxBytes`; size for *peak undrained backlog*,
  not steady state.
- Estimate: `peak msgs/day × avg message size × days-between-drains`, with healthy
  headroom. A typical ADT/ORU message materializes to a few KB of JSON plus the raw
  ER7; 10 GiB holds millions of messages.
- `maxAge` / `maxBytes` bound the **acked** tail (already-consumed audit copies).
  Lower them to reclaim space sooner; raise them to keep a longer replay/audit
  window.
- Set `connectionProfile.ackDurability: full` for zero-loss clinical feeds (fsync
  per row); `normal` (fsync at WAL checkpoint) is the faster default.

Watch `db.walBytes` and `listener.bufferDepth` / `oldestUnackedSec` in `/healthz`
(§4) — a steadily climbing `oldestUnackedSec` means the pipeline isn't draining.

---

## 3. Reading health

`GET https://<node-host>:8888/healthz` (operations port; the Hub Node polls it
every 30s):

```json
{ "listener": { "up": true, "lastReceived": "2026-06-01T10:30:00Z",
                "bufferDepth": 142, "oldestUnackedSec": 3 },
  "db":       { "walBytes": 12345 },
  "extensions": [ { "artifact": "hl7-extension-epic-adt", "schemasLoaded": 7 } ] }
```

| Field | Read it as |
|---|---|
| `listener.up` | MLLP listener accepting connections. `false` → `503` (degraded). |
| `listener.lastReceived` | timestamp of the most recent message (absent if none yet). |
| `listener.bufferDepth` | total rows in the buffer. |
| `listener.oldestUnackedSec` | age of the oldest un-drained message — **the key backlog signal**. Absent when nothing is pending. |
| `db.walBytes` | WAL sidecar size; large/growing can indicate checkpoint pressure. |
| `extensions[]` | which baked-in packs loaded, and how many schemas each added. |

**A failing `/healthz` raises a Node alert — it does not auto-restart.** TCP
connections from sending systems are expensive to rebuild, so the listener port
stays open through a transient flap; restart is operator-driven. (Surfacing Node
alerts platform-side is a separate effort, out of scope here.)

Startup also runs an **MLLP self-test** (dials its own port with a synthetic
no-op message); check the container logs for `MLLP self-test: OK` to confirm the
receive loop is wired end-to-end.

---

## 4. Extensions

Vendor/customer HL7 content (Z-segments, custom tables, augmented structures) is
delivered **install-time**: extension packs are npm dependencies **baked into the
image at build**, not fetched or mounted at runtime. One image = one extension set;
serving a different vendor pack means a different image build.

**To bake a pack into a (derived) image:**

```dockerfile
FROM ghcr.io/zerobias-org/module-hl7-v2:<version>
RUN npm install @zerobias-org/hl7-extension-epic-adt
COPY extensions-staging/ /opt/module/extensions/
#   -> /opt/module/extensions/<pack>/extensions/manifest.json …
```

**To select among baked-in packs at deploy time** (when the image ships more than
it enables by default), use the opaque config — the platform passes it through
untouched:

```yaml
config:
  activeExtensions: [epic-adt]    # omit / empty = all baked-in packs active
```

At boot the module merges each active pack's schemas + structure index and surfaces
its structures under `/by-type/<X>` (e.g. `/by-type/ADT_A01_with_ZPV`). A message is
routed to an augmented structure by the pack's discriminator (e.g. *MSH-3 == EPIC
and code ADT → `ADT_A01_with_ZPV`*). Confirm what loaded via `extensions[]` in
`/healthz`. Validation (version compatibility, no duplicate schema IDs) is
module-internal and applied at boot — a misconfigured pack fails the boot loudly.

---

## 5. Finding the listener port when it's not pinned

For test feeds you can leave the port **ephemeral** (`port: null`) and let the node
allocate one. To find what it picked:

- **Hub Node deployment view** — the resolved `runtimeConfig.listenerPorts[].port`
  is recorded on the deployment; the operations API / UI surfaces it.
- **Directly on the node host**, the container's port map shows it:

  ```bash
  docker port <container>            # e.g.  2575/tcp -> 0.0.0.0:49170
  ```

  The container-side and host-side numbers are equal (1:1, no NAT), so the
  allocated number is both.
- The value the Java process is using is the `LISTENER_PORT_MLLP` env var:

  ```bash
  docker exec <container> printenv LISTENER_PORT_MLLP
  ```

Ephemeral ports change on re-deploy — never point a statically-configured clinical
feed at one. Pin the port (§1) for anything real.

---

## Draining (for pipeline authors)

Browsing the collections is read-only; **consuming** messages is the lease cycle on
the `/ops` functions (`invokeFunction`):

1. `ops/take` `{ filter?, max=100, leaseTtl=PT5M }` → `{ leaseId, messages[], remaining }`
   — returns up to `max` drainable messages and marks them `in_flight`.
2. Process them, then `ops/ack` `{ leaseId, controlIds? }` to durably consume
   (omit `controlIds` to ack the whole lease; pass a subset for a partial ack —
   the rest revert to `new` at `leaseTtl`).
3. On a crash mid-lease, the messages auto-revert when `leaseTtl` elapses; or call
   `ops/release` to return them immediately, or `ops/replay` to force `in_flight`
   rows back to `new`.
4. `ops/purge` `{ olderThan }` deletes acked rows ahead of the retention sweep.

At-least-once delivery: a message stays drainable until explicitly acked.
