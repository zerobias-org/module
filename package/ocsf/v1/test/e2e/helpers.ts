/**
 * Helpers for the OCSF Receiver e2e suite: the committed fixtures, the object-id grammar,
 * feeding files into the running module's inbox, and the preconditions the suite refuses to
 * run without.
 *
 * The receiver's ingest path is a volume. The container gradle starts runs with the committed
 * runtimeConfig.yml `config`, where allowFileManagement is false — so there is no upload path
 * through the API in this harness (java/scripts/e2e-local.sh covers the upload). The suite
 * therefore feeds it the way production does: `docker cp` the fixtures into the watched source
 * directory, then waits (bounded, derived from the container's own MODULE_CONFIG) until the
 * module has consumed them. Everything after that goes through the hub-sdk client, except the
 * raw-wire checks that need what the client hides (isSupported, and the exact JSON text).
 */

import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync } from 'node:fs';
import https from 'node:https';
import { join } from 'node:path';
import { Readable } from 'node:stream';
import axios from 'axios';
import { createWireProtocolClient } from '@zerobias-org/module-test-client';
import { expect } from 'chai';
import { CoreError } from '@zerobias-org/types-core-js';
import type { Ocsf } from '../../hub-sdk/generated/api/index.js';
import { CONTAINER_URL, MODULE_DIR, OCSF_CONTAINER, TEST_MODE } from './constants.js';

export const RECEIVER = '/ocsf-receiver';

/**
 * Slack on top of stableForSec + pollIntervalSec before the inbox wait gives up. The worst case
 * is two poll intervals plus the window (a drop just after a scan), so the slack covers one more
 * interval and scheduling drift.
 */
const CONSUME_SLACK_SEC = 60;
/** How often the wait re-checks the files. */
const POLL_EVERY_MS = 1000;
/** The connection id the suite registers on the container (describeReceiver). */
const WIRE_CONNECTION = 'e2e';

/** A committed fixture as dropped into the inbox for this run. */
export interface InboxFile {
  /** The committed fixture on this machine. */
  local: string;
  /** Name in the inbox (run-tagged, so a name never collides with an earlier drop). */
  name: string;
  bytes: Buffer;
  /** `<absolute path at discovery>@<first 12 hex of sha256>` */
  fileId: string;
  /** `/ocsf-receiver/files/<fileId, percent-encoded for / and %>` */
  nodeId: string;
  sha256: string;
}

/** The watched source + the deployment flags the module was started with. */
export interface ModuleSettings {
  name: string;
  path: string;
  pollIntervalSec: number;
  stableForSec: number;
  consumedSuffix: string;
  /** config.allowFileManagement, exactly as the container got it (only literal true enables). */
  allowFileManagement: boolean;
}

export interface Feed {
  container: string;
  source: ModuleSettings;
  /** Three Authentication events (class 3002) as NDJSON, metadata.uid auth-1..3. */
  auth: InboxFile;
  /** Two File System Activity events (class 1001) as a JSON array, written as OCSF 1.1.0. */
  files: InboxFile;
}

const E2E_FIXTURES = join(MODULE_DIR, 'test', 'e2e', 'fixtures');

/** Percent-encode `%` and `/` only — the receiver's id-segment encoding (ObjectTree.encodeSegment). */
export function encodeSegment(value: string): string {
  return value.replace(/%/g, '%25').replace(/\//g, '%2F');
}

export function sha256(bytes: Buffer): string {
  return createHash('sha256').update(bytes).digest('hex');
}

function docker(args: string[]): string {
  return execFileSync('docker', args, { encoding: 'utf-8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
}

/** The container to feed. Throws (never skips) when there is none. */
export function moduleContainer(): string {
  if (OCSF_CONTAINER) {
    return OCSF_CONTAINER;
  }
  if (TEST_MODE !== 'docker') {
    throw new Error(`TEST_MODE=${TEST_MODE} has no local container to feed: set OCSF_CONTAINER to the `
      + 'receiver\'s container (the inbox is a volume, and every data assertion needs files in it)');
  }
  const info = join(MODULE_DIR, 'build', 'module-container.json');
  if (!existsSync(info)) {
    throw new Error(`TEST_MODE=docker but ${info} is missing — run through gradle testDocker `
      + '(startModuleExec writes it) or set OCSF_CONTAINER');
  }
  return JSON.parse(readFileSync(info, 'utf-8')).containerId as string;
}

/**
 * config.sources[0] + consumedSuffix + allowFileManagement from the container's MODULE_CONFIG,
 * or — when the container got none — the defaults the image bakes in (runtimeConfig.yml).
 */
export function moduleSettings(container: string): ModuleSettings {
  const env: string[] = JSON.parse(docker(['inspect', '--format', '{{json .Config.Env}}', container]));
  const raw = env.find((e) => e.startsWith('MODULE_CONFIG='));
  const config = raw ? JSON.parse(raw.slice('MODULE_CONFIG='.length)) : shippedConfig();
  const src = config.sources?.[0];
  if (!src || typeof src.path !== 'string') {
    throw new Error(`module config sources[0] must declare a path: ${JSON.stringify(src)}`);
  }
  return {
    name: src.name,
    path: src.path,
    pollIntervalSec: src.pollIntervalSec ?? 30,
    stableForSec: src.stableForSec ?? 60,
    consumedSuffix: config.consumedSuffix ?? '.done',
    allowFileManagement: config.allowFileManagement === true,
  };
}

/** runtimeConfig.yml `config`, read without a YAML dependency (the shipped file's simple shape). */
function shippedConfig(): Record<string, any> {
  const yml = readFileSync(join(MODULE_DIR, 'runtimeConfig.yml'), 'utf-8');
  const value = (key: string) => yml.match(new RegExp(`^\\s+${key}:\\s*"?([^"\\s#]+)"?`, 'm'))?.[1];
  return {
    sources: [{
      name: value('- name') ?? 'inbox',
      path: value('path'),
      pollIntervalSec: Number(value('pollIntervalSec')),
      stableForSec: Number(value('stableForSec')),
    }],
    consumedSuffix: value('consumedSuffix'),
    allowFileManagement: value('allowFileManagement') === 'true',
  };
}

/** Worst case from drop to consumption: first sighting ≤ one poll, then stableForSec unchanged, plus slack. */
export function consumeBoundMs(source: ModuleSettings): number {
  return (source.stableForSec + source.pollIntervalSec + CONSUME_SLACK_SEC) * 1000;
}

function inboxFile(source: ModuleSettings, local: string, name: string): InboxFile {
  const bytes = readFileSync(local);
  const hash = sha256(bytes);
  const fileId = `${source.path}/${name}@${hash.slice(0, 12)}`;
  return { local, name, bytes, fileId, nodeId: `${RECEIVER}/files/${encodeSegment(fileId)}`, sha256: hash };
}

/** The fixtures for this run. */
export function planFeed(container: string, runTag: string): Feed {
  const source = moduleSettings(container);
  return {
    container,
    source,
    auth: inboxFile(source, join(E2E_FIXTURES, 'authentication.ndjson'), `${runTag}-authentication.ndjson`),
    files: inboxFile(source, join(E2E_FIXTURES, 'file-activity.json'), `${runTag}-file-activity.json`),
  };
}

export function feedFiles(feed: Feed): InboxFile[] {
  return [feed.auth, feed.files];
}

/** `docker cp` every fixture into the watched directory and prove the module can read it. */
export function dropFixtures(feed: Feed): void {
  for (const f of feedFiles(feed)) {
    const target = `${feed.source.path}/${f.name}`;
    docker(['cp', f.local, `${feed.container}:${target}`]);
    try {
      docker(['exec', feed.container, 'test', '-r', target]);
    } catch {
      throw new Error(`${target} is not readable inside ${feed.container}`);
    }
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => { setTimeout(resolve, ms); });
}

/** A file node's `status:<x>` tag, or undefined while /files does not list it yet. */
async function fileStatus(client: Ocsf, nodeId: string): Promise<string | undefined> {
  try {
    const node = await client.getObjectsApi().getObject(nodeId);
    const tag = (node.tags ?? []).find((t) => t.startsWith('status:'));
    return tag?.slice('status:'.length);
  } catch (e) {
    if (e instanceof CoreError && e.statusCode === 404) {
      return undefined;
    }
    throw e;
  }
}

/**
 * Wait until the module has consumed every dropped file (bounded by the container's own poll
 * cadence and stability window). Fails fast when a file lands anywhere but `consumed`.
 */
export async function awaitConsumed(client: Ocsf, feed: Feed): Promise<number> {
  const started = Date.now();
  const deadline = started + consumeBoundMs(feed.source);
  let pending = feedFiles(feed);
  while (pending.length > 0) {
    if (Date.now() > deadline) {
      throw new Error(`not consumed within ${consumeBoundMs(feed.source) / 1000}s: ${pending.map((f) => f.name).join(', ')}`);
    }
    const still: InboxFile[] = [];
    for (const f of pending) {
      const status = await fileStatus(client, f.nodeId);
      if (status === 'duplicate') {
        throw new Error(`${f.name} was recorded as a duplicate: this container already consumed the same bytes. `
          + 'The suite owns its fixtures only in a fresh container (gradle testDocker starts one per run)');
      }
      if (status !== undefined && status !== 'consumed') {
        throw new Error(`${f.name} was ${status}: every fixture event must be accepted`);
      }
      if (status === undefined) {
        still.push(f);
      }
    }
    pending = still;
    if (pending.length > 0) {
      await sleep(POLL_EVERY_MS);
    }
  }
  return Date.now() - started;
}

/** The rejection of `p` (fails the test when it resolves). */
export async function rejectionOf(p: Promise<unknown>): Promise<any> {
  try {
    await p;
  } catch (e) {
    return e;
  }
  throw new Error('expected the call to be rejected');
}

/**
 * The interface's UnsupportedOperationError response: an illegalArgumentError-shaped body
 * (400) keyed `err.unsupported.operation`. types-core-js has no library for that key, so it
 * deserializes to a GenericCoreError — assert the type by key + status, never the message.
 */
export function expectUnsupported(e: unknown): void {
  expect(e).to.be.instanceOf(CoreError);
  expect((e as CoreError<any>).key).to.equal('err.unsupported.operation');
  expect((e as CoreError<any>).statusCode).to.equal(400);
}

// ── Raw wire (docker mode only) ─────────────────────────────

const insecure = new https.Agent({ rejectUnauthorized: false });

/**
 * module-test-client's describeModule, minus the slot secret. describeModule runs once per
 * `zbb secret` registered for the module and, with none, records one skipped test — a green
 * testDocker that ran nothing. The receiver has no credentials (its connection profile is
 * empty: the daemon reads MODULE_CONFIG, never the profile), so a secret could only ever be
 * invented to satisfy the harness. This connects with an empty profile instead and fails —
 * never skips — without a container.
 */
export function describeReceiver(name: string, fn: (client: Ocsf) => void,
  errorDeserializer?: (data: unknown) => Error): void {
  describe(`${name} [${TEST_MODE}]`, function () {
    this.timeout(240000);
    const ref: { client?: Ocsf } = {};
    const proxy = new Proxy({} as Ocsf, {
      get(_target, prop) {
        if (!ref.client) {
          throw new Error('Client not initialized — use inside it()');
        }
        return (ref.client as any)[prop];
      },
    });
    before(async function () {
      requireRawWire();
      await axios.post(`${CONTAINER_URL}/connections`, { connectionId: WIRE_CONNECTION, connectionProfile: {} },
        { httpsAgent: insecure });
      ref.client = createWireProtocolClient<Ocsf>({
        mode: 'docker', baseUrl: CONTAINER_URL, connectionId: WIRE_CONNECTION, moduleDir: MODULE_DIR, errorDeserializer,
      });
    });
    fn(proxy);
  });
}

/** True when the raw-wire checks can run (docker mode, gradle's CONTAINER_URL). */
export function hasRawWire(): boolean {
  return TEST_MODE === 'docker' && CONTAINER_URL !== '';
}

function requireRawWire(): void {
  if (!hasRawWire()) {
    throw new Error(`raw-wire checks need TEST_MODE=docker and CONTAINER_URL (got ${TEST_MODE}, '${CONTAINER_URL}')`);
  }
}

/** The container's own isSupported answer (not exposed by the hub-sdk client). */
export async function wireIsSupported(operationId: string): Promise<boolean> {
  requireRawWire();
  const res = await axios.get(`${CONTAINER_URL}/connections/${WIRE_CONNECTION}/isSupported/${operationId}`,
    { httpsAgent: insecure });
  return res.data.supported === true;
}

/** The JSON text of an RPC response, before any client parses it. */
export async function wireText(apiClassMethod: string, argMap: Record<string, unknown>): Promise<string> {
  requireRawWire();
  const res = await axios.post(`${CONTAINER_URL}/connections/${WIRE_CONNECTION}/${apiClassMethod}`, { argMap },
    { httpsAgent: insecure, responseType: 'text', transformResponse: (d) => d });
  expect(res.status, `${apiClassMethod} status`).to.equal(200);
  return res.data as string;
}

/**
 * downloadBinary's result as bytes. Over the docker wire the client hands back the body as
 * axios decoded it (UTF-8 text — the fixtures are JSON); other transports may return a Buffer,
 * an ArrayBuffer view, a Blob, a stream or a `{ value }` RequestDetailedFile.
 */
export async function toBytes(v: unknown): Promise<Buffer> {
  if (Buffer.isBuffer(v)) {
    return v;
  }
  if (typeof v === 'string') {
    return Buffer.from(v, 'utf8');
  }
  if (v instanceof ArrayBuffer) {
    return Buffer.from(v);
  }
  if (ArrayBuffer.isView(v)) {
    return Buffer.from(v.buffer, v.byteOffset, v.byteLength);
  }
  if (v instanceof Readable) {
    const chunks: Buffer[] = [];
    for await (const chunk of v) {
      chunks.push(Buffer.from(chunk));
    }
    return Buffer.concat(chunks);
  }
  if (v && typeof (v as Blob).arrayBuffer === 'function') {
    return Buffer.from(await (v as Blob).arrayBuffer());
  }
  if (v && typeof v === 'object' && 'value' in v) {
    return toBytes((v as { value: unknown }).value);
  }
  throw new Error(`unexpected downloadBinary result: ${typeof v}`);
}
