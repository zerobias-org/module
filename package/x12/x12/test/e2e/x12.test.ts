/**
 * X12 receiver e2e — the real container through the real Hub client (docker wire protocol
 * today, Hub Server under TEST_MODE=hub).
 *
 * The receiver is fed the way production feeds it: committed synthetic fixtures are
 * `docker cp`'d into the watched inbox (an 835, an 837P, and one file carrying two
 * interchanges that reuse GS06/ST02), and the suite waits — bounded by the container's own
 * stableForSec/pollIntervalSec — until the module has renamed them `.done`. Every assertion
 * after that goes through the DataProducer client, so every call exercises the Java
 * router's wire names. File names carry a per-run tag, so a reused container still sees new
 * files; assertions are keyed by this run's fileIds, never by totals it cannot own.
 *
 * The drain cycle (take → ack → purge) runs last because it removes the 837P row.
 * Without a container to feed (hub mode, no X12_CONTAINER) the data-dependent tests skip
 * and the fixed-tree, schema, validation and unsupported-operation tests still run.
 */

import { expect } from 'chai';
import { CoreError, IllegalArgumentError, NoSuchObjectError } from '@zerobias-org/types-core-js';
import { describeModule } from '@zerobias-org/module-test-client';
import type { X12 } from '../../hub-sdk/generated/api/index.js';
import type {
  CreateObjectRequest,
  SearchScopeDef,
  UpdateObjectRequest,
  ValidateFunctionInputRequest,
} from '../../hub-sdk/generated/model/index.js';
import {
  ENVELOPE_SCHEMA,
  RECEIVER,
  allChildren,
  awaitConsumed,
  body,
  consumeBoundMs,
  dropFixtures,
  expectUnsupported,
  moduleContainer,
  names,
  planFeed,
  rejectionOf,
  toBytes,
  type Feed,
} from './helpers.js';

const T835 = `${RECEIVER}/by-type/835`;
const C835 = `${T835}/005010X221A1`;
const C837P = `${RECEIVER}/by-type/837P/005010X222A1`;
const ALL_TX = `${RECEIVER}/transactions`;
const STATS = `${RECEIVER}/stats`;
const OPS = `${RECEIVER}/ops`;

describeModule<X12>('X12 Receiver Module', (client) => {
  let feed: Feed | undefined;

  /** The fed fixtures, or skip the test when there is no container to feed. */
  function fed(ctx: Mocha.Context): Feed {
    if (!feed) {
      ctx.skip();
    }
    return feed as Feed;
  }

  before(async function () {
    const container = moduleContainer();
    if (!container) {
      return;
    }
    const plan = planFeed(container, `e2e${Date.now().toString(36)}`);
    this.timeout(consumeBoundMs(plan.source) + 30_000);
    dropFixtures(plan);
    await awaitConsumed(client, plan);
    feed = plan;
  });

  // ── ObjectsApi — the fixed tree ───────────────────────────

  describe('ObjectsApi — fixed tree', () => {
    it('getRootObject returns the "/" container', async () => {
      const root = await client.getObjectsApi().getRootObject();
      expect(root.id).to.equal('/');
      expect(root.name).to.equal('/');
      expect(names(root.objectClass)).to.deep.equal(['container']);
    });

    it('getChildren("/") lists the receiver container', async () => {
      const children = await client.getObjectsApi().getChildren('/');
      expect(children.items.map((o) => o.id)).to.deep.equal([RECEIVER]);
      expect(children.count).to.equal(1);
    });

    it('getChildren(/x12-receiver) lists the folders, the live /inbox, the /transactions collection and /stats', async () => {
      const receiver = await client.getObjectsApi().getObject(RECEIVER);
      expect(names(receiver.objectClass)).to.deep.equal(['container']);

      const children = await client.getObjectsApi().getChildren(RECEIVER);
      const byId = new Map(children.items.map((o) => [o.id, o]));
      expect([...byId.keys()]).to.have.members(
        ['files', 'inbox', 'transactions', 'by-type', 'by-version', 'by-sender', 'by-source', 'stats', 'ops']
          .map((n) => `${RECEIVER}/${n}`));
      expect(children.count).to.equal(children.items.length);
      expect(names(byId.get(ALL_TX)?.objectClass)).to.deep.equal(['collection']);
      expect(byId.get(ALL_TX)?.collectionSchema).to.equal(ENVELOPE_SCHEMA);
      expect(names(byId.get(STATS)?.objectClass)).to.deep.equal(['document']);
      expect(byId.get(STATS)?.documentSchema).to.equal('schema:shared:x12.receiver-stats');
    });

    it('every emitted child id round-trips through getObject', async () => {
      for (const parent of ['/', RECEIVER, OPS, `${RECEIVER}/by-type`, `${RECEIVER}/by-source`]) {
        for (const child of await allChildren(client, parent)) {
          const again = await client.getObjectsApi().getObject(child.id);
          expect(again.id, `round-trip of ${child.id}`).to.equal(child.id);
        }
      }
    });

    it('/ops lists the drain and maintenance functions with their schemas', async () => {
      const fns = await allChildren(client, OPS);
      expect(fns.map((f) => f.name)).to.deep.equal(
        ['take', 'ack', 'release', 'replay', 'recast', 'purge', 'raw', 'validate', 'rescan', 'packs']);
      for (const f of fns) {
        expect(names(f.objectClass)).to.deep.equal(['function']);
        expect(f.inputSchema).to.equal(`schema:function:x12.ops.${f.name}:input`);
        expect(f.outputSchema).to.equal(`schema:function:x12.ops.${f.name}:output`);
      }
    });

    it('getObject rejects an unknown id with NoSuchObjectError', async () => {
      const e = await rejectionOf(client.getObjectsApi().getObject(`${RECEIVER}/no-such-folder`));
      expect(e).to.be.instanceOf(NoSuchObjectError);
    });

    it('getChildren enforces the paging bounds with IllegalArgumentError', async () => {
      expect(await rejectionOf(client.getObjectsApi().getChildren(RECEIVER, 1, 1001))).to.be.instanceOf(IllegalArgumentError);
      expect(await rejectionOf(client.getObjectsApi().getChildren(RECEIVER, 0, 10))).to.be.instanceOf(IllegalArgumentError);
    });
  });

  // ── ObjectsApi — files and by-type ────────────────────────

  describe('ObjectsApi — consumed files', () => {
    it('/files lists every dropped file as [container, document, binary]', async function () {
      const f = fed(this);
      const files = new Map((await allChildren(client, `${RECEIVER}/files`)).map((o) => [o.id, o]));
      for (const file of [f.f835, f.f837p, f.twoIsa]) {
        const node = files.get(file.nodeId);
        expect(node, `${file.nodeId} listed`).to.not.equal(undefined);
        expect(names(node?.objectClass)).to.deep.equal(['container', 'document', 'binary']);
        expect(node?.fileName).to.equal(file.name);
        expect(node?.size).to.equal(file.bytes.length);
        expect(node?.checksum).to.equal(file.sha256);
        expect(node?.tags).to.include.members(['status:consumed', `source:${f.source.name}`]);
      }
    });

    it('a file node holds one /transactions collection sized by its transaction sets', async function () {
      const f = fed(this);
      const node = await client.getObjectsApi().getObject(f.twoIsa.nodeId);
      expect(node.id).to.equal(f.twoIsa.nodeId);
      expect(node.mimeType).to.equal('application/EDI-X12');

      const children = await client.getObjectsApi().getChildren(f.twoIsa.nodeId);
      expect(children.items).to.have.length(1);
      expect(children.items[0].id).to.equal(`${f.twoIsa.nodeId}/transactions`);
      expect(names(children.items[0].objectClass)).to.deep.equal(['collection']);
      expect(children.items[0].collectionSchema).to.equal(ENVELOPE_SCHEMA);
      expect(children.items[0].collectionSize).to.equal(2);
    });

    it('/by-type/835 is a container of per-guide collections bound to the guide table schema', async function () {
      fed(this);
      const container = await client.getObjectsApi().getObject(T835);
      expect(names(container.objectClass)).to.deep.equal(['container']);

      const guides = await client.getObjectsApi().getChildren(T835);
      const coll = guides.items.find((o) => o.id === C835);
      expect(coll, `${C835} listed`).to.not.equal(undefined);
      expect(names(coll?.objectClass)).to.deep.equal(['collection']);
      expect(coll?.collectionSchema).to.equal('schema:table:x12.005010X221A1.835');
      expect(coll?.collectionSize).to.be.at.least(3);   // the 835 + both interchanges of the two-ISA file
    });

    it('/by-type/837P/005010X222A1 is the 837P guide collection', async function () {
      fed(this);
      const coll = await client.getObjectsApi().getObject(C837P);
      expect(names(coll.objectClass)).to.deep.equal(['collection']);
      expect(coll.collectionSchema).to.equal('schema:table:x12.005010X222A1.837P');
    });

    it('getCollectionElements on the /by-type/<TS> container is an unsupported operation', async function () {
      fed(this);
      expectUnsupported(await rejectionOf(client.getCollectionsApi().getCollectionElements(T835)));
    });
  });

  // ── CollectionsApi ────────────────────────────────────────

  describe('CollectionsApi', () => {
    it('the two-interchange file yields BOTH transaction sets, paged one per page', async function () {
      const f = fed(this);
      const coll = `${f.twoIsa.nodeId}/transactions`;
      const seen: string[] = [];
      for (const page of [1, 2]) {
        const res = await client.getCollectionsApi().getCollectionElements(coll, page, 1);
        expect(res.count).to.equal(2);
        expect(res.pageNumber).to.equal(page);
        expect(res.pageSize).to.equal(1);
        expect(res.items).to.have.length(1);
        seen.push(res.items[0].elementKey);
      }
      expect(seen).to.have.members(f.twoIsa.elementKeys);   // told apart by ISA13 alone

      const beyond = await client.getCollectionsApi().getCollectionElements(coll, 3, 1);
      expect(beyond.items).to.have.length(0);
      expect(beyond.count).to.equal(2);
    });

    it('getCollectionElements enforces the paging bounds with IllegalArgumentError', async () => {
      expect(await rejectionOf(client.getCollectionsApi().getCollectionElements(ALL_TX, 1, 1001)))
        .to.be.instanceOf(IllegalArgumentError);
      expect(await rejectionOf(client.getCollectionsApi().getCollectionElements(ALL_TX, 0, 10)))
        .to.be.instanceOf(IllegalArgumentError);
    });

    it('getCollectionElement by <fileId>:<ISA13>:<GS06>:<ST02> returns the envelope', async function () {
      const f = fed(this);
      const [key] = f.f835.elementKeys;
      const el = await client.getCollectionsApi().getCollectionElement(C835, key) as Record<string, any>;
      expect(el.elementKey).to.equal(key);
      expect(el.fileId).to.equal(f.f835.fileId);
      expect(el.fileName).to.equal(f.f835.name);
      expect(el.sourceName).to.equal(f.source.name);
      // control numbers stay strings (leading zeros kept)
      expect(el.isaControlNumber).to.equal('000000101');
      expect(el.gsControlNumber).to.equal('101');
      expect(el.stControlNumber).to.equal('0001');
      expect(el.gs08).to.equal('005010X221A1');
      expect(el.transactionType).to.equal('835');
      expect(el.senderId).to.equal('EXAMPLEPAYER');
      expect(el.receiverId).to.equal('EXAMPLEPROV');
      expect(el.interchangeDate).to.equal('2026-09-22T12:00:00Z');
      expect(el.status).to.equal('new');
      expect(el.parserErrorCount).to.equal(0);
    });

    it('the 835 body is typed: decimal money, ISO dates, integer counts', async function () {
      const f = fed(this);
      const el = await client.getCollectionsApi().getCollectionElement(C835, f.f835.elementKeys[0]) as Record<string, any>;
      const bpr = el.header.bpr;
      const claims = el.detail[0].loop2000[0].loop2100.map((c: Record<string, any>) => c.clp);
      const plb = el.footer.plb[0];

      expect(claims.map((c: Record<string, any>) => c.clp01)).to.deep.equal(['CLM0001', 'CLM0002']);
      // N2/R money arrives as JSON numbers (the wire keeps `300.00`; JSON.parse makes it 300)
      expect(claims.map((c: Record<string, any>) => [c.clp03, c.clp04, c.clp05])).to.deep.equal([[300, 220, 40], [250, 240, 0]]);
      expect(bpr.bpr02).to.be.a('number').and.equal(450);
      // fixture invariant: sum(CLP04) - PLB04 == BPR02
      expect(claims.reduce((sum: number, c: Record<string, any>) => sum + c.clp04, 0) - plb.plb04).to.equal(bpr.bpr02);
      expect(bpr.bpr16).to.equal('2026-09-22');
      expect(plb.plb02).to.equal('2026-12-31');
      expect(el.detail[0].loop2000[0].loop2100[0].dtm[0].dtm02).to.equal('2026-09-01');
      expect(el.se).to.deep.equal({ se01: 42, se02: '0001' });
      expect(claims[0].clp02).to.equal('1');   // a coded value stays a string
    });

    it('getCollectionElement rejects an unknown key with NoSuchObjectError', async () => {
      const e = await rejectionOf(client.getCollectionsApi().getCollectionElement(ALL_TX, '/nope@000000000000:1:2:3'));
      expect(e).to.be.instanceOf(NoSuchObjectError);
    });

    it('searchCollectionElements filters on envelope properties (RFC4515)', async function () {
      const f = fed(this);
      const res = await client.getCollectionsApi().searchCollectionElements(
        ALL_TX, 1, 10, `(&(fileId=${f.twoIsa.fileId})(isaControlNumber=000000201))`);
      expect(res.count).to.equal(1);
      expect(res.items.map((e) => e.elementKey)).to.deep.equal([f.twoIsa.elementKeys[1]]);

      const byType = await client.getCollectionsApi().searchCollectionElements(
        ALL_TX, 1, 10, `(&(transactionType=837P)(fileId=${f.f837p.fileId}))`);
      expect(byType.items.map((e) => e.elementKey)).to.deep.equal(f.f837p.elementKeys);
    });

    it('searchCollectionElements rejects a malformed filter with IllegalArgumentError', async () => {
      const e = await rejectionOf(client.getCollectionsApi().searchCollectionElements(ALL_TX, 1, 10, '(unclosed'));
      expect(e).to.be.instanceOf(IllegalArgumentError);
    });
  });

  // ── DocumentsApi ──────────────────────────────────────────

  describe('DocumentsApi', () => {
    it('getDocumentData on a file node returns its files row', async function () {
      const f = fed(this);
      const doc = await client.getDocumentsApi().getDocumentData(f.f835.nodeId) as Record<string, any>;
      expect(doc.fileId).to.equal(f.f835.fileId);
      expect(doc.fileName).to.equal(f.f835.name);
      expect(doc.filePath).to.equal(`${f.source.path}/${f.f835.name}`);
      expect(doc.currentPath).to.equal(`${f.source.path}/${f.f835.name}${f.source.consumedSuffix}`);
      expect(doc.status).to.equal('consumed');
      expect(doc.size).to.equal(f.f835.bytes.length);
      expect(doc.checksum).to.equal(f.f835.sha256);
      expect(doc.mimeType).to.equal('application/EDI-X12');
      expect(doc.transactionCount).to.equal(1);

      const two = await client.getDocumentsApi().getDocumentData(f.twoIsa.nodeId) as Record<string, any>;
      expect(two.isaCount).to.equal(2);
      expect(two.transactionCount).to.equal(2);
    });

    it('getDocumentData on /stats reports the poller, buffer and source', async function () {
      fed(this);
      const stats = await client.getDocumentsApi().getDocumentData(STATS) as Record<string, any>;
      expect(stats.up).to.equal(true);
      expect(stats.backpressure).to.equal(false);
      expect(stats.fileCount).to.be.at.least(3);
      expect(stats.doneFileCount).to.be.at.least(3);
      expect(stats.newCount).to.be.at.least(4);
      expect(stats.dbSizeBytes).to.be.greaterThan(0);
      const src = (stats.sources as Array<Record<string, any>>).find((s) => s.name === feed?.source.name);
      expect(src?.path).to.equal(feed?.source.path);
      expect(src?.writable).to.equal(true);
    });

    it('getDocumentData on a non-document is an unsupported operation', async () => {
      expectUnsupported(await rejectionOf(client.getDocumentsApi().getDocumentData(`${RECEIVER}/files`)));
    });
  });

  // ── BinaryApi ─────────────────────────────────────────────

  describe('BinaryApi', () => {
    it('downloadBinary returns the exact bytes that were dropped', async function () {
      const f = fed(this);
      for (const file of [f.f835, f.twoIsa]) {
        const bytes = await toBytes(await client.getBinaryApi().downloadBinary(file.nodeId));
        expect(bytes.equals(file.bytes), `${file.name} bytes`).to.equal(true);
      }
    });

    it('downloadBinary on a non-binary is an unsupported operation', async () => {
      expectUnsupported(await rejectionOf(client.getBinaryApi().downloadBinary(STATS)));
    });
  });

  // ── SchemasApi ────────────────────────────────────────────

  describe('SchemasApi', () => {
    it('getSchema resolves the 835 guide table schema', async () => {
      const schema = await client.getSchemasApi().getSchema('schema:table:x12.005010X221A1.835');
      expect(schema.id).to.equal('schema:table:x12.005010X221A1.835');
      expect(schema.properties.map((p) => p.name)).to.include.members(['st', 'header', 'detail', 'footer', 'se']);
    });

    it('getSchema resolves a code-list enum that covers the fixture value', async () => {
      const schema = await client.getSchemasApi().getSchema('schema:enum:x12.codes.1029');   // CLP02 claim status
      expect(schema.id).to.equal('schema:enum:x12.codes.1029');
      const enumType = schema.dataTypes.find((t) => t.isEnum);
      // `values` is the wire field listing the code set (not in the generated Type model)
      const values = (enumType as unknown as { values?: Array<{ value: string }> })?.values ?? [];
      expect(values.map((v) => v.value)).to.include('1');
    });

    it('every /ops function input and output schema resolves', async () => {
      for (const fn of await allChildren(client, OPS)) {
        for (const id of [fn.inputSchema, fn.outputSchema]) {
          expect((await client.getSchemasApi().getSchema(id)).id).to.equal(id);
        }
      }
    });

    it('getSchema rejects an unknown id with NoSuchObjectError', async () => {
      const e = await rejectionOf(client.getSchemasApi().getSchema('schema:enum:x12.codes.no-such-code'));
      expect(e).to.be.instanceOf(NoSuchObjectError);
    });
  });

  // ── FunctionsApi ──────────────────────────────────────────

  describe('FunctionsApi — input checks', () => {
    it('validateFunctionInput accepts a valid take input', async () => {
      const req: ValidateFunctionInputRequest = { input: body({ max: 5, leaseTtl: 'PT1M' }) };
      const res = await client.getFunctionsApi().validateFunctionInput(`${OPS}/take`, req);
      expect(res.valid).to.equal(true);
      expect(res.errors ?? []).to.have.length(0);
    });

    it('validateFunctionInput reports an unknown key as unknown_property', async () => {
      const req: ValidateFunctionInputRequest = { input: body({ olderthan: 'P30D' }) };
      const res = await client.getFunctionsApi().validateFunctionInput(`${OPS}/purge`, req);
      expect(res.valid).to.equal(false);
      expect((res.errors ?? []).map((e) => [e.path, e.code])).to.deep.equal([['olderthan', 'unknown_property']]);
    });

    it('invokeFunction(purge) with a misspelled key is rejected before anything runs', async () => {
      const e = await rejectionOf(client.getFunctionsApi().invokeFunction(`${OPS}/purge`, body({ olderthan: 'P30D' })));
      expect(e).to.be.instanceOf(IllegalArgumentError);
    });
  });

  describe('FunctionsApi — drain cycle (take → ack → purge)', () => {
    let leaseId: string | undefined;

    it('take leases the 837P transaction set', async function () {
      const f = fed(this);
      const out = await client.getFunctionsApi().invokeFunction(`${OPS}/take`,
        body({ filter: `(fileId=${f.f837p.fileId})`, max: 10 })) as Record<string, any>;
      expect(out.leaseId).to.be.a('string');
      expect(out.transactions.map((t: Record<string, any>) => [t.elementKey, t.status]))
        .to.deep.equal([[f.f837p.elementKeys[0], 'in_flight']]);
      leaseId = out.leaseId;

      const el = await client.getCollectionsApi().getCollectionElement(C837P, f.f837p.elementKeys[0]) as Record<string, any>;
      expect(el.status).to.equal('in_flight');
      expect(el.leaseId).to.equal(leaseId);
    });

    it('ack finalizes the lease', async function () {
      fed(this);
      expect(leaseId, 'lease from take').to.be.a('string');
      const out = await client.getFunctionsApi().invokeFunction(`${OPS}/ack`, body({ leaseId })) as Record<string, any>;
      expect(out.acked).to.equal(1);
    });

    it('ack of the same lease again is NoSuchObjectError', async function () {
      fed(this);
      expect(leaseId, 'lease from take').to.be.a('string');
      const e = await rejectionOf(client.getFunctionsApi().invokeFunction(`${OPS}/ack`, body({ leaseId })));
      expect(e).to.be.instanceOf(NoSuchObjectError);
    });

    it('purge evicts the acked row; the file row and its bytes stay', async function () {
      const f = fed(this);
      const out = await client.getFunctionsApi().invokeFunction(`${OPS}/purge`, body({})) as Record<string, any>;
      expect(out.purged).to.be.at.least(1);

      const e = await rejectionOf(client.getCollectionsApi().getCollectionElement(ALL_TX, f.f837p.elementKeys[0]));
      expect(e).to.be.instanceOf(NoSuchObjectError);
      const doc = await client.getDocumentsApi().getDocumentData(f.f837p.nodeId) as Record<string, any>;
      expect(doc.status).to.equal('consumed');
      const bytes = await toBytes(await client.getBinaryApi().downloadBinary(f.f837p.nodeId));
      expect(bytes.equals(f.f837p.bytes)).to.equal(true);
    });
  });

  // ── Unsupported operations ────────────────────────────────

  describe('Unsupported operations (receive-only producer)', () => {
    it('createChildObject', async () => {
      const req: CreateObjectRequest = { name: 'e2e-denied', objectClass: [] };
      expectUnsupported(await rejectionOf(client.getObjectsApi().createChildObject(RECEIVER, req)));
    });

    it('updateObject', async () => {
      const req: UpdateObjectRequest = { name: 'renamed' };
      expectUnsupported(await rejectionOf(client.getObjectsApi().updateObject(STATS, req)));
    });

    it('deleteObject', async () => {
      expectUnsupported(await rejectionOf(client.getObjectsApi().deleteObject(STATS)));
    });

    it('addCollectionElement', async () => {
      expectUnsupported(await rejectionOf(client.getCollectionsApi().addCollectionElement(ALL_TX, body({ note: 'x' }))));
    });

    it('updateCollectionElement', async () => {
      expectUnsupported(await rejectionOf(
        client.getCollectionsApi().updateCollectionElement(ALL_TX, '/nope@000000000000:1:2:3', body({ note: 'x' }))));
    });

    it('deleteCollectionElement', async () => {
      expectUnsupported(await rejectionOf(client.getCollectionsApi().deleteCollectionElement(ALL_TX, '/nope@000000000000:1:2:3')));
    });

    it('updateDocumentData', async () => {
      expectUnsupported(await rejectionOf(client.getDocumentsApi().updateDocumentData(STATS, body({ up: false }))));
    });

    it('sortBy on getChildren, scope=subtree on searchChildObjects, pageToken on getCollectionElements', async () => {
      expectUnsupported(await rejectionOf(client.getObjectsApi().getChildren(RECEIVER, 1, 10, ['name'])));
      expectUnsupported(await rejectionOf(
        client.getObjectsApi().searchChildObjects(RECEIVER, 1, 10, undefined, undefined, undefined,
          'subtree' as unknown as SearchScopeDef)));
      expectUnsupported(await rejectionOf(
        client.getCollectionsApi().getCollectionElements(ALL_TX, 1, 10, undefined, undefined, 'cursor')));
    });
  });

}, (data) => CoreError.deserialize(data));
