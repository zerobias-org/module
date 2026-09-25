/**
 * X12 receiver e2e — the real container through the real hub-sdk client (the docker wire
 * protocol under gradle testDocker).
 *
 * The receiver is fed the way production feeds it: committed synthetic fixtures are
 * `docker cp`'d into the watched inbox (an 835, an 837P, an 837I, and one file carrying two
 * interchanges that reuse GS06/ST02), and the suite waits — bounded by the container's own
 * stableForSec/pollIntervalSec — until the module has renamed them `.done`. Every assertion
 * after that goes through the DataProducer client, so every call exercises the Java router's
 * wire names. Assertions are keyed by this run's fileIds, never by totals it cannot own. The
 * receiver de-duplicates by content, so the suite needs a container that has not seen these
 * bytes before — gradle starts a fresh one (new anonymous volumes) for every run.
 *
 * Nothing here skips. describeModule runs nothing without a module secret in the slot, so a
 * precondition test fails first with the command that fixes it; no container to feed is a
 * failed before() hook, not skipped data tests.
 *
 * The drain cycle (take → ack → purge) runs last because it removes the 837P rows.
 */

import { expect } from 'chai';
import { CoreError, IllegalArgumentError, NoSuchObjectError } from '@zerobias-org/types-core-js';
import { describeModule } from '@zerobias-org/module-test-client';
import type { X12 } from '../../hub-sdk/generated/api/index.js';
import type {
  CreateObjectRequest,
  SearchScopeDef,
  SortDirectionDef,
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
  missingSecretReason,
  moduleContainer,
  names,
  planFeed,
  rejectionOf,
  toBytes,
  wireIsSupported,
  wireText,
  type Feed,
} from './helpers.js';

const T835 = `${RECEIVER}/by-type/835`;
const V835 = `${RECEIVER}/by-version/005010X221A1`;
const T837P = `${RECEIVER}/by-type/837P`;
const T837I = `${RECEIVER}/by-type/837I`;
const ALL_TX = `${RECEIVER}/transactions`;
const STATS = `${RECEIVER}/stats`;
const OPS = `${RECEIVER}/ops`;
const SOURCE_INBOX = `${RECEIVER}/inbox/inbox`;

/** Business collections and the schema each is bound to (generated from the mappings). */
const BUSINESS: Record<string, string> = {
  remittances: 'schema:business:x12.835.Remittance',
  claims: 'schema:business:x12.835.Claim',
  'service-lines': 'schema:business:x12.835.ServiceLine',
  'professional-claims': 'schema:business:x12.837P.Claim',
  'professional-service-lines': 'schema:business:x12.837P.ServiceLine',
  'institutional-claims': 'schema:business:x12.837I.Claim',
  'institutional-service-lines': 'schema:business:x12.837I.ServiceLine',
  payers: 'schema:business:x12.Payer',
  payees: 'schema:business:x12.Payee',
};

const ASC = 'asc' as unknown as SortDirectionDef;
const DESC = 'desc' as unknown as SortDirectionDef;

type Row = Record<string, any>;

const noSecret = missingSecretReason();
if (noSecret) {
  describe('X12 Receiver Module — e2e preconditions', () => {
    it('has a module secret for describeModule to run against', () => {
      throw new Error(noSecret);
    });
  });
}

describeModule<X12>('X12 Receiver Module', (client) => {
  let feed: Feed | undefined;

  /** The fed fixtures; before() either set them or failed the whole block. */
  function fed(): Feed {
    if (!feed) {
      throw new Error('fixtures were not fed (see the before all hook failure)');
    }
    return feed;
  }

  /** Rows of a collection, scoped to one file of this run. */
  async function rowsOf(collection: string, fileId: string, pageSize = 100): Promise<{ rows: Row[]; count: number }> {
    const res = await client.getCollectionsApi().searchCollectionElements(
      `${RECEIVER}/${collection}`, 1, pageSize, `(fileId=${fileId})`);
    return { rows: res.items as Row[], count: res.count ?? -1 };
  }

  before(async function () {
    const container = moduleContainer();
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
      expect(names(root.objectClass)).to.deep.equal(['container']);
    });

    it('getChildren("/") lists only the receiver container', async () => {
      const children = await client.getObjectsApi().getChildren('/');
      expect(children.items.map((o) => o.id)).to.deep.equal([RECEIVER]);
      expect(children.count).to.equal(1);
      expect(names(children.items[0].objectClass)).to.deep.equal(['container']);
    });

    it('getChildren(/x12-receiver) lists the structural folders, the business collections, /stats and /ops', async () => {
      const children = await allChildren(client, RECEIVER);
      const byId = new Map(children.map((o) => [o.id, o]));
      expect([...byId.keys()]).to.have.members([
        ...['files', 'inbox', 'transactions', 'by-type', 'by-version', 'by-sender', 'by-source', 'stats', 'ops'],
        ...Object.keys(BUSINESS),
      ].map((n) => `${RECEIVER}/${n}`));
      expect(names(byId.get(ALL_TX)?.objectClass)).to.deep.equal(['collection']);
      expect(byId.get(ALL_TX)?.collectionSchema).to.equal(ENVELOPE_SCHEMA);
      for (const [name, schema] of Object.entries(BUSINESS)) {
        const node = byId.get(`${RECEIVER}/${name}`);
        expect(names(node?.objectClass), name).to.deep.equal(['collection']);
        expect(node?.collectionSchema, name).to.equal(schema);
      }
      expect(names(byId.get(STATS)?.objectClass)).to.deep.equal(['document']);
    });

    it('every emitted child id round-trips through getObject', async () => {
      for (const parent of ['/', RECEIVER, OPS, `${RECEIVER}/by-type`, `${RECEIVER}/by-version`,
        `${RECEIVER}/by-source`, `${RECEIVER}/claims`]) {
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

    it('getChildren refuses the params it does not honour (sort, type, tags, pageToken) with 400', async () => {
      const objects = client.getObjectsApi();
      expectUnsupported(await rejectionOf(objects.getChildren(RECEIVER, 1, 10, ['name'])));
      expectUnsupported(await rejectionOf(objects.getChildren(RECEIVER, 1, 10, undefined, [ASC])));
      expectUnsupported(await rejectionOf(objects.getChildren(RECEIVER, 1, 10, undefined, undefined,
        ['collection' as unknown as never])));
      expectUnsupported(await rejectionOf(objects.getChildren(RECEIVER, 1, 10, undefined, undefined, undefined, ['x'])));
      expectUnsupported(await rejectionOf(objects.getChildren(RECEIVER, 1, 10, undefined, undefined, undefined,
        undefined, 'cursor')));
    });
  });

  // ── Consumed files and structural collections ─────────────

  describe('ObjectsApi — consumed files and structural collections', () => {
    it('/files lists every dropped file as a consumed [container, binary]', async () => {
      const f = fed();
      const files = new Map((await allChildren(client, `${RECEIVER}/files`)).map((o) => [o.id, o]));
      for (const file of [f.f835, f.f837p, f.f837i, f.twoIsa]) {
        const node = files.get(file.nodeId);
        expect(node, `${file.nodeId} listed`).to.not.equal(undefined);
        expect(names(node?.objectClass)).to.deep.equal(['container', 'binary']);
        expect(node?.fileId).to.equal(file.fileId);
        expect(node?.fileName).to.equal(file.name);
        expect(node?.filePath).to.equal(`${f.source.path}/${file.name}`);
        expect(node?.size).to.equal(file.bytes.length);
        expect(node?.checksum).to.equal(file.sha256);
        expect(node?.mimeType).to.equal('application/EDI-X12');
        expect(node?.tags).to.include.members(['status:consumed', `source:${f.source.name}`]);
      }
    });

    it('a file node holds one /transactions collection sized by its transaction sets', async () => {
      const f = fed();
      const children = await client.getObjectsApi().getChildren(f.twoIsa.nodeId);
      expect(children.items).to.have.length(1);
      expect(children.items[0].id).to.equal(`${f.twoIsa.nodeId}/transactions`);
      expect(names(children.items[0].objectClass)).to.deep.equal(['collection']);
      expect(children.items[0].collectionSchema).to.equal(ENVELOPE_SCHEMA);
      expect(children.items[0].collectionSize).to.equal(2);
    });

    it('/by-type/<TS> are collections bound to each guide table schema', async () => {
      fed();
      const expected: Array<[string, string]> = [
        [T835, 'schema:table:x12.005010X221A1.835'],
        [T837P, 'schema:table:x12.005010X222A1.837P'],
        [T837I, 'schema:table:x12.005010X223A2.837I'],
      ];
      for (const [id, schema] of expected) {
        const coll = await client.getObjectsApi().getObject(id);
        expect(names(coll.objectClass), id).to.deep.equal(['collection']);
        expect(coll.collectionSchema, id).to.equal(schema);
      }
      expect((await client.getObjectsApi().getObject(T835)).collectionSize).to.be.at.least(3);
    });

    it('/by-version/<GS08> is an envelope collection holding this run\'s 835s', async () => {
      const f = fed();
      const coll = await client.getObjectsApi().getObject(V835);
      expect(names(coll.objectClass)).to.deep.equal(['collection']);
      expect(coll.collectionSchema).to.equal(ENVELOPE_SCHEMA);
      const res = await client.getCollectionsApi().searchCollectionElements(V835, 1, 10, `(fileId=${f.twoIsa.fileId})`);
      expect(res.items.map((e) => e.elementKey)).to.have.members(f.twoIsa.elementKeys);
    });

    it('/by-sender and /by-source are populated from the envelopes', async () => {
      const f = fed();
      const senders = (await allChildren(client, `${RECEIVER}/by-sender`)).map((o) => o.id);
      expect(senders).to.include.members([`${RECEIVER}/by-sender/EXAMPLEPAYER`, `${RECEIVER}/by-sender/EXAMPLEPROV`]);
      const sources = (await allChildren(client, `${RECEIVER}/by-source`)).map((o) => o.id);
      expect(sources).to.include(`${RECEIVER}/by-source/${f.source.name}`);
    });
  });

  // ── CollectionsApi — structural ───────────────────────────

  describe('CollectionsApi — structural collections', () => {
    it('the two-interchange file yields BOTH transaction sets, paged one per page', async () => {
      const f = fed();
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

    it('getCollectionElement by <fileId>:<ISA13>:<GS06>:<ST02> returns the envelope', async () => {
      const f = fed();
      const [key] = f.f835.elementKeys;
      const el = await client.getCollectionsApi().getCollectionElement(T835, key) as Row;
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

    it('the 835 body is typed: decimal money, ISO dates, integer counts', async () => {
      const f = fed();
      const el = await client.getCollectionsApi().getCollectionElement(T835, f.f835.elementKeys[0]) as Row;
      const bpr = el.header.bpr;
      const claims = el.detail[0].loop2000[0].loop2100.map((c: Row) => c.clp);
      const plb = el.footer.plb[0];

      expect(claims.map((c: Row) => c.clp01)).to.deep.equal(['CLM0001', 'CLM0002']);
      // N2/R money: the wire keeps `300.00`; through the client it is the JS number 300
      expect(claims.map((c: Row) => [c.clp03, c.clp04, c.clp05])).to.deep.equal([[300, 220, 40], [250, 240, 0]]);
      expect(bpr.bpr02).to.be.a('number').and.equal(450);
      // fixture invariant: sum(CLP04) - PLB04 == BPR02
      expect(claims.reduce((sum: number, c: Row) => sum + c.clp04, 0) - plb.plb04).to.equal(bpr.bpr02);
      expect(bpr.bpr16).to.equal('2026-09-22');
      expect(plb.plb02).to.equal('2026-12-31');
      expect(el.se).to.deep.equal({ se01: 42, se02: '0001' });
      expect(claims[0].clp02).to.equal('1');   // a coded value stays a string
    });

    it('getCollectionElement rejects an unknown key with NoSuchObjectError', async () => {
      const e = await rejectionOf(client.getCollectionsApi().getCollectionElement(ALL_TX, '/nope@000000000000:1:2:3'));
      expect(e).to.be.instanceOf(NoSuchObjectError);
    });

    it('searchCollectionElements filters on envelope properties (RFC4515)', async () => {
      const f = fed();
      const res = await client.getCollectionsApi().searchCollectionElements(
        ALL_TX, 1, 10, `(&(fileId=${f.twoIsa.fileId})(isaControlNumber=000000201))`);
      expect(res.count).to.equal(1);
      expect(res.items.map((e) => e.elementKey)).to.deep.equal([f.twoIsa.elementKeys[1]]);

      const byType = await client.getCollectionsApi().searchCollectionElements(
        ALL_TX, 1, 10, `(&(transactionType=837I)(fileId=${f.f837i.fileId}))`);
      expect(byType.items.map((e) => e.elementKey)).to.deep.equal(f.f837i.elementKeys);
    });

    it('sortBy/sortDir order a structural collection (SQL ORDER BY), both directions', async () => {
      const f = fed();
      const filter = `(fileId=${f.twoIsa.fileId})`;
      const desc = await client.getCollectionsApi().searchCollectionElements(
        ALL_TX, 1, 10, filter, ['isaControlNumber'], [DESC]);
      expect(desc.items.map((e) => e.isaControlNumber)).to.deep.equal(['000000201', '000000101']);
      const asc = await client.getCollectionsApi().searchCollectionElements(
        ALL_TX, 1, 10, filter, ['isaControlNumber'], [ASC]);
      expect(asc.items.map((e) => e.isaControlNumber)).to.deep.equal(['000000101', '000000201']);
    });

    it('searchCollectionElements rejects a malformed filter with IllegalArgumentError', async () => {
      const e = await rejectionOf(client.getCollectionsApi().searchCollectionElements(ALL_TX, 1, 10, '(unclosed'));
      expect(e).to.be.instanceOf(IllegalArgumentError);
    });

    it('getCollectionElements enforces the paging bounds with IllegalArgumentError', async () => {
      expect(await rejectionOf(client.getCollectionsApi().getCollectionElements(ALL_TX, 1, 1001)))
        .to.be.instanceOf(IllegalArgumentError);
      expect(await rejectionOf(client.getCollectionsApi().getCollectionElements(ALL_TX, 0, 10)))
        .to.be.instanceOf(IllegalArgumentError);
    });

    it('pageToken and properties are refused with 400, not ignored', async () => {
      const colls = client.getCollectionsApi();
      expectUnsupported(await rejectionOf(colls.getCollectionElements(ALL_TX, 1, 10, undefined, undefined, 'cursor')));
      expectUnsupported(await rejectionOf(
        colls.getCollectionElements(ALL_TX, 1, 10, undefined, undefined, undefined, ['elementKey'])));
      expectUnsupported(await rejectionOf(
        colls.searchCollectionElements(`${RECEIVER}/claims`, 1, 10, undefined, undefined, undefined, 'cursor')));
    });
  });

  // ── CollectionsApi — business entities ────────────────────

  describe('CollectionsApi — business collections', () => {
    it('/remittances has one row per 835 transaction set, amounts as numbers', async () => {
      const f = fed();
      const { rows, count } = await rowsOf('remittances', f.f835.fileId);
      expect(count).to.equal(1);
      expect(rows[0].elementKey).to.equal(f.f835.elementKeys[0]);
      expect(rows[0].paymentAmount).to.be.a('number').and.equal(450);
      expect(rows[0].checkOrEftNumber).to.equal('EFT000000101');
      expect(rows[0].payerName).to.equal('EXAMPLE HEALTH PLAN');
      expect(rows[0].payeeNpi).to.equal('1234567893');
      expect(rows[0].effectiveDate).to.equal('2026-09-22');
    });

    it('/claims carries the 835 claims with their business identity', async () => {
      const f = fed();
      const { rows, count } = await rowsOf('claims', f.f835.fileId);
      expect(count).to.equal(2);
      const byId = new Map(rows.map((r) => [r.claimId, r]));
      const c1 = byId.get('CLM0001');
      expect([c1?.chargedAmount, c1?.paidAmount, c1?.patientResponsibility, c1?.allowedAmount])
        .to.deep.equal([300, 220, 40, 260]);
      expect(c1?.patientLastName).to.equal('DOE');
      expect(c1?.payerClaimControlNumber).to.equal('EHP2026000001');
      expect(c1?.statementFromDate).to.equal('2026-09-01');
      expect(byId.get('CLM0002')?.paidAmount).to.equal(240);
    });

    it('/service-lines has its own grain: 3 lines, sum(paid) per claim == CLP04', async () => {
      const f = fed();
      const { rows, count } = await rowsOf('service-lines', f.f835.fileId);
      expect(count).to.equal(3);
      expect(rows.map((r) => r.procedureCode)).to.have.members(['99213', '36415', '99214']);
      const paid = (claim: string): number => rows.filter((r) => r.claimId === claim)
        .reduce((s, r) => s + r.paidAmount, 0);
      expect([paid('CLM0001'), paid('CLM0002')]).to.deep.equal([220, 240]);
    });

    it('/professional-claims and /professional-service-lines carry the 837P', async () => {
      const f = fed();
      const claims = await rowsOf('professional-claims', f.f837p.fileId);
      expect(claims.count).to.equal(1);
      expect(claims.rows[0]).to.include({
        claimId: 'CLM0001', chargedAmount: 300, placeOfServiceCode: '11', principalDiagnosisCode: 'J069',
        subscriberLastName: 'DOE', payerName: 'EXAMPLE HEALTH PLAN', payerId: 'EHPID00001',
      });
      const lines = await rowsOf('professional-service-lines', f.f837p.fileId);
      expect(lines.count).to.equal(2);
      expect(lines.rows.map((r) => [r.lineNumber, r.procedureCode, r.chargedAmount, r.lineItemControlNumber]))
        .to.have.deep.members([[1, '99213', 200, 'LINE0001'], [2, '36415', 100, 'LINE0002']]);
    });

    it('/institutional-claims and /institutional-service-lines carry the 837I', async () => {
      const f = fed();
      const claims = await rowsOf('institutional-claims', f.f837i.fileId);
      expect(claims.count).to.equal(1);
      expect(claims.rows[0]).to.include({
        claimId: 'CLM0002', chargedAmount: 2500, facilityTypeCode: '13', principalDiagnosisCode: 'K3580',
        billingProviderName: 'EXAMPLE COMMUNITY HOSPITAL', subscriberLastName: 'ROE',
      });
      const lines = await rowsOf('institutional-service-lines', f.f837i.fileId);
      expect(lines.count).to.equal(3);
      expect(lines.rows.map((r) => r.revenueCode)).to.have.members(['0450', '0300', '0320']);
      expect(lines.rows.reduce((s, r) => s + r.chargedAmount, 0)).to.equal(2500);   // sum(SV203) == CLM02
    });

    it('/payers and /payees are dimension-grain: one payer across 835/837P/837I, one payee', async () => {
      fed();
      const payers = (await client.getCollectionsApi().searchCollectionElements(
        `${RECEIVER}/payers`, 1, 100, '(payerId=EHPID00001)')).items as Row[];
      expect(payers).to.have.length(1);
      expect(payers[0].payerKey).to.equal('id:EHPID00001');
      expect(payers[0].payerName).to.equal('EXAMPLE HEALTH PLAN');
      expect(String(payers[0].transactionTypes).split(',')).to.include.members(['835', '837P', '837I']);
      expect(payers[0].transactionCount).to.be.at.least(5);

      const payees = (await client.getCollectionsApi().searchCollectionElements(
        `${RECEIVER}/payees`, 1, 100, '(payeeNpi=1234567893)')).items as Row[];
      expect(payees).to.have.length(1);
      expect(payees[0].payeeName).to.equal('EXAMPLE MEDICAL GROUP');
    });

    it('filters by a dimension with a substring match: (payerName=EXAMPLE*)', async () => {
      const f = fed();
      const hit = await client.getCollectionsApi().searchCollectionElements(`${RECEIVER}/claims`, 1, 10,
        `(&(fileId=${f.f835.fileId})(payerName=EXAMPLE*))`);
      expect(hit.count).to.equal(2);
      const miss = await client.getCollectionsApi().searchCollectionElements(`${RECEIVER}/claims`, 1, 10,
        `(&(fileId=${f.f835.fileId})(payerName=NOBODY*))`);
      expect(miss.count).to.equal(0);
      const typed = await client.getCollectionsApi().searchCollectionElements(`${RECEIVER}/claims`, 1, 10,
        `(&(fileId=${f.f835.fileId})(paidAmount>=230))`);
      expect(typed.items.map((r) => r.claimId)).to.deep.equal(['CLM0002']);
    });

    it('sortBy/sortDir order business rows with a typed comparator, both directions', async () => {
      const f = fed();
      const coll = `${RECEIVER}/service-lines`;
      const filter = `(fileId=${f.f835.fileId})`;
      const desc = await client.getCollectionsApi().searchCollectionElements(coll, 1, 10, filter, ['paidAmount'], [DESC]);
      expect(desc.items.map((r) => r.paidAmount)).to.deep.equal([240, 160, 60]);
      const asc = await client.getCollectionsApi().searchCollectionElements(coll, 1, 10, filter, ['paidAmount'], [ASC]);
      expect(asc.items.map((r) => r.paidAmount)).to.deep.equal([60, 160, 240]);
    });

    it('pages business rows: pageSize=2 over 3 service lines', async () => {
      const f = fed();
      const coll = `${RECEIVER}/service-lines`;
      const filter = `(fileId=${f.f835.fileId})`;
      const p1 = await client.getCollectionsApi().searchCollectionElements(coll, 1, 2, filter, ['paidAmount'], [ASC]);
      const p2 = await client.getCollectionsApi().searchCollectionElements(coll, 2, 2, filter, ['paidAmount'], [ASC]);
      expect([p1.count, p2.count]).to.deep.equal([3, 3]);
      expect(p1.items.map((r) => r.paidAmount)).to.deep.equal([60, 160]);
      expect(p2.items.map((r) => r.paidAmount)).to.deep.equal([240]);
    });

    it('rejects an unknown filter attribute, sort attribute or direction with IllegalArgumentError', async () => {
      const colls = client.getCollectionsApi();
      const claims = `${RECEIVER}/claims`;
      expect(await rejectionOf(colls.searchCollectionElements(claims, 1, 10, '(nope=1)'))).to.be.instanceOf(IllegalArgumentError);
      expect(await rejectionOf(colls.getCollectionElements(claims, 1, 10, ['nope']))).to.be.instanceOf(IllegalArgumentError);
      expect(await rejectionOf(colls.getCollectionElements(claims, 1, 10, ['paidAmount'],
        ['sideways' as unknown as SortDirectionDef]))).to.be.instanceOf(IllegalArgumentError);
    });

    it('/claims is segmentable by payer: the segment emerges from the data', async () => {
      const f = fed();
      const segments = (await allChildren(client, `${RECEIVER}/claims`)).map((o) => o.name);
      expect(segments).to.include.members(['by-file', 'by-payerName']);
      const payerNodes = await allChildren(client, `${RECEIVER}/claims/by-payerName`);
      const plan = payerNodes.find((o) => o.name === 'EXAMPLE HEALTH PLAN');
      expect(plan, 'EXAMPLE HEALTH PLAN segment').to.not.equal(undefined);
      const res = await client.getCollectionsApi().searchCollectionElements(plan?.id, 1, 10, `(fileId=${f.f835.fileId})`);
      expect(res.count).to.equal(2);
    });

    it('decimal scale: the wire keeps 300.00, the hub-sdk client yields the JS number 300', async () => {
      const f = fed();
      const filter = `(&(fileId=${f.f835.fileId})(claimId=CLM0001))`;
      const viaClient = (await client.getCollectionsApi().searchCollectionElements(
        `${RECEIVER}/claims`, 1, 10, filter)).items[0] as Row;
      // JSON.parse (axios) cannot keep a trailing zero: the value is right, the scale is gone
      expect(viaClient.chargedAmount).to.be.a('number').and.equal(300);
      expect(String(viaClient.chargedAmount)).to.equal('300');

      const text = await wireText('CollectionsApi.searchCollectionElements',
        { objectId: `${RECEIVER}/claims`, filter, pageNumber: 1, pageSize: 10 });
      expect(text).to.contain('"chargedAmount":300.00');
      expect(text).to.contain('"paidAmount":220.00');
      expect(text).to.contain('"patientResponsibility":40.00');
    });
  });

  // ── BinaryApi / ops/raw ───────────────────────────────────

  describe('BinaryApi and raw X12', () => {
    it('downloadBinary returns the exact bytes that were dropped', async () => {
      const f = fed();
      for (const file of [f.f835, f.f837i, f.twoIsa]) {
        const bytes = await toBytes(await client.getBinaryApi().downloadBinary(file.nodeId));
        expect(bytes.equals(file.bytes), `${file.name} bytes`).to.equal(true);
      }
    });

    it('ops/raw returns the stored X12 of one transaction set, scale intact', async () => {
      const f = fed();
      const out = await client.getFunctionsApi().invokeFunction(`${OPS}/raw`,
        body({ elementKey: f.f835.elementKeys[0] })) as Row;
      expect(out.elementKey).to.equal(f.f835.elementKeys[0]);
      expect(out.transactionType).to.equal('835');
      expect(out.raw).to.contain('ST*835*0001~');
      expect(out.raw).to.contain('BPR*I*450.00*C*ACH');
    });

    it('downloadBinary on a non-binary is an unsupported operation', async () => {
      expectUnsupported(await rejectionOf(client.getBinaryApi().downloadBinary(STATS)));
    });
  });

  // ── DocumentsApi ──────────────────────────────────────────

  describe('DocumentsApi', () => {
    it('getDocumentData on /stats reports the poller, buffer and source', async () => {
      const f = fed();
      const stats = await client.getDocumentsApi().getDocumentData(STATS) as Row;
      expect(stats.up).to.equal(true);
      expect(stats.backpressure).to.equal(false);
      expect(stats.fileCount).to.be.at.least(4);
      expect(stats.doneFileCount).to.be.at.least(4);
      expect(stats.dbSizeBytes).to.be.greaterThan(0);
      const src = (stats.sources as Row[]).find((s) => s.name === f.source.name);
      expect(src?.path).to.equal(f.source.path);
      expect(src?.writable).to.equal(true);
    });

    it('getDocumentData on a file node (not a document) is an unsupported operation', async () => {
      expectUnsupported(await rejectionOf(client.getDocumentsApi().getDocumentData(fed().f835.nodeId)));
    });
  });

  // ── SchemasApi ────────────────────────────────────────────

  describe('SchemasApi', () => {
    it('getSchema resolves the 835 guide table schema', async () => {
      const schema = await client.getSchemasApi().getSchema('schema:table:x12.005010X221A1.835');
      expect(schema.id).to.equal('schema:table:x12.005010X221A1.835');
      expect(schema.properties.map((p) => p.name)).to.include.members(['st', 'header', 'detail', 'footer', 'se']);
    });

    it('getSchema resolves every business collectionSchema, money typed decimal', async () => {
      for (const id of Object.values(BUSINESS)) {
        expect((await client.getSchemasApi().getSchema(id)).id).to.equal(id);
      }
      const claim = await client.getSchemasApi().getSchema(BUSINESS.claims);
      const types = new Map(claim.properties.map((p) => [p.name, p.dataType]));
      expect(types.get('paidAmount')).to.equal('decimal');
      expect(types.get('claimId')).to.equal('string');
      expect(types.get('statementFromDate')).to.equal('date');
      expect([...types.keys()]).to.include.members(['payerName', 'fileId', 'elementKey']);
    });

    it('getSchema resolves a code-list enum that covers the fixture value', async () => {
      const schema = await client.getSchemasApi().getSchema('schema:enum:x12.codes.1029');   // CLP02 claim status
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

  // ── File management gate ──────────────────────────────────

  describe('File management (config.allowFileManagement)', () => {
    it('/inbox browses the live volume whatever the flag', async () => {
      const f = fed();
      const listed = (await allChildren(client, SOURCE_INBOX)).map((o) => o.name);
      expect(listed).to.include.members(
        [f.f835, f.f837p, f.f837i, f.twoIsa].map((file) => `${file.name}${f.source.consumedSuffix}`));
    });

    it('isSupported answers from the flag the container was started with', async () => {
      const on = fed().source.allowFileManagement;
      for (const op of ['uploadBinaryContent', 'createChildObject', 'deleteObject']) {
        expect(await wireIsSupported(op), op).to.equal(on);
      }
      expect(await wireIsSupported('getChildren')).to.equal(true);
    });

    it('mkdir / delete on /inbox follow the flag (refused as unsupported when off)', async () => {
      const f = fed();
      const objects = client.getObjectsApi();
      const req: CreateObjectRequest = { name: `e2e-${Date.now().toString(36)}`, objectClass: ['container' as unknown as never] };
      if (!f.source.allowFileManagement) {
        expectUnsupported(await rejectionOf(objects.createChildObject(SOURCE_INBOX, req)));
        expectUnsupported(await rejectionOf(objects.deleteObject(`${SOURCE_INBOX}/${f.f835.name}${f.source.consumedSuffix}`)));
        // the .done file is still there: the refusal happened before anything was touched
        const listed = (await allChildren(client, SOURCE_INBOX)).map((o) => o.name);
        expect(listed).to.include(`${f.f835.name}${f.source.consumedSuffix}`);
        return;
      }
      const made = await objects.createChildObject(SOURCE_INBOX, req);
      expect(made.id).to.equal(`${SOURCE_INBOX}/${req.name}`);
      expect(names(made.objectClass)).to.deep.equal(['container']);
      await objects.deleteObject(made.id);
      expect(await rejectionOf(objects.getObject(made.id))).to.be.instanceOf(NoSuchObjectError);
    });

    it('uploadBinaryContent through the hub-sdk client is rejected with 400', async () => {
      // Off: the receiver is receive-only. On: the client's (objectId, body) cannot carry the
      // fileName the receiver requires (raw bytes + ?fileName=, or fileName + contentBase64).
      const e = await rejectionOf(client.getBinaryApi().uploadBinaryContent(SOURCE_INBOX, fed().f835.bytes as never));
      expect(e).to.be.instanceOf(CoreError);
      expect((e as CoreError<any>).statusCode).to.equal(400);
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

    it('invokeFunction with an unknown key, or without a required one, is a 400 before anything runs', async () => {
      const fns = client.getFunctionsApi();
      expect(await rejectionOf(fns.invokeFunction(`${OPS}/purge`, body({ olderthan: 'P30D' })))).to.be.instanceOf(IllegalArgumentError);
      expect(await rejectionOf(fns.invokeFunction(`${OPS}/take`, body({ maxx: 1 })))).to.be.instanceOf(IllegalArgumentError);
      expect(await rejectionOf(fns.invokeFunction(`${OPS}/ack`, body({})))).to.be.instanceOf(IllegalArgumentError);
    });

    it('ack of an unknown lease is idempotent: acked 0, not an error', async () => {
      const out = await client.getFunctionsApi().invokeFunction(`${OPS}/ack`, body({ leaseId: 'no-such-lease' })) as Row;
      expect(out.acked).to.equal(0);
    });
  });

  // ── Unsupported operations ────────────────────────────────

  describe('Unsupported operations (receive-only data)', () => {
    it('updateObject', async () => {
      const req: UpdateObjectRequest = { name: 'renamed' };
      expectUnsupported(await rejectionOf(client.getObjectsApi().updateObject(STATS, req)));
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

    it('searchChildObjects with scope=subtree', async () => {
      expectUnsupported(await rejectionOf(
        client.getObjectsApi().searchChildObjects(RECEIVER, 1, 10, undefined, undefined, undefined,
          'subtree' as unknown as SearchScopeDef)));
    });
  });

  // ── Drain cycle — last: purge removes the 837P rows ───────

  describe('FunctionsApi — drain cycle (take → ack → purge)', () => {
    let leaseId: string | undefined;

    it('take leases the 837P transaction set', async () => {
      const f = fed();
      const out = await client.getFunctionsApi().invokeFunction(`${OPS}/take`,
        body({ filter: `(fileId=${f.f837p.fileId})`, max: 10 })) as Row;
      expect(out.leaseId).to.be.a('string');
      expect(out.transactions.map((t: Row) => [t.elementKey, t.status]))
        .to.deep.equal([[f.f837p.elementKeys[0], 'in_flight']]);
      leaseId = out.leaseId;

      const el = await client.getCollectionsApi().getCollectionElement(T837P, f.f837p.elementKeys[0]) as Row;
      expect(el.status).to.equal('in_flight');
      expect(el.leaseId).to.equal(leaseId);
    });

    it('ack finalizes the lease', async () => {
      expect(leaseId, 'lease from take').to.be.a('string');
      const out = await client.getFunctionsApi().invokeFunction(`${OPS}/ack`, body({ leaseId })) as Row;
      expect(out.acked).to.equal(1);
      const el = await client.getCollectionsApi().getCollectionElement(T837P, fed().f837p.elementKeys[0]) as Row;
      expect(el.status).to.equal('acked');
    });

    it('ack of the same lease again is idempotent (acked 0)', async () => {
      expect(leaseId, 'lease from take').to.be.a('string');
      const out = await client.getFunctionsApi().invokeFunction(`${OPS}/ack`, body({ leaseId })) as Row;
      expect(out.acked).to.equal(0);
    });

    it('purge evicts the acked row and its business rows; the file row and its bytes stay', async () => {
      const f = fed();
      const out = await client.getFunctionsApi().invokeFunction(`${OPS}/purge`, body({})) as Row;
      expect(out.purged).to.be.at.least(1);

      const e = await rejectionOf(client.getCollectionsApi().getCollectionElement(ALL_TX, f.f837p.elementKeys[0]));
      expect(e).to.be.instanceOf(NoSuchObjectError);
      expect((await rowsOf('professional-claims', f.f837p.fileId)).count).to.equal(0);
      expect((await rowsOf('claims', f.f835.fileId)).count).to.equal(2);   // un-acked rows are never purged

      const node = await client.getObjectsApi().getObject(f.f837p.nodeId);
      expect(node.tags).to.include('status:consumed');
      const bytes = await toBytes(await client.getBinaryApi().downloadBinary(f.f837p.nodeId));
      expect(bytes.equals(f.f837p.bytes)).to.equal(true);
    });
  });

}, (data) => CoreError.deserialize(data));
