/**
 * Azureresourcegraph Module E2E Tests — one test, three impls.
 *
 * TEST_MODE selects the impl:
 *   direct  — AzureresourcegraphImpl (in-process)
 *   docker  — AzureresourcegraphClient (container REST)
 *   hub     — AzureresourcegraphClient (Hub Server via Dana)
 *
 * All three return the same Azureresourcegraph interface. Requires a live
 * Azure AD tenant with a Reader-scoped service principal (secrets:
 * client_id / client_secret / directoryId); skips otherwise.
 */

import { expect } from 'chai';
import { CoreError } from '@zerobias-org/types-core-js';
import { describeModule } from '@zerobias-org/module-test-client';
import type { Azureresourcegraph } from '../../hub-sdk/generated/api/index.js';
import { AZURE_SUBSCRIPTION_ID, hasCredentials } from './constants.js';

describeModule<Azureresourcegraph>('Azureresourcegraph Module', (client) => {

  before(function () {
    if (!hasCredentials()) {
      this.skip();
    }
  });

  // ── Resource API ─────────────────────────────────────────

  describe('ResourceApi.list', () => {
    it('should retrieve a page of resources at tenant scope', async () => {
      const results = await client.getResourceApi().list();

      expect(results.items).to.be.an('array');
      expect(results.count ?? 0).to.be.a('number');

      for (const resource of results.items) {
        expect(resource.id, 'every row carries an ARM id').to.be.a('string');
        expect(resource.name, 'every row carries a name').to.be.a('string');
        expect(resource.type, 'every row carries a type (assetType discriminator)').to.be.a('string');
      }
    });

    it('should honor pageSize and expose a continuation token on truncation', async function () {
      const firstPage = await client.getResourceApi().list(1);

      expect(firstPage.items.length).to.be.at.most(1);

      if ((firstPage.count ?? 0) <= 1) {
        // Tenant holds at most one visible resource — nothing to paginate.
        this.skip();
      }

      expect(firstPage.pageToken, 'truncated result must carry $skipToken').to.be.a('string');

      const secondPage = await client.getResourceApi().list(undefined, firstPage.pageToken);
      expect(secondPage.items).to.be.an('array');
      if (firstPage.items.length && secondPage.items.length) {
        expect(secondPage.items[0].id).to.not.equal(firstPage.items[0].id);
      }
    });

    it('should scope by subscription when one is provided', async function () {
      if (!AZURE_SUBSCRIPTION_ID) {
        this.skip();
      }

      const results = await client.getResourceApi().list(undefined, undefined, [AZURE_SUBSCRIPTION_ID]);

      for (const resource of results.items) {
        expect(resource.subscriptionId).to.equal(AZURE_SUBSCRIPTION_ID);
      }
    });
  });

  // ── DataProducer interface ───────────────────────────────

  describe('DataProducer', () => {
    it('getRootObject returns the "/" container', async () => {
      const root = await client.getObjectsApi().getRootObject();

      expect(root.id).to.equal('/');
      expect(root.name).to.equal('/');
      expect(root.objectClass).to.include('container');
    });

    it('getChildren("/") lists the static containers', async () => {
      const children = await client.getObjectsApi().getChildren('/');

      expect(children.items.map((item) => item.id)).to.include.members([
        'subscriptions',
        'managementgroups',
        'query',
      ]);
    });

    it('navigates subscriptions → subscription object → children', async function () {
      const subscriptions = await client.getObjectsApi().getChildren('subscriptions');
      expect(subscriptions.items).to.be.an('array');

      if (subscriptions.items.length === 0) {
        this.skip();
      }

      const first = subscriptions.items[0];
      expect(first.id).to.match(/^sub_/);
      expect(first.documentSchema).to.be.a('string');

      const fetched = await client.getObjectsApi().getObject(first.id);
      expect(fetched.id).to.equal(first.id);

      const children = await client.getObjectsApi().getChildren(first.id);
      expect(children.items[0].id).to.equal(`collection_resources_${first.id}`);
    });

    it('getCollectionElements returns scoped resource rows', async function () {
      const subscriptions = await client.getObjectsApi().getChildren('subscriptions');
      if (subscriptions.items.length === 0) {
        this.skip();
      }

      const collectionId = `collection_resources_${subscriptions.items[0].id}`;
      const elements = await client.getCollectionsApi().getCollectionElements(collectionId);

      expect(elements.items).to.be.an('array');
      for (const element of elements.items as Array<Record<string, unknown>>) {
        expect(element.id, 'every element carries its ARM id').to.be.a('string');
      }
    });

    it('getDocumentData returns a value-wrapped subscription document', async function () {
      const subscriptions = await client.getObjectsApi().getChildren('subscriptions');
      if (subscriptions.items.length === 0) {
        this.skip();
      }

      const document = await client.getDocumentsApi().getDocumentData(subscriptions.items[0].id);
      expect(document).to.be.an('object');
      expect(document.type).to.deep.equal({ value: 'microsoft.resources/subscriptions' });
    });

    it('getSchema resolves each published schema id', async () => {
      const schemaIds = [
        'schema:shared:microsoft.azure.azureresourcegraph.resource',
        'schema:shared:microsoft.azure.azureresourcegraph.resourceContainer',
        'schema:function:azure.azureresourcegraph.argQuery:input',
        'schema:function:azure.azureresourcegraph.argQuery:output',
      ];

      for (const schemaId of schemaIds) {
        const schema = await client.getSchemasApi().getSchema(schemaId);
        expect(schema.id).to.equal(schemaId);
        expect(schema.properties).to.be.an('array');
      }
    });

    it('invokeFunction(func_arg_query) executes a raw KQL query', async () => {
      const output = await client.getFunctionsApi().invokeFunction('func_arg_query', {
        query: { value: 'ResourceContainers | take 1' } as unknown as object,
      });

      expect(output.data).to.have.property('value');
    });

    it('write operations reject (read-only module)', async () => {
      let failed = false;
      try {
        await client.getObjectsApi().deleteObject('sub_nope');
      } catch {
        failed = true;
      }
      expect(failed, 'deleteObject must throw — ARG is read-only').to.equal(true);
    });
  });

  // ── ResourceContainer API ────────────────────────────────

  describe('ResourceContainerApi.list', () => {
    it('should retrieve subscriptions / resource groups / management groups', async () => {
      const results = await client.getResourceContainerApi().list();

      expect(results.items).to.be.an('array');
      expect(results.items.length, 'a readable tenant always has at least one container').to.be.greaterThan(0);

      const knownTypes = new Set([
        'microsoft.resources/subscriptions',
        'microsoft.resources/subscriptions/resourcegroups',
        'microsoft.management/managementgroups',
      ]);

      for (const container of results.items) {
        expect(container.id).to.be.a('string');
        expect(container.name).to.be.a('string');
        expect(knownTypes.has(container.type), `unexpected container type ${container.type}`).to.be.true;
      }
    });
  });

}, (data) => CoreError.deserialize(data));
