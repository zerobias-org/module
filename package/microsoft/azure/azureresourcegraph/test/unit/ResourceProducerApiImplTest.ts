import { expect } from 'chai';
import nock from 'nock';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { PagedResults } from '@zerobias-org/types-core-js';
import { AzureResourceGraphClient } from '../../src/AzureResourceGraphClient.js';
import { ResourceProducerApiImpl } from '../../src/ResourceProducerApiImpl.js';
import { Resource } from '../../generated/model/index.js';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile.js';
import {
  cleanNock,
  mockTokenSuccess,
  mockArgQuery,
  TEST_DIRECTORY_ID,
  TEST_CLIENT_ID,
  TEST_CLIENT_SECRET
} from '../utils/nock-helpers.js';

const dirname = path.dirname(fileURLToPath(import.meta.url));

function loadFixture(name: string): Record<string, unknown> {
  const file = path.join(dirname, '../fixtures/templates', name);
  return JSON.parse(fs.readFileSync(file, 'utf8')) as Record<string, unknown>;
}

describe('ResourceProducerApiImpl', () => {
  let client: AzureResourceGraphClient;
  let producer: ResourceProducerApiImpl;

  beforeEach(async () => {
    nock.disableNetConnect();
    client = new AzureResourceGraphClient();
    mockTokenSuccess();
    await client.connect({
      client_id: TEST_CLIENT_ID,
      client_secret: TEST_CLIENT_SECRET,
      directoryId: TEST_DIRECTORY_ID,
    } as unknown as ConnectionProfile);
    producer = new ResourceProducerApiImpl(client);
  });

  afterEach(() => {
    cleanNock();
    nock.enableNetConnect();
  });

  describe('list()', () => {
    it('should query the Resources table and map rows', async () => {
      let capturedBody: Record<string, unknown> | undefined;
      nock('https://management.azure.com')
        .post('/providers/Microsoft.ResourceGraph/resources', (body) => {
          capturedBody = body as Record<string, unknown>;
          return true;
        })
        .query({ 'api-version': '2024-04-01' })
        .reply(200, loadFixture('resources-query-success.json'));

      const results = new PagedResults<Resource>();
      await producer.list(results);

      expect(capturedBody?.query).to.equal('Resources');
      expect(results.items).to.have.length(3);
      expect(results.count).to.equal(3);
      expect(results.pageToken).to.be.undefined;

      const vm = results.items[0];
      expect(vm.id).to.include('/virtualMachines/vm-web-01');
      expect(vm.name).to.equal('vm-web-01');
      expect(vm.type).to.equal('microsoft.compute/virtualmachines');
      expect(vm.identity?.type).to.equal('SystemAssigned');
      expect(vm.tags?.env).to.equal('prod');
      // null columns must normalize to undefined
      expect(vm.sku).to.be.undefined;
      expect(vm.plan).to.be.undefined;
    });

    it('should carry the response $skipToken as results.pageToken', async () => {
      mockArgQuery(loadFixture('resources-query-paged.json'));

      const results = new PagedResults<Resource>();
      results.pageSize = 1000;
      await producer.list(results);

      expect(results.pageToken).to.equal('skiptoken-page-2-opaque-value');
      expect(results.count).to.equal(2500);
    });

    it('should send the previous pageToken as options.$skipToken', async () => {
      let capturedBody: Record<string, unknown> | undefined;
      nock('https://management.azure.com')
        .post('/providers/Microsoft.ResourceGraph/resources', (body) => {
          capturedBody = body as Record<string, unknown>;
          return true;
        })
        .query({ 'api-version': '2024-04-01' })
        .reply(200, { totalRecords: 0, count: 0, data: [] });

      const results = new PagedResults<Resource>();
      results.pageToken = 'continuation-token';
      await producer.list(results);

      const options = capturedBody?.options as Record<string, unknown>;
      expect(options.$skipToken).to.equal('continuation-token');
      expect(options.$top).to.be.undefined;
    });

    it('should bound $top by the ARG max of 1000', async () => {
      let capturedBody: Record<string, unknown> | undefined;
      nock('https://management.azure.com')
        .post('/providers/Microsoft.ResourceGraph/resources', (body) => {
          capturedBody = body as Record<string, unknown>;
          return true;
        })
        .query({ 'api-version': '2024-04-01' })
        .reply(200, { totalRecords: 0, count: 0, data: [] });

      const results = new PagedResults<Resource>();
      results.pageSize = 5000;
      await producer.list(results);

      const options = capturedBody?.options as Record<string, unknown>;
      expect(options.$top).to.equal(1000);
    });

    it('should pass subscription scope into the request body', async () => {
      let capturedBody: Record<string, unknown> | undefined;
      nock('https://management.azure.com')
        .post('/providers/Microsoft.ResourceGraph/resources', (body) => {
          capturedBody = body as Record<string, unknown>;
          return true;
        })
        .query({ 'api-version': '2024-04-01' })
        .reply(200, { totalRecords: 0, count: 0, data: [] });

      const results = new PagedResults<Resource>();
      await producer.list(results, ['sub-1', 'sub-2'], ['mg-root']);

      expect(capturedBody?.subscriptions).to.deep.equal(['sub-1', 'sub-2']);
      expect(capturedBody?.managementGroups).to.deep.equal(['mg-root']);
    });

    it('should omit scope fields for a tenant-scope query', async () => {
      let capturedBody: Record<string, unknown> | undefined;
      nock('https://management.azure.com')
        .post('/providers/Microsoft.ResourceGraph/resources', (body) => {
          capturedBody = body as Record<string, unknown>;
          return true;
        })
        .query({ 'api-version': '2024-04-01' })
        .reply(200, { totalRecords: 0, count: 0, data: [] });

      const results = new PagedResults<Resource>();
      await producer.list(results);

      expect(capturedBody?.subscriptions).to.be.undefined;
      expect(capturedBody?.managementGroups).to.be.undefined;
    });
  });
});
