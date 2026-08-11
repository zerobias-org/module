import { PagedResults, NoSuchObjectError, InvalidInputError } from '@zerobias-org/types-core-js';
import { ModelObject, ObjectClass, Resource, ResourceContainer } from '../../../generated/model/index.js';
import {
  AzureResourceGraphClient,
  ArgQueryRequest,
  ArgQueryOptions,
  ArgQueryResponse
} from '../../AzureResourceGraphClient.js';
import { toResource, toResourceContainer } from '../../Mappers.js';
import { SCHEMA_IDS } from './DataProducerSchemas.js';

/**
 * DataProducerMappers — mapping functions for the Dynamic Data Producer
 * Interface on Azure Resource Graph.
 *
 * Everything routes through the ONE endpoint this module wraps — the ARG
 * query API — via `AzureResourceGraphClient.query()`. The existing producer
 * methods are list-only over fixed table queries, so scoped lookups
 * (get-by-id, per-container children) are expressed as filtered KQL on the
 * same two tables (`Resources`, `ResourceContainers`).
 *
 * Object ID strategy:
 * - Root: "/"
 * - Static containers: "subscriptions", "managementgroups", "query"
 * - Subscriptions: "sub_{subscriptionId}"
 * - Resource groups: "rg_{subscriptionId}/{resourceGroupName}"
 * - Management groups: "mg_{managementGroupName}"
 * - Resources: "res_{fullArmResourceId}"
 * - Collections: "collection_resources_{sub|rg|mg scope id}"
 * - Functions: "func_arg_query"
 */

/** ARG `ResourceContainers` type discriminators. */
const SUBSCRIPTION_TYPE = 'microsoft.resources/subscriptions';
const RESOURCE_GROUP_TYPE = 'microsoft.resources/subscriptions/resourcegroups';
const MANAGEMENT_GROUP_TYPE = 'microsoft.management/managementgroups';

/** ARG caps options.$top at 1000 (same bound the existing producers apply). */
const MAX_PAGE_SIZE = 1000;

/**
 * Escape a value for interpolation into a single-quoted KQL string literal.
 * KQL string literals use backslash escaping.
 */
export function escapeKql(value: string): string {
  return value.replaceAll('\\', '\\\\').replaceAll("'", "\\'");
}

/**
 * Run an ARG query honoring PagedResults cursor pagination — the same
 * $skipToken/$top contract the existing producers implement.
 */
async function runPagedQuery(
  client: AzureResourceGraphClient,
  request: ArgQueryRequest,
  results: PagedResults<unknown>
): Promise<ArgQueryResponse> {
  const options: ArgQueryOptions = { ...request.options };

  if (results.pageToken) {
    options.$skipToken = results.pageToken;
  } else if (results.pageSize) {
    options.$top = Math.min(Math.max(results.pageSize, 1), MAX_PAGE_SIZE);
  }

  const response = await client.query({ ...request, options });

  results.count = response.totalRecords || 0;
  results.pageToken = response.$skipToken;

  return response;
}

/** Fetch exactly one row or throw NoSuchObjectError. */
async function queryOne(
  client: AzureResourceGraphClient,
  request: ArgQueryRequest,
  objectType: string,
  objectId: string
): Promise<Record<string, unknown>> {
  const response = await client.query(request);
  const row = response.data[0];
  if (!row) {
    throw new NoSuchObjectError(objectType, objectId);
  }
  return row;
}

// ==================== Root and Container Objects ====================

export function buildRootObject(): ModelObject {
  return new ModelObject(
    '/',
    '/', // root: id == name == '/' (DataProducer spec MUST)
    'Azure Resource Graph — subscriptions, management groups and resource inventory',
    ['/'],
    undefined,
    ['azure', 'resources', 'arg'],
    undefined,
    undefined,
    undefined,
    undefined,
    [ObjectClass.Container]
  );
}

export function buildContainerObject(objectId: string): ModelObject {
  const containerMap: Record<string, { name: string; description: string }> = {
    subscriptions: {
      name: 'Subscriptions',
      description: 'Azure subscriptions visible to the service principal',
    },
    managementgroups: {
      name: 'Management Groups',
      description: 'Azure management groups visible to the service principal',
    },
    query: {
      name: 'Query',
      description: 'Azure Resource Graph query functions',
    },
  };

  const container = containerMap[objectId];
  if (!container) {
    throw new NoSuchObjectError('container', objectId);
  }

  return new ModelObject(
    objectId,
    container.name,
    container.description,
    ['/', objectId],
    undefined,
    ['container'],
    undefined,
    undefined,
    undefined,
    undefined,
    [ObjectClass.Container]
  );
}

/**
 * Build a navigable subscription object from a `ResourceContainers` row.
 * A subscription is a Container (resource groups + resources collection
 * below it) and a Document (its container row is fetchable).
 */
export function buildSubscriptionObject(container: ResourceContainer): ModelObject {
  const subscriptionId = container.subscriptionId || container.name;
  return new ModelObject(
    `sub_${subscriptionId}`,
    container.name,
    `Azure subscription ${subscriptionId}`,
    ['/', 'subscriptions', subscriptionId],
    undefined,
    ['subscription'],
    undefined,
    undefined,
    undefined,
    undefined,
    [ObjectClass.Container, ObjectClass.Document],
    undefined,
    undefined,
    undefined,
    undefined,
    undefined,
    SCHEMA_IDS.RESOURCE_CONTAINER
  );
}

/**
 * Build a navigable resource group object from a `ResourceContainers` row.
 */
export function buildResourceGroupObject(container: ResourceContainer): ModelObject {
  const subscriptionId = container.subscriptionId || '';
  return new ModelObject(
    `rg_${subscriptionId}/${container.name}`,
    container.name,
    `Resource group ${container.name} in subscription ${subscriptionId}`,
    ['/', 'subscriptions', subscriptionId, 'resourcegroups', container.name],
    undefined,
    ['resource-group'],
    undefined,
    undefined,
    undefined,
    undefined,
    [ObjectClass.Container, ObjectClass.Document],
    undefined,
    undefined,
    undefined,
    undefined,
    undefined,
    SCHEMA_IDS.RESOURCE_CONTAINER
  );
}

/**
 * Build a navigable management group object from a `ResourceContainers` row.
 */
export function buildManagementGroupObject(container: ResourceContainer): ModelObject {
  return new ModelObject(
    `mg_${container.name}`,
    container.name,
    `Azure management group ${container.name}`,
    ['/', 'managementgroups', container.name],
    undefined,
    ['management-group'],
    undefined,
    undefined,
    undefined,
    undefined,
    [ObjectClass.Container, ObjectClass.Document],
    undefined,
    undefined,
    undefined,
    undefined,
    undefined,
    SCHEMA_IDS.RESOURCE_CONTAINER
  );
}

/**
 * Build a resource object from a `Resources` row. A resource is a leaf
 * Document — its full ARG row (including the `properties` bag) is the
 * document payload.
 */
export function buildResourceObject(resource: Resource): ModelObject {
  const path = resource.subscriptionId
    ? [
      '/',
      'subscriptions',
      resource.subscriptionId,
      ...(resource.resourceGroup ? ['resourcegroups', resource.resourceGroup] : []),
      'resources',
      resource.name,
    ]
    : ['/', 'resources', resource.name];

  return new ModelObject(
    `res_${resource.id}`,
    resource.name,
    `${resource.type}${resource.location ? ` (${resource.location})` : ''}`,
    path,
    undefined,
    ['resource', resource.type],
    undefined,
    undefined,
    undefined,
    undefined,
    [ObjectClass.Document],
    undefined,
    undefined,
    undefined,
    undefined,
    undefined,
    SCHEMA_IDS.RESOURCE
  );
}

/**
 * Build a resources Collection object for a subscription / resource group /
 * management group scope. `scopeId` is the owning object ID without its
 * prefix stripped (e.g. `sub_xxx`, `rg_xxx/yyy`, `mg_xxx`).
 */
export function buildResourcesCollectionObject(scopeId: string, collectionSize?: number): ModelObject {
  return new ModelObject(
    `collection_resources_${scopeId}`,
    'Resources',
    `Azure resources in ${scopeId.replace(/^(sub_|rg_|mg_)/, '')}`,
    undefined,
    undefined,
    ['resources'],
    undefined,
    undefined,
    undefined,
    undefined,
    [ObjectClass.Collection],
    SCHEMA_IDS.RESOURCE,
    collectionSize
  );
}

export function buildFunctionObject(objectId: string): ModelObject {
  if (objectId !== 'func_arg_query') {
    throw new NoSuchObjectError('function', objectId);
  }

  return new ModelObject(
    objectId,
    'ARG Query',
    'Execute a raw Azure Resource Graph KQL query with optional subscription / management-group scoping',
    undefined,
    undefined,
    ['function'],
    undefined,
    undefined,
    undefined,
    undefined,
    [ObjectClass.Function],
    undefined,
    undefined,
    SCHEMA_IDS.ARG_QUERY_INPUT,
    SCHEMA_IDS.ARG_QUERY_OUTPUT
  );
}

// ==================== Container Children ====================

export async function getRootChildren(results: PagedResults<ModelObject>): Promise<void> {
  results.items = [
    buildContainerObject('subscriptions'),
    buildContainerObject('managementgroups'),
    buildContainerObject('query'),
  ];
}

export async function getSubscriptionsChildren(
  client: AzureResourceGraphClient,
  results: PagedResults<ModelObject>
): Promise<void> {
  const response = await runPagedQuery(
    client,
    { query: `ResourceContainers | where type =~ '${SUBSCRIPTION_TYPE}'` },
    results
  );

  results.items = response.data.map((raw) => buildSubscriptionObject(toResourceContainer(raw)));
}

export async function getManagementGroupsChildren(
  client: AzureResourceGraphClient,
  results: PagedResults<ModelObject>
): Promise<void> {
  const response = await runPagedQuery(
    client,
    { query: `ResourceContainers | where type =~ '${MANAGEMENT_GROUP_TYPE}'` },
    results
  );

  results.items = response.data.map((raw) => buildManagementGroupObject(toResourceContainer(raw)));
}

export async function getQueryChildren(results: PagedResults<ModelObject>): Promise<void> {
  results.items = [buildFunctionObject('func_arg_query')];
}

/**
 * Children of a subscription: its resource groups, plus (first page only)
 * the subscription-scoped resources collection.
 */
export async function getSubscriptionChildren(
  client: AzureResourceGraphClient,
  objectId: string,
  results: PagedResults<ModelObject>
): Promise<void> {
  const subscriptionId = objectId.slice('sub_'.length);

  const firstPage = !results.pageToken;

  const response = await runPagedQuery(
    client,
    {
      query: `ResourceContainers | where type =~ '${RESOURCE_GROUP_TYPE}'`,
      subscriptions: [subscriptionId],
    },
    results
  );

  const children: ModelObject[] = response.data.map(
    (raw) => buildResourceGroupObject(toResourceContainer(raw))
  );

  // Emit the fixed collection edge once so it doesn't repeat as RGs paginate.
  if (firstPage) {
    children.unshift(buildResourcesCollectionObject(objectId));
  }

  results.items = children;
}

/** Children of a resource group: its scoped resources collection. */
export async function getResourceGroupChildren(
  objectId: string,
  results: PagedResults<ModelObject>
): Promise<void> {
  results.items = [buildResourcesCollectionObject(objectId)];
}

/** Children of a management group: its scoped resources collection. */
export async function getManagementGroupChildren(
  objectId: string,
  results: PagedResults<ModelObject>
): Promise<void> {
  results.items = [buildResourcesCollectionObject(objectId)];
}

// ==================== Object Getters ====================

export async function getSubscriptionObject(
  client: AzureResourceGraphClient,
  objectId: string
): Promise<ModelObject> {
  const subscriptionId = objectId.slice('sub_'.length);

  const row = await queryOne(
    client,
    {
      query: `ResourceContainers | where type =~ '${SUBSCRIPTION_TYPE}' and subscriptionId =~ '${escapeKql(subscriptionId)}'`,
      subscriptions: [subscriptionId],
    },
    'subscription',
    objectId
  );

  return buildSubscriptionObject(toResourceContainer(row));
}

export async function getResourceGroupObject(
  client: AzureResourceGraphClient,
  objectId: string
): Promise<ModelObject> {
  const { subscriptionId, resourceGroupName } = parseResourceGroupId(objectId);

  const row = await queryOne(
    client,
    {
      query: `ResourceContainers | where type =~ '${RESOURCE_GROUP_TYPE}' and name =~ '${escapeKql(resourceGroupName)}'`,
      subscriptions: [subscriptionId],
    },
    'resource group',
    objectId
  );

  return buildResourceGroupObject(toResourceContainer(row));
}

export async function getManagementGroupObject(
  client: AzureResourceGraphClient,
  objectId: string
): Promise<ModelObject> {
  const managementGroupName = objectId.slice('mg_'.length);

  const row = await queryOne(
    client,
    {
      query: `ResourceContainers | where type =~ '${MANAGEMENT_GROUP_TYPE}' and name =~ '${escapeKql(managementGroupName)}'`,
    },
    'management group',
    objectId
  );

  return buildManagementGroupObject(toResourceContainer(row));
}

export async function getResourceObject(
  client: AzureResourceGraphClient,
  objectId: string
): Promise<ModelObject> {
  const armId = objectId.slice('res_'.length);

  const row = await queryOne(
    client,
    { query: `Resources | where id =~ '${escapeKql(armId)}'` },
    'resource',
    objectId
  );

  return buildResourceObject(toResource(row));
}

// ==================== Search ====================

/**
 * Keyword search over resource names — the ARG analogue of the reference
 * module's repository search.
 */
export async function searchResources(
  client: AzureResourceGraphClient,
  keywords: Array<string>,
  results: PagedResults<ModelObject>
): Promise<void> {
  const clauses = keywords
    .filter((keyword) => keyword.length > 0)
    .map((keyword) => `name contains '${escapeKql(keyword)}'`);

  if (clauses.length === 0) {
    results.items = [];
    return;
  }

  const response = await runPagedQuery(
    client,
    { query: `Resources | where ${clauses.join(' or ')}` },
    results
  );

  results.items = response.data.map((raw) => buildResourceObject(toResource(raw)));
}

// ==================== Collections ====================

interface CollectionScope {
  /** Owning object id, e.g. `sub_xxx` / `rg_xxx/yyy` / `mg_xxx`. */
  scopeId: string;
  subscriptions?: string[];
  managementGroups?: string[];
  /** Extra KQL `where` clause narrowing the scope, when needed. */
  where?: string;
}

/** Parse `rg_{subscriptionId}/{resourceGroupName}`. */
function parseResourceGroupId(objectId: string): { subscriptionId: string; resourceGroupName: string } {
  const remainder = objectId.slice('rg_'.length);
  const separator = remainder.indexOf('/');
  if (separator <= 0 || separator === remainder.length - 1) {
    throw new NoSuchObjectError('resource group', objectId);
  }
  return {
    subscriptionId: remainder.slice(0, separator),
    resourceGroupName: remainder.slice(separator + 1),
  };
}

/** Parse a `collection_resources_{scope}` object ID into its query scope. */
export function parseResourcesCollectionId(objectId: string): CollectionScope {
  const prefix = 'collection_resources_';
  if (!objectId.startsWith(prefix)) {
    throw new NoSuchObjectError('collection', objectId);
  }

  const scopeId = objectId.slice(prefix.length);

  if (scopeId.startsWith('sub_')) {
    return { scopeId, subscriptions: [scopeId.slice('sub_'.length)] };
  }

  if (scopeId.startsWith('rg_')) {
    const { subscriptionId, resourceGroupName } = parseResourceGroupId(scopeId);
    return {
      scopeId,
      subscriptions: [subscriptionId],
      where: `resourceGroup =~ '${escapeKql(resourceGroupName)}'`,
    };
  }

  if (scopeId.startsWith('mg_')) {
    return { scopeId, managementGroups: [scopeId.slice('mg_'.length)] };
  }

  throw new NoSuchObjectError('collection', objectId);
}

function buildScopedResourcesRequest(scope: CollectionScope, extraWhere?: string): ArgQueryRequest {
  const clauses = [scope.where, extraWhere].filter((clause): clause is string => Boolean(clause));
  const query = clauses.length > 0
    ? `Resources | where ${clauses.join(' and ')}`
    : 'Resources';

  const request: ArgQueryRequest = { query };
  if (scope.subscriptions) {
    request.subscriptions = scope.subscriptions;
  }
  if (scope.managementGroups) {
    request.managementGroups = scope.managementGroups;
  }
  return request;
}

/**
 * List the elements of a scoped resources collection. Elements are the
 * existing `toResource`-mapped rows — plain field values, matching the
 * collection-element shape of the reference implementation.
 */
export async function getResourcesCollectionElements(
  client: AzureResourceGraphClient,
  objectId: string,
  results: PagedResults<object>
): Promise<void> {
  const scope = parseResourcesCollectionId(objectId);

  const response = await runPagedQuery(client, buildScopedResourcesRequest(scope), results);

  results.items = response.data.map((raw) => toResource(raw) as unknown as object);
}

/**
 * Fetch a single collection element by its primary key (the full ARM
 * resource ID), still scoped to the owning collection.
 */
export async function getResourcesCollectionElement(
  client: AzureResourceGraphClient,
  objectId: string,
  elementKey: string
): Promise<{ [key: string]: object }> {
  const scope = parseResourcesCollectionId(objectId);

  const request = buildScopedResourcesRequest(scope, `id =~ '${escapeKql(elementKey)}'`);
  const row = await queryOne(client, request, 'collection element', `${objectId}/${elementKey}`);

  return toResource(row) as unknown as { [key: string]: object };
}

// ==================== Documents ====================

/** Wrap a value in the `{ value }` document-field envelope, skipping undefined. */
function documentField(value: unknown): object | undefined {
  return value === undefined ? undefined : { value };
}

function pruneUndefined(document: Record<string, object | undefined>): { [key: string]: object } {
  const result: { [key: string]: object } = {};
  for (const [key, value] of Object.entries(document)) {
    if (value !== undefined) {
      result[key] = value;
    }
  }
  return result;
}

function toContainerDocument(container: ResourceContainer): { [key: string]: object } {
  return pruneUndefined({
    id: documentField(container.id),
    name: documentField(container.name),
    type: documentField(container.type),
    tenantId: documentField(container.tenantId),
    location: documentField(container.location),
    subscriptionId: documentField(container.subscriptionId),
    resourceGroup: documentField(container.resourceGroup),
    properties: documentField(container.properties),
    tags: documentField(container.tags),
  });
}

export async function getSubscriptionDocumentData(
  client: AzureResourceGraphClient,
  objectId: string
): Promise<{ [key: string]: object }> {
  const subscriptionId = objectId.slice('sub_'.length);

  const row = await queryOne(
    client,
    {
      query: `ResourceContainers | where type =~ '${SUBSCRIPTION_TYPE}' and subscriptionId =~ '${escapeKql(subscriptionId)}'`,
      subscriptions: [subscriptionId],
    },
    'subscription',
    objectId
  );

  return toContainerDocument(toResourceContainer(row));
}

export async function getResourceGroupDocumentData(
  client: AzureResourceGraphClient,
  objectId: string
): Promise<{ [key: string]: object }> {
  const { subscriptionId, resourceGroupName } = parseResourceGroupId(objectId);

  const row = await queryOne(
    client,
    {
      query: `ResourceContainers | where type =~ '${RESOURCE_GROUP_TYPE}' and name =~ '${escapeKql(resourceGroupName)}'`,
      subscriptions: [subscriptionId],
    },
    'resource group',
    objectId
  );

  return toContainerDocument(toResourceContainer(row));
}

export async function getManagementGroupDocumentData(
  client: AzureResourceGraphClient,
  objectId: string
): Promise<{ [key: string]: object }> {
  const managementGroupName = objectId.slice('mg_'.length);

  const row = await queryOne(
    client,
    {
      query: `ResourceContainers | where type =~ '${MANAGEMENT_GROUP_TYPE}' and name =~ '${escapeKql(managementGroupName)}'`,
    },
    'management group',
    objectId
  );

  return toContainerDocument(toResourceContainer(row));
}

export async function getResourceDocumentData(
  client: AzureResourceGraphClient,
  objectId: string
): Promise<{ [key: string]: object }> {
  const armId = objectId.slice('res_'.length);

  const row = await queryOne(
    client,
    { query: `Resources | where id =~ '${escapeKql(armId)}'` },
    'resource',
    objectId
  );

  const resource = toResource(row);

  return pruneUndefined({
    id: documentField(resource.id),
    name: documentField(resource.name),
    type: documentField(resource.type),
    tenantId: documentField(resource.tenantId),
    kind: documentField(resource.kind),
    location: documentField(resource.location),
    resourceGroup: documentField(resource.resourceGroup),
    subscriptionId: documentField(resource.subscriptionId),
    managedBy: documentField(resource.managedBy),
    ...(resource.sku && {
      sku: pruneUndefined({
        name: documentField(resource.sku.name),
        tier: documentField(resource.sku.tier),
        size: documentField(resource.sku.size),
        family: documentField(resource.sku.family),
        capacity: documentField(resource.sku.capacity),
      }),
    }),
    ...(resource.plan && {
      plan: pruneUndefined({
        name: documentField(resource.plan.name),
        publisher: documentField(resource.plan.publisher),
        product: documentField(resource.plan.product),
        promotionCode: documentField(resource.plan.promotionCode),
        version: documentField(resource.plan.version),
      }),
    }),
    ...(resource.identity && {
      identity: pruneUndefined({
        type: documentField(resource.identity.type),
        principalId: documentField(resource.identity.principalId),
        tenantId: documentField(resource.identity.tenantId),
        userAssignedIdentities: documentField(resource.identity.userAssignedIdentities),
      }),
    }),
    ...(resource.extendedLocation && {
      extendedLocation: pruneUndefined({
        name: documentField(resource.extendedLocation.name),
        type: documentField(resource.extendedLocation.type),
      }),
    }),
    zones: documentField(resource.zones),
    properties: documentField(resource.properties),
    tags: documentField(resource.tags),
  });
}

// ==================== Functions ====================

/** Unwrap a function input that may arrive `{ value }`-enveloped or plain. */
function unwrapInput(input: unknown): unknown {
  if (input && typeof input === 'object' && 'value' in (input as Record<string, unknown>)) {
    return (input as Record<string, unknown>).value;
  }
  return input;
}

function unwrapStringArray(input: unknown): string[] | undefined {
  const value = unwrapInput(input);
  if (value === undefined || value === null) {
    return undefined;
  }
  if (Array.isArray(value)) {
    return value.map(String);
  }
  return [String(value)];
}

export async function invokeArgQueryFunction(
  client: AzureResourceGraphClient,
  requestBody?: { [key: string]: object }
): Promise<{ [key: string]: object }> {
  const query = unwrapInput(requestBody?.query);
  if (typeof query !== 'string' || query.trim().length === 0) {
    throw new InvalidInputError('query', 'A non-empty KQL query string is required');
  }

  const subscriptions = unwrapStringArray(requestBody?.subscriptions);
  const managementGroups = unwrapStringArray(requestBody?.managementGroups);
  const pageSize = unwrapInput(requestBody?.pageSize);
  const skipToken = unwrapInput(requestBody?.skipToken);

  const options: ArgQueryOptions = {};
  if (typeof skipToken === 'string' && skipToken.length > 0) {
    options.$skipToken = skipToken;
  } else if (typeof pageSize === 'number' && Number.isFinite(pageSize)) {
    options.$top = Math.min(Math.max(Math.trunc(pageSize), 1), MAX_PAGE_SIZE);
  }

  const request: ArgQueryRequest = { query, options };
  if (subscriptions?.length) {
    request.subscriptions = subscriptions;
  }
  if (managementGroups?.length) {
    request.managementGroups = managementGroups;
  }

  const response = await client.query(request);

  return pruneUndefined({
    data: { value: response.data },
    totalRecords: documentField(response.totalRecords ?? 0),
    count: documentField(response.count ?? response.data.length),
    skipToken: documentField(response.$skipToken),
  });
}
