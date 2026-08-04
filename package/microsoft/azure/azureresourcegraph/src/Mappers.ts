import { Resource, ResourceContainer } from '../generated/model/index.js';

/**
 * Maps an Azure Resource Graph `Resources` row (options.resultFormat
 * objectArray) to the module Resource model. Rows arrive as flat objects keyed
 * by ARG column name.
 */
export function toResource(row: Record<string, unknown>): Resource {
  return {
    id: row.id,
    name: row.name,
    type: row.type,
    tenantId: row.tenantId,
    kind: row.kind || undefined,
    location: row.location,
    resourceGroup: row.resourceGroup,
    subscriptionId: row.subscriptionId,
    managedBy: row.managedBy || undefined,
    sku: row.sku ?? undefined,
    plan: row.plan ?? undefined,
    properties: row.properties ?? undefined,
    tags: row.tags ?? undefined,
    identity: row.identity ?? undefined,
    zones: row.zones ?? undefined,
    extendedLocation: row.extendedLocation ?? undefined,
  } as Resource;
}

/**
 * Maps an Azure Resource Graph `ResourceContainers` row to the module
 * ResourceContainer model. Covers microsoft.resources/subscriptions,
 * microsoft.resources/subscriptions/resourcegroups and
 * microsoft.management/managementgroups rows.
 */
export function toResourceContainer(row: Record<string, unknown>): ResourceContainer {
  return {
    id: row.id,
    name: row.name,
    type: row.type,
    tenantId: row.tenantId,
    location: row.location || undefined,
    resourceGroup: row.resourceGroup || undefined,
    subscriptionId: row.subscriptionId || undefined,
    managedBy: row.managedBy || undefined,
    properties: row.properties ?? undefined,
    tags: row.tags ?? undefined,
  } as ResourceContainer;
}
