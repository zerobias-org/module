/**
 * Helpers for the X12 receiver e2e suite: the committed fixtures, the object-id grammar,
 * and feeding files into the running module's inbox.
 *
 * The receiver has no ingest API — files arrive on a volume. So the suite feeds it the way
 * production does: `docker cp` the fixtures into the container's watched source directory,
 * then waits (bounded, derived from the container's own MODULE_CONFIG, i.e. the
 * runtimeConfig.yml `config` gradle started it with) until the module has consumed them.
 * Everything after that goes through the hub-sdk client only.
 */

import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { Readable } from 'node:stream';
import { expect } from 'chai';
import { CoreError, NoSuchObjectError } from '@zerobias-org/types-core-js';
import type { X12 } from '../../hub-sdk/generated/api/index.js';
import { MODULE_DIR, TEST_MODE, X12_CONTAINER } from './constants.js';

export const RECEIVER = '/x12-receiver';
export const ENVELOPE_SCHEMA = 'schema:shared:x12.transaction-envelope';

/** The image's unprivileged user (Dockerfile); it must be able to read every dropped file. */
const MODULE_UID = '10001';
/** Slack on top of stableForSec + pollIntervalSec before the inbox wait gives up. */
const CONSUME_SLACK_SEC = 30;
/** How often the wait nudges the poller (ops/rescan) and re-checks the files. */
const RESCAN_EVERY_MS = 2000;

/** One interchange/functional group/transaction set a fixture carries: ISA13, GS06, ST02. */
export interface ControlNumbers {
  isa13: string;
  gs06: string;
  st02: string;
}

/** A committed fixture as dropped into the inbox for this run. */
export interface InboxFile {
  /** The committed fixture on this machine. */
  local: string;
  /** Name in the inbox (run-unique, so a reused container still sees new files). */
  name: string;
  bytes: Buffer;
  /** `<absolute path at discovery>@<first 12 hex of sha256>` */
  fileId: string;
  /** `/x12-receiver/files/<fileId, percent-encoded for / and %>` */
  nodeId: string;
  sha256: string;
  /** Element keys `<fileId>:<ISA13>:<GS06>:<ST02>`, one per transaction set in the file. */
  elementKeys: string[];
}

/** The watched source the module was started with (config.sources[0]). */
export interface SourceSettings {
  name: string;
  path: string;
  pollIntervalSec: number;
  stableForSec: number;
  consumedSuffix: string;
}

export interface Feed {
  container: string;
  source: SourceSettings;
  f835: InboxFile;
  f837p: InboxFile;
  /** Two interchanges that reuse GS06=101 / ST02=0001; only ISA13 tells them apart. */
  twoIsa: InboxFile;
}

const JAVA_FIXTURES = join(MODULE_DIR, 'java', 'src', 'test', 'resources', 'fixtures');
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

/** The container to feed, or '' when there is none (hub mode without X12_CONTAINER). */
export function moduleContainer(): string {
  if (X12_CONTAINER) {
    return X12_CONTAINER;
  }
  if (TEST_MODE !== 'docker') {
    return '';
  }
  const info = join(MODULE_DIR, 'build', 'module-container.json');
  if (!existsSync(info)) {
    throw new Error(`TEST_MODE=docker but ${info} is missing — run through gradle testDocker (startModuleExec writes it)`);
  }
  return JSON.parse(readFileSync(info, 'utf-8')).containerId as string;
}

/** config.sources[0] + consumedSuffix from the MODULE_CONFIG the container actually runs with. */
export function sourceSettings(container: string): SourceSettings {
  const env: string[] = JSON.parse(docker(['inspect', '--format', '{{json .Config.Env}}', container]));
  const raw = env.find((e) => e.startsWith('MODULE_CONFIG='));
  if (!raw) {
    throw new Error(`container ${container} has no MODULE_CONFIG; cannot derive the inbox path and wait bounds`);
  }
  const config = JSON.parse(raw.slice('MODULE_CONFIG='.length));
  const src = config.sources?.[0];
  if (!src || typeof src.path !== 'string' || !Number.isInteger(src.pollIntervalSec) || !Number.isInteger(src.stableForSec)) {
    throw new Error(`MODULE_CONFIG sources[0] must declare path, pollIntervalSec and stableForSec: ${JSON.stringify(src)}`);
  }
  return {
    name: src.name,
    path: src.path,
    pollIntervalSec: src.pollIntervalSec,
    stableForSec: src.stableForSec,
    consumedSuffix: config.consumedSuffix ?? '.done',
  };
}

/** Worst case from drop to consumption: first sighting ≤ one poll, then stableForSec unchanged, plus slack. */
export function consumeBoundMs(source: SourceSettings): number {
  return (source.stableForSec + source.pollIntervalSec + CONSUME_SLACK_SEC) * 1000;
}

function inboxFile(source: SourceSettings, local: string, name: string, controls: ControlNumbers[]): InboxFile {
  const bytes = readFileSync(local);
  const hash = sha256(bytes);
  const fileId = `${source.path}/${name}@${hash.slice(0, 12)}`;
  return {
    local,
    name,
    bytes,
    fileId,
    nodeId: `${RECEIVER}/files/${encodeSegment(fileId)}`,
    sha256: hash,
    elementKeys: controls.map((c) => `${fileId}:${c.isa13}:${c.gs06}:${c.st02}`),
  };
}

/** The three fixtures for this run (control numbers per java/src/test/resources/fixtures/README.md). */
export function planFeed(container: string, runTag: string): Feed {
  const source = sourceSettings(container);
  return {
    container,
    source,
    f835: inboxFile(source, join(JAVA_FIXTURES, '835-005010X221A1.x12'), `${runTag}-835-005010X221A1.x12`,
      [{ isa13: '000000101', gs06: '101', st02: '0001' }]),
    f837p: inboxFile(source, join(JAVA_FIXTURES, '837P-005010X222A1.x12'), `${runTag}-837P-005010X222A1.x12`,
      [{ isa13: '000000102', gs06: '102', st02: '0001' }]),
    twoIsa: inboxFile(source, join(E2E_FIXTURES, '835-two-interchanges.x12'), `${runTag}-835-two-interchanges.x12`,
      [{ isa13: '000000101', gs06: '101', st02: '0001' }, { isa13: '000000201', gs06: '101', st02: '0001' }]),
  };
}

export function feedFiles(feed: Feed): InboxFile[] {
  return [feed.f835, feed.f837p, feed.twoIsa];
}

/** `docker cp` every fixture into the watched directory and prove uid 10001 can read it. */
export function dropFixtures(feed: Feed): void {
  expect(docker(['exec', feed.container, 'id', '-u']), 'module container user').to.equal(MODULE_UID);
  for (const f of feedFiles(feed)) {
    const target = `${feed.source.path}/${f.name}`;
    docker(['cp', f.local, `${feed.container}:${target}`]);
    try {
      docker(['exec', feed.container, 'test', '-r', target]);
    } catch {
      throw new Error(`${target} is not readable by uid ${MODULE_UID} inside ${feed.container}`);
    }
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => { setTimeout(resolve, ms); });
}

/**
 * Wait until the module has consumed every dropped file, nudging the poller with
 * ops/rescan (the same scan the schedule runs) so the wait is ~stableForSec, not
 * stableForSec + a poll interval. Fails fast when a file lands in the `.error` path.
 */
export async function awaitConsumed(client: X12, feed: Feed): Promise<number> {
  const started = Date.now();
  const deadline = started + consumeBoundMs(feed.source);
  let pending = feedFiles(feed);
  while (pending.length > 0) {
    if (Date.now() > deadline) {
      throw new Error(`not consumed within ${consumeBoundMs(feed.source) / 1000}s: ${pending.map((f) => f.name).join(', ')}`);
    }
    await client.getFunctionsApi().invokeFunction(`${RECEIVER}/ops/rescan`, {});
    const still: InboxFile[] = [];
    for (const f of pending) {
      try {
        const doc = await client.getDocumentsApi().getDocumentData(f.nodeId) as Record<string, unknown>;
        if (doc.status === 'error') {
          throw new Error(`${f.name} went to the error path: ${JSON.stringify(doc)}`);
        }
        if (doc.status !== 'consumed') {
          still.push(f);
        }
      } catch (e) {
        if (!(e instanceof NoSuchObjectError)) {
          throw e;
        }
        still.push(f);
      }
    }
    pending = still;
    if (pending.length > 0) {
      await sleep(RESCAN_EVERY_MS);
    }
  }
  return Date.now() - started;
}

/** Every child of a container, paging through (the receiver pages by pageNumber/pageSize). */
export async function allChildren(client: X12, objectId: string): Promise<Array<Record<string, any>>> {
  const out: Array<Record<string, any>> = [];
  for (let page = 1; ; page++) {
    const res = await client.getObjectsApi().getChildren(objectId, page, 100);
    out.push(...(res.items as unknown as Array<Record<string, any>>));
    if (res.items.length === 0 || out.length >= (res.count ?? 0)) {
      return out;
    }
  }
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

/** Enum values (hub mode) and plain strings (docker mode) as strings. */
export function names(values: unknown[] | undefined): string[] {
  return (values ?? []).map(String);
}

/** A free-form function/element body as the generated `{ [key: string]: object }` type. */
export function body(values: Record<string, unknown>): { [key: string]: object } {
  return values as { [key: string]: object };
}

/**
 * downloadBinary's result as bytes. Over the docker wire the client hands back the body
 * as axios decoded it (UTF-8 text — X12 is ASCII); other transports may return a Buffer,
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
