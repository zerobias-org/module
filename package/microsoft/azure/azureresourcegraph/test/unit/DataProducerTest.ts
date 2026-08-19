import { expect } from 'chai';
import nock from 'nock';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  PagedResults,
  UnexpectedError,
  NoSuchObjectError,
  InvalidInputError
} from '@zerobias-org/types-core-js';
import { AzureResourceGraphClient } from '../../src/AzureResourceGraphClient.js';
import { DataProducerImpl } from '../../src/impl/dataproducer/DataProducerImpl.js';
import { escapeKql } from '../../src/impl/dataproducer/DataProducerMappers.js';
import { SCHEMA_IDS, getSchemaById } from '../../src/impl/dataproducer/DataProducerSchemas.js';
import { ModelObject, ObjectClass, ConnectionProfile } from '../../generated/model/index.js';
import {
  cleanNock,
  mockTokenSuccess,
  ARM_BASE_URL,
  ARG_QUERY_PATH,
  ARG_API_VERSION,
  TEST_DIRECTORY_ID,
  TEST_CLIENT_ID,
  TEST_CLIENT_SECRET
} from '../utils/nock-helpers.js';

const dirname = path.dirname(fileURLToPath(import.meta.url));

const SUB_ID = 'aaaa1111-bbbb-2222-cccc-333344445555';
const VM_ID = `/subscriptions/${SUB_ID}/resourceGroups/rg-prod/providers/Microsoft.Compute/virtualMachines/vm-web-01`;

function loadFixture(name: string): Record<string, unknown> {
  const file = path.join(dirname, '../fixtures/templates', name);
  return JSON.parse(fs.readFileSync(file, 'utf8')) as Record<string, unknown>;
}

/** Extract one fixture row set filtered by ARG container type. */
function containerRows(type: string): Record<string, unknown> {
  const fixture = loadFixture('resource-containers-query-success.json');
  const data = (fixture.data as Record<string, unknown>[]).filter((row) => row.type === type);
  return { totalRecords: data.length, count: data.length, data };
}

/** Mock one ARG query and capture the request body. */
function mockArgQueryCapture(
  responseBody: unknown,
  captured: { body?: Record<string, unknown> }
): nock.Scope {
  return nock(ARM_BASE_URL)
    .post(ARG_QUERY_PATH, (body) => {
      captured.body = body as Record<string, unknown>;
      return true;
    })
    .query({ 'api-version': ARG_API_VERSION })
    .reply(200, responseBody as nock.Body);
}

async function expectRejects(
  promise: Promise<unknown>,
  errorType: new (...args: never[]) => Error
): Promise<void> {
  try {
    await promise;
  } catch (error) {
    expect(error).to.be.instanceOf(errorType);
    return;
  }
  expect.fail(`expected rejection with ${errorType.name}`);
}

describe('DataProducerImpl', () => {
  let client: AzureResourceGraphClient;
  let producer: DataProducerImpl;

  beforeEach(async () => {
    nock.disableNetConnect();
    client = new AzureResourceGraphClient();
    mockTokenSuccess();
    await client.connect({
      client_id: TEST_CLIENT_ID,
      client_secret: TEST_CLIENT_SECRET,
      directoryId: TEST_DIRECTORY_ID,
    } as unknown as ConnectionProfile);
    producer = new DataProducerImpl(client);
  });

  afterEach(() => {
    cleanNock();
    nock.enableNetConnect();
  });

  describe('escapeKql', () => {
    it('should backslash-escape quotes and backslashes', () => {
      expect(escapeKql("o'brien\\x")).to.equal("o\\'brien\\\\x");
    });
  });

  describe('ObjectsProducerApi', () => {
    it('getRootObject: id == name == "/" and Container class', async () => {
      const root = await producer.getRootObject();

      expect(root.id).to.equal('/');
      expect(root.name).to.equal('/');
      expect(root.objectClass).to.deep.equal([ObjectClass.Container]);
    });

    it('getChildren("/"): the three static containers', async () => {
      const results = new PagedResults<ModelObject>();
      await producer.getChildren(results, '/');

      expect(results.items.map((item) => item.id)).to.deep.equal([
        'subscriptions',
        'managementgroups',
        'query',
      ]);
    });

    it('getChildren("subscriptions"): queries ResourceContainers filtered by subscription type', async () => {
      const captured: { body?: Record<string, unknown> } = {};
      mockArgQueryCapture(containerRows('microsoft.resources/subscriptions'), captured);

      const results = new PagedResults<ModelObject>();
      await producer.getChildren(results, 'subscriptions');

      expect(captured.body?.query).to.include("microsoft.resources/subscriptions'");
      expect(results.items).to.have.length(1);

      const subscription = results.items[0];
      expect(subscription.id).to.equal(`sub_${SUB_ID}`);
      expect(subscription.name).to.equal('Production');
      expect(subscription.objectClass).to.deep.equal([ObjectClass.Container, ObjectClass.Document]);
      expect(subscription.documentSchema).to.equal(SCHEMA_IDS.RESOURCE_CONTAINER);
    });

    it('getChildren("managementgroups"): maps mg rows to navigable objects', async () => {
      const captured: { body?: Record<string, unknown> } = {};
      mockArgQueryCapture(containerRows('microsoft.management/managementgroups'), captured);

      const results = new PagedResults<ModelObject>();
      await producer.getChildren(results, 'managementgroups');

      expect(captured.body?.query).to.include('microsoft.management/managementgroups');
      expect(results.items.map((item) => item.id)).to.deep.equal(['mg_mg-root']);
    });

    it('getChildren("query"): the ARG query function object', async () => {
      const results = new PagedResults<ModelObject>();
      await producer.getChildren(results, 'query');

      expect(results.items).to.have.length(1);
      const func = results.items[0];
      expect(func.id).to.equal('func_arg_query');
      expect(func.objectClass).to.deep.equal([ObjectClass.Function]);
      expect(func.inputSchema).to.equal(SCHEMA_IDS.ARG_QUERY_INPUT);
      expect(func.outputSchema).to.equal(SCHEMA_IDS.ARG_QUERY_OUTPUT);
    });

    it('getChildren(sub_): scopes to the subscription and prepends the resources collection', async () => {
      const captured: { body?: Record<string, unknown> } = {};
      mockArgQueryCapture(containerRows('microsoft.resources/subscriptions/resourcegroups'), captured);

      const results = new PagedResults<ModelObject>();
      await producer.getChildren(results, `sub_${SUB_ID}`);

      expect(captured.body?.subscriptions).to.deep.equal([SUB_ID]);
      expect(captured.body?.query).to.include('resourcegroups');

      expect(results.items[0].id).to.equal(`collection_resources_sub_${SUB_ID}`);
      expect(results.items[0].objectClass).to.deep.equal([ObjectClass.Collection]);
      expect(results.items[0].collectionSchema).to.equal(SCHEMA_IDS.RESOURCE);
      expect(results.items[1].id).to.equal(`rg_${SUB_ID}/rg-prod`);
    });

    it('getChildren(rg_ / mg_): static scoped collection edge, no API call', async () => {
      const rgResults = new PagedResults<ModelObject>();
      await producer.getChildren(rgResults, `rg_${SUB_ID}/rg-prod`);
      expect(rgResults.items.map((item) => item.id)).to.deep.equal([
        `collection_resources_rg_${SUB_ID}/rg-prod`,
      ]);

      const mgResults = new PagedResults<ModelObject>();
      await producer.getChildren(mgResults, 'mg_mg-root');
      expect(mgResults.items.map((item) => item.id)).to.deep.equal([
        'collection_resources_mg_mg-root',
      ]);
    });

    it('getObject(sub_): fetches the container row by subscriptionId', async () => {
      const captured: { body?: Record<string, unknown> } = {};
      mockArgQueryCapture(containerRows('microsoft.resources/subscriptions'), captured);

      const subscription = await producer.getObject(`sub_${SUB_ID}`);

      expect(captured.body?.query).to.include(`subscriptionId =~ '${SUB_ID}'`);
      expect(subscription.id).to.equal(`sub_${SUB_ID}`);
    });

    it('getObject(res_): fetches the resource row by full ARM id', async () => {
      const captured: { body?: Record<string, unknown> } = {};
      mockArgQueryCapture(loadFixture('resources-query-success.json'), captured);

      const resource = await producer.getObject(`res_${VM_ID}`);

      expect(captured.body?.query).to.include(`id =~ '${VM_ID}'`);
      expect(resource.id).to.equal(`res_${VM_ID}`);
      expect(resource.objectClass).to.deep.equal([ObjectClass.Document]);
      expect(resource.documentSchema).to.equal(SCHEMA_IDS.RESOURCE);
    });

    it('getObject: NoSuchObjectError when the row does not exist', async () => {
      mockArgQueryCapture({ totalRecords: 0, count: 0, data: [] }, {});
      await expectRejects(producer.getObject('mg_missing'), NoSuchObjectError);
    });

    it('getObject: NoSuchObjectError for an unknown prefix', async () => {
      await expectRejects(producer.getObject('bogus_id'), NoSuchObjectError);
    });

    it('objectSearch: keyword search over resource names', async () => {
      const captured: { body?: Record<string, unknown> } = {};
      mockArgQueryCapture(loadFixture('resources-query-success.json'), captured);

      const results = new PagedResults<ModelObject>();
      await producer.objectSearch(results, '/', undefined, ['vm-web', 'vnet']);

      expect(captured.body?.query).to.include("name contains 'vm-web' or name contains 'vnet'");
      expect(results.items).to.have.length(3);
    });

    it('objectSearch: empty keywords short-circuits to no results', async () => {
      const results = new PagedResults<ModelObject>();
      await producer.objectSearch(results, '/');
      expect(results.items).to.deep.equal([]);
    });

    it('write methods throw UnexpectedError (read-only module)', async () => {
      await expectRejects(
        producer.createChildObject('/', { name: 'x', objectClass: [ObjectClass.Container] }),
        UnexpectedError
      );
      await expectRejects(producer.updateObject(`sub_${SUB_ID}`, {}), UnexpectedError);
      await expectRejects(producer.deleteObject(`sub_${SUB_ID}`), UnexpectedError);
    });
  });

  describe('CollectionsProducerApi', () => {
    it('getCollectionElements(sub scope): scoped request, plain-value elements, cursor carried', async () => {
      const captured: { body?: Record<string, unknown> } = {};
      const fixture = loadFixture('resources-query-success.json');
      (fixture as Record<string, unknown>).$skipToken = 'next-page-token';
      mockArgQueryCapture(fixture, captured);

      const results = new PagedResults<object>();
      await producer.getCollectionElements(results, `collection_resources_sub_${SUB_ID}`);

      expect(captured.body?.query).to.equal('Resources');
      expect(captured.body?.subscriptions).to.deep.equal([SUB_ID]);
      expect(results.items).to.have.length(3);
      expect(results.count).to.equal(3);
      expect(results.pageToken).to.equal('next-page-token');

      const vm = results.items[0] as Record<string, unknown>;
      expect(vm.id).to.equal(VM_ID);
      expect(vm.name).to.equal('vm-web-01');
    });

    it('getCollectionElements(rg scope): adds the resourceGroup where clause', async () => {
      const captured: { body?: Record<string, unknown> } = {};
      mockArgQueryCapture(loadFixture('resources-query-success.json'), captured);

      const results = new PagedResults<object>();
      await producer.getCollectionElements(results, `collection_resources_rg_${SUB_ID}/rg-prod`);

      expect(captured.body?.query).to.include("resourceGroup =~ 'rg-prod'");
      expect(captured.body?.subscriptions).to.deep.equal([SUB_ID]);
    });

    it('getCollectionElements(mg scope): scopes via managementGroups', async () => {
      const captured: { body?: Record<string, unknown> } = {};
      mockArgQueryCapture(loadFixture('resources-query-success.json'), captured);

      const results = new PagedResults<object>();
      await producer.getCollectionElements(results, 'collection_resources_mg_mg-root');

      expect(captured.body?.managementGroups).to.deep.equal(['mg-root']);
    });

    it('getCollectionElements: NoSuchObjectError for a malformed collection id', async () => {
      const results = new PagedResults<object>();
      await expectRejects(
        producer.getCollectionElements(results, 'collection_resources_bogus'),
        NoSuchObjectError
      );
    });

    it('getCollectionElement: looks up one element by ARM id within scope', async () => {
      const captured: { body?: Record<string, unknown> } = {};
      mockArgQueryCapture(loadFixture('resources-query-success.json'), captured);

      const element = await producer.getCollectionElement(
        `collection_resources_sub_${SUB_ID}`,
        VM_ID
      );

      expect(captured.body?.query).to.include(`id =~ '${VM_ID}'`);
      expect((element as Record<string, unknown>).name).to.equal('vm-web-01');
    });

    it('getCollectionElement: NoSuchObjectError when the element is missing', async () => {
      mockArgQueryCapture({ totalRecords: 0, count: 0, data: [] }, {});
      await expectRejects(
        producer.getCollectionElement(`collection_resources_sub_${SUB_ID}`, '/missing'),
        NoSuchObjectError
      );
    });

    it('collection write methods throw UnexpectedError (read-only module)', async () => {
      const collectionId = `collection_resources_sub_${SUB_ID}`;
      await expectRejects(producer.addCollectionElement(collectionId, {}), UnexpectedError);
      await expectRejects(producer.updateCollectionElement(collectionId, 'k', {}), UnexpectedError);
      await expectRejects(producer.deleteCollectionElement(collectionId, 'k'), UnexpectedError);
      await expectRejects(
        producer.executeBulkOperations(collectionId, { operations: [] }),
        UnexpectedError
      );
      await expectRejects(
        producer.searchCollectionElements(new PagedResults<object>(), collectionId),
        UnexpectedError
      );
    });
  });

  describe('DocumentsProducerApi', () => {
    it('getDocumentData(res_): value-wrapped fields including nested identity', async () => {
      mockArgQueryCapture(loadFixture('resources-query-success.json'), {});

      const document = await producer.getDocumentData(`res_${VM_ID}`);

      expect(document.id).to.deep.equal({ value: VM_ID });
      expect(document.name).to.deep.equal({ value: 'vm-web-01' });
      expect(document.type).to.deep.equal({ value: 'microsoft.compute/virtualmachines' });
      const identity = document.identity as Record<string, unknown>;
      expect(identity.type).to.deep.equal({ value: 'SystemAssigned' });
      expect(document.tags).to.deep.equal({ value: { env: 'prod', owner: 'platform' } });
      // null ARG columns must not appear as wrapped undefineds
      expect(document).to.not.have.property('sku');
      expect(document).to.not.have.property('plan');
    });

    it('getDocumentData(sub_): container row as value-wrapped document', async () => {
      mockArgQueryCapture(containerRows('microsoft.resources/subscriptions'), {});

      const document = await producer.getDocumentData(`sub_${SUB_ID}`);

      expect(document.name).to.deep.equal({ value: 'Production' });
      expect(document.type).to.deep.equal({ value: 'microsoft.resources/subscriptions' });
      expect(document.subscriptionId).to.deep.equal({ value: SUB_ID });
    });

    it('getDocumentData: UnexpectedError for a non-document object', async () => {
      await expectRejects(producer.getDocumentData('subscriptions'), UnexpectedError);
    });

    it('updateDocumentData throws UnexpectedError (read-only module)', async () => {
      await expectRejects(producer.updateDocumentData(`res_${VM_ID}`, {}), UnexpectedError);
    });
  });

  describe('FunctionsProducerApi', () => {
    it('invokeFunction(func_arg_query): unwraps enveloped inputs and wraps outputs', async () => {
      const captured: { body?: Record<string, unknown> } = {};
      const fixture = loadFixture('resources-query-success.json');
      (fixture as Record<string, unknown>).$skipToken = 'more';
      mockArgQueryCapture(fixture, captured);

      const output = await producer.invokeFunction('func_arg_query', {
        query: { value: "Resources | where type =~ 'microsoft.compute/virtualmachines'" } as unknown as object,
        subscriptions: { value: [SUB_ID] } as unknown as object,
        pageSize: { value: 50 } as unknown as object,
      });

      expect(captured.body?.query).to.equal("Resources | where type =~ 'microsoft.compute/virtualmachines'");
      expect(captured.body?.subscriptions).to.deep.equal([SUB_ID]);
      expect((captured.body?.options as Record<string, unknown>).$top).to.equal(50);

      expect((output.data as Record<string, unknown>).value).to.have.length(3);
      expect(output.totalRecords).to.deep.equal({ value: 3 });
      expect(output.skipToken).to.deep.equal({ value: 'more' });
    });

    it('invokeFunction: plain (non-enveloped) inputs also work', async () => {
      const captured: { body?: Record<string, unknown> } = {};
      mockArgQueryCapture({ totalRecords: 0, count: 0, data: [] }, captured);

      await producer.invokeFunction('func_arg_query', {
        query: 'ResourceContainers' as unknown as object,
        skipToken: 'resume-here' as unknown as object,
      });

      expect(captured.body?.query).to.equal('ResourceContainers');
      expect((captured.body?.options as Record<string, unknown>).$skipToken).to.equal('resume-here');
    });

    it('invokeFunction: InvalidInputError without a query', async () => {
      await expectRejects(producer.invokeFunction('func_arg_query', {}), InvalidInputError);
    });

    it('invokeFunction: UnexpectedError for an unknown function', async () => {
      await expectRejects(producer.invokeFunction('func_nope'), UnexpectedError);
    });

    it('validateFunctionInput throws UnexpectedError (not implemented)', async () => {
      await expectRejects(
        producer.validateFunctionInput('func_arg_query', {}),
        UnexpectedError
      );
    });
  });

  describe('BinaryProducerApi', () => {
    it('both binary operations throw UnexpectedError (ARG has no binary content)', async () => {
      await expectRejects(producer.downloadBinary(`res_${VM_ID}`), UnexpectedError);
      await expectRejects(
        producer.uploadBinaryContent(`res_${VM_ID}`, Buffer.from('') as never),
        UnexpectedError
      );
    });
  });

  describe('SchemasProducerApi', () => {
    it('getSchema: returns the resource schema with its primary key', async () => {
      const schema = await producer.getSchema(SCHEMA_IDS.RESOURCE);

      expect(schema.id).to.equal(SCHEMA_IDS.RESOURCE);
      const idProperty = schema.properties.find((property) => property.name === 'id');
      expect(idProperty, 'resource schema exposes the id property').to.exist;
      expect(idProperty?.primaryKey).to.equal(true);
    });

    it('getSchema: resourceContainer and function schemas resolve', async () => {
      expect((await producer.getSchema(SCHEMA_IDS.RESOURCE_CONTAINER)).id)
        .to.equal(SCHEMA_IDS.RESOURCE_CONTAINER);
      expect((await producer.getSchema(SCHEMA_IDS.ARG_QUERY_INPUT)).id)
        .to.equal(SCHEMA_IDS.ARG_QUERY_INPUT);
      expect((await producer.getSchema(SCHEMA_IDS.ARG_QUERY_OUTPUT)).id)
        .to.equal(SCHEMA_IDS.ARG_QUERY_OUTPUT);
    });

    it('getSchema: NoSuchObjectError for an unknown schema id', async () => {
      await expectRejects(producer.getSchema('schema:shared:nope'), NoSuchObjectError);
    });

    it('getSchemaById mirrors the producer lookup', () => {
      expect(getSchemaById(SCHEMA_IDS.RESOURCE)).to.exist;
      expect(getSchemaById('schema:shared:nope')).to.equal(undefined);
    });
  });
});
