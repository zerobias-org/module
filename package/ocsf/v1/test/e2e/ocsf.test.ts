/**
 * OCSF Receiver e2e (gradle testDocker): the real container, fed through its inbox volume with
 * the committed fixtures (`docker cp`, as a producer would), read back through the hub-sdk
 * client generated from api.yml — the OCSF operations a collector calls, and the DataProducer
 * files side. The container runs the shipped runtimeConfig.yml config: uploads are off.
 */

import { expect } from 'chai';
import { CoreError } from '@zerobias-org/types-core-js';
import {
  awaitConsumed,
  describeReceiver,
  dropFixtures,
  expectUnsupported,
  Feed,
  moduleContainer,
  planFeed,
  RECEIVER,
  rejectionOf,
  toBytes,
  wireIsSupported,
  wireText,
} from './helpers.js';

const AUTH_TIMES = [1760000000000, 1760000060000, 1760000120000];

describeReceiver('OCSF Receiver Module', (client) => {
  let feed: Feed;

  before(async function () {
    feed = planFeed(moduleContainer(), `e2e-${Date.now()}`);
    dropFixtures(feed);
    await awaitConsumed(client, feed);
  });

  describe('OCSF interface', () => {
    it('listClasses names the classes that hold events', async () => {
      const classes = await client.getClassesApi().listClasses();
      const byUid = new Map(classes.map((c) => [c.uid, c]));
      expect(byUid.get(3002)).to.include({ name: 'authentication', category: 'iam', categoryUid: 3 });
      expect(byUid.get(1001)).to.include({ name: 'file_activity', category: 'system' });
    });

    it('list<Class> pages a class newest first', async () => {
      const page = await client.getIamApi().listAuthentication(undefined, undefined, 1, 2);
      expect(page.count).to.equal(3);
      expect(page.items.map((e) => e.time)).to.deep.equal([AUTH_TIMES[2], AUTH_TIMES[1]]);
      const next = await client.getIamApi().listAuthentication(undefined, undefined, 2, 2);
      expect(next.items.map((e) => e.time)).to.deep.equal([AUTH_TIMES[0]]);
    });

    it('startTime is inclusive and endTime exclusive', async () => {
      const page = await client.getIamApi().listAuthentication(AUTH_TIMES[1], AUTH_TIMES[2]);
      expect(page.count).to.equal(1);
      // Nested objects are typed `object` (models link by x-ocsf-object, not $ref): read them as records.
      expect((page.items[0].metadata as Record<string, unknown>).uid).to.equal('auth-2');
    });

    it('events come back as written, nested objects included', async () => {
      const e = await client.getIamApi().getAuthentication('auth-1');
      expect(e.user).to.deep.include({ name: 'alice', uid: 'u-alice' });
      const line = feed.auth.bytes.toString('utf8').split('\n')[0];
      expect(await wireText('IamApi.getAuthentication', { uid: 'auth-1' })).to.equal(line);
    });

    it('metadata.version is kept: a 1.1.0 JSON-array file serves as written', async () => {
      const page = await client.getSystemApi().listFileActivity();
      expect(page.count).to.equal(2);
      expect(page.items.map((e) => (e.metadata as Record<string, unknown>).version)).to.deep.equal(['1.1.0', '1.1.0']);
    });

    it('a class with no events is an empty page; an unknown uid is NoSuchObjectError', async () => {
      expect((await client.getSystemApi().listProcessActivity()).count).to.equal(0);
      const e = await rejectionOf(client.getIamApi().getAuthentication('no-such-uid'));
      expect(e).to.be.instanceOf(CoreError);
      expect((e as CoreError<any>).statusCode).to.equal(404);
    });

    it('a uid of another class is not found under this one', async () => {
      const e = await rejectionOf(client.getSystemApi().getFileActivity('auth-1'));
      expect((e as CoreError<any>).statusCode).to.equal(404);
    });

    it('paging bounds and an empty window are IllegalArgumentError', async () => {
      for (const p of [client.getIamApi().listAuthentication(undefined, undefined, 1, 1001),
        client.getIamApi().listAuthentication(AUTH_TIMES[0], AUTH_TIMES[0])]) {
        const e = await rejectionOf(p);
        expect((e as CoreError<any>).statusCode).to.equal(400);
      }
    });
  });

  describe('DataProducer (files only)', () => {
    it('the receiver container holds /inbox, /files and /stats', async () => {
      const kids = await client.getObjectsApi().getChildren(RECEIVER, 1, 100);
      expect(kids.items.map((o) => o.id)).to.deep.equal([`${RECEIVER}/inbox`, `${RECEIVER}/files`, `${RECEIVER}/stats`]);
    });

    it('a consumed file is a [document, binary] with its counts', async () => {
      const node = await client.getObjectsApi().getObject(feed.auth.nodeId);
      expect(node.objectClass?.map(String)).to.deep.equal(['document', 'binary']);
      const doc = await client.getDocumentsApi().getDocumentData(feed.auth.nodeId) as Record<string, any>;
      expect(doc).to.include({ status: 'consumed', eventCount: 3, rejectedCount: 0, fileId: feed.auth.fileId });
    });

    it('downloadBinary returns the exact bytes that were dropped', async () => {
      expect(await toBytes(await client.getBinaryApi().downloadBinary(feed.auth.nodeId))).to.deep.equal(feed.auth.bytes);
    });

    it('/stats reports the events held', async () => {
      const stats = await client.getDocumentsApi().getDocumentData(`${RECEIVER}/stats`) as Record<string, any>;
      expect(stats.eventCount).to.be.at.least(5);
      expect(stats.up).to.equal(true);
    });

    it('uploads are refused: the shipped config is receive-only', async () => {
      expectUnsupported(await rejectionOf(client.getObjectsApi().deleteObject(`${RECEIVER}/inbox/inbox/x.ndjson`)));
    });
  });

  describe('isSupported (raw wire)', () => {
    it('answers for what is routed, under its own API class only', async () => {
      expect(await wireIsSupported('IamApi.listAuthentication')).to.equal(true);
      expect(await wireIsSupported('UnmannedSystemsApi.listDroneFlightsActivity')).to.equal(true);
      expect(await wireIsSupported('SystemApi.listAuthentication')).to.equal(false);
      expect(await wireIsSupported('BinaryApi.uploadBinaryContent')).to.equal(false);
    });
  });
}, (data) => CoreError.deserialize(data));
