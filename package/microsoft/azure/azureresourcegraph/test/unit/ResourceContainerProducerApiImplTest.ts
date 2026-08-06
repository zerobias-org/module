import { expect } from 'chai';
import nock from 'nock';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { PagedResults } from '@zerobias-org/types-core-js';
import { AzureResourceGraphClient } from '../../src/AzureResourceGraphClient.js';
import { ResourceContainerProducerApiImpl } from '../../src/ResourceContainerProducerApiImpl.js';
import { ResourceContainer } from '../../generated/model/index.js';
import { ConnectionProfile } from '../../generated/model/index.js';
import {
  cleanNock,
  mockTokenSuccess,
  TEST_DIRECTORY_ID,
  TEST_CLIENT_ID,
  TEST_CLIENT_SECRET
} from '../utils/nock-helpers.js';

const dirname = path.dirname(fileURLToPath(import.meta.url));

function loadFixture(name: string): Record<string, unknown> {
  const file = path.join(dirname, '../fixtures/templates', name);
  return JSON.parse(fs.readFileSync(file, 'utf8')) as Record<string, unknown>;
}

describe('ResourceContainerProducerApiImpl', () => {
  let client: AzureResourceGraphClient;
  let producer: ResourceContainerProducerApiImpl;

  beforeEach(async () => {
    nock.disableNetConnect();
    client = new AzureResourceGraphClient();
    mockTokenSuccess();
    await client.connect({
      client_id: TEST_CLIENT_ID,
      client_secret: TEST_CLIENT_SECRET,
      directoryId: TEST_DIRECTORY_ID,
    } as unknown as ConnectionProfile);
    producer = new ResourceContainerProducerApiImpl(client);
  });

  afterEach(() => {
    cleanNock();
    nock.enableNetConnect();
  });

  describe('list()', () => {
    it('should query the ResourceContainers table and map all three container kinds', async () => {
      let capturedBody: Record<string, unknown> | undefined;
      nock('https://management.azure.com')
        .post('/providers/Microsoft.ResourceGraph/resources', (body) => {
          capturedBody = body as Record<string, unknown>;
          return true;
        })
        .query({ 'api-version': '2024-04-01' })
        .reply(200, loadFixture('resource-containers-query-success.json'));

      const results = new PagedResults<ResourceContainer>();
      await producer.list(results);

      expect(capturedBody?.query).to.equal('ResourceContainers');
      expect(results.items).to.have.length(3);
      expect(results.count).to.equal(3);

      const [subscription, resourceGroup, managementGroup] = results.items;

      expect(subscription.type).to.equal('microsoft.resources/subscriptions');
      expect(subscription.name).to.equal('Production');
      expect(subscription.subscriptionId).to.equal('aaaa1111-bbbb-2222-cccc-333344445555');
      // null columns must normalize to undefined
      expect(subscription.location).to.be.undefined;
      expect(subscription.resourceGroup).to.be.undefined;

      expect(resourceGroup.type).to.equal('microsoft.resources/subscriptions/resourcegroups');
      expect(resourceGroup.location).to.equal('eastus');

      expect(managementGroup.type).to.equal('microsoft.management/managementgroups');
      expect(managementGroup.subscriptionId).to.be.undefined;
      expect(managementGroup.tags).to.be.undefined;
    });
  });
});
