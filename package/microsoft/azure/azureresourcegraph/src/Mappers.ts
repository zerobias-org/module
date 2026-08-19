/**
 * Mappers — raw Azure Resource Graph objectArray rows → generated model objects.
 *
 * ARG returns camelCase column names that map 1:1 onto the api.yml schemas;
 * mapping is therefore mostly null→undefined normalization plus validation of
 * the identity fields (id / name / type) every row must carry.
 */

import {
  Resource,
  ResourceSku,
  ResourcePlan,
  ResourceIdentity,
  ExtendedLocation,
  ResourceContainer
} from '../generated/model/index.js';
import { mapWith, optional, ensureProperties } from './util.js';

export function toResourceSku(raw: Record<string, unknown>): ResourceSku {
  return {
    name: optional<string>(raw.name),
    tier: optional<string>(raw.tier),
    size: optional<string>(raw.size),
    family: optional<string>(raw.family),
    capacity: optional<number>(raw.capacity),
  };
}

export function toResourcePlan(raw: Record<string, unknown>): ResourcePlan {
  return {
    name: optional<string>(raw.name),
    publisher: optional<string>(raw.publisher),
    product: optional<string>(raw.product),
    promotionCode: optional<string>(raw.promotionCode),
    version: optional<string>(raw.version),
  };
}

export function toResourceIdentity(raw: Record<string, unknown>): ResourceIdentity {
  return {
    type: optional<string>(raw.type),
    principalId: optional<string>(raw.principalId),
    tenantId: optional<string>(raw.tenantId),
    userAssignedIdentities: optional<{ [key: string]: object }>(raw.userAssignedIdentities),
  };
}

export function toExtendedLocation(raw: Record<string, unknown>): ExtendedLocation {
  return {
    name: optional<string>(raw.name),
    type: optional<string>(raw.type),
  };
}

export function toResource(raw: Record<string, unknown>): Resource {
  ensureProperties(raw, ['id', 'name', 'type']);

  return {
    id: String(raw.id),
    name: String(raw.name),
    type: String(raw.type),
    tenantId: optional<string>(raw.tenantId),
    kind: optional<string>(raw.kind),
    location: optional<string>(raw.location),
    resourceGroup: optional<string>(raw.resourceGroup),
    subscriptionId: optional<string>(raw.subscriptionId),
    managedBy: optional<string>(raw.managedBy),
    sku: mapWith(toResourceSku, raw.sku),
    plan: mapWith(toResourcePlan, raw.plan),
    identity: mapWith(toResourceIdentity, raw.identity),
    extendedLocation: mapWith(toExtendedLocation, raw.extendedLocation),
    zones: optional<string[]>(raw.zones),
    properties: optional<{ [key: string]: object }>(raw.properties),
    tags: optional<Record<string, string>>(raw.tags),
  };
}

export function toResourceContainer(raw: Record<string, unknown>): ResourceContainer {
  ensureProperties(raw, ['id', 'name', 'type']);

  return {
    id: String(raw.id),
    name: String(raw.name),
    type: String(raw.type),
    tenantId: optional<string>(raw.tenantId),
    location: optional<string>(raw.location),
    subscriptionId: optional<string>(raw.subscriptionId),
    resourceGroup: optional<string>(raw.resourceGroup),
    properties: optional<{ [key: string]: object }>(raw.properties),
    tags: optional<Record<string, string>>(raw.tags),
  };
}
