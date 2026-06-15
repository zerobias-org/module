import { toEnum, map } from '@zerobias-org/util-hub-module-utils';
import { mapWith, ensureProperties, optional } from './util.js';
import {
  Pet,
  PetStatus,
  Category,
  Tag,
  Order,
  PetstoreUser,
} from '../generated/model/index.js';

// ============================================================================
// HELPER FUNCTIONS (NON-EXPORTED) — Declared before exported mappers
// ============================================================================

/**
 * Coerce Petstore int64 IDs to TypeScript string.
 * Petstore wire returns numbers (e.g., petId=10); api.yml types ID fields as
 * `string`; consumers see strings. Preserves null/undefined for optional IDs.
 */
function int64ToString(id: number | bigint | string | undefined | null): string | undefined {
  if (id === null || id === undefined) {
    return undefined;
  }
  return String(id);
}

/**
 * Maps raw Category to Category — 2 fields parity with generated Category.ts.
 */
function toCategoryHelper(raw: any): Category {
  const output: Category = {
    id: int64ToString(raw.id),
    name: optional(raw.name),
  };
  return output;
}

/**
 * Maps raw Tag to Tag — 2 fields parity with generated Tag.ts.
 */
function toTagHelper(raw: any): Tag {
  const output: Tag = {
    id: int64ToString(raw.id),
    name: optional(raw.name),
  };
  return output;
}

// ============================================================================
// EXPORTED MAPPERS — Field-count parity with generated/model interfaces.
// ============================================================================

/**
 * Maps raw Petstore /pet response to Pet — 6 fields parity with Pet.ts:
 * name (required), photoUrls (required), id, category, tags, status.
 *
 * status is an EnumValue (PetStatusDef) returned by toEnum which looks up the
 * generated PetStatus enum (lowercase wire values).
 */
export function toPet(raw: any): Pet {
  // 1. Validate required fields per api.yml
  ensureProperties(raw, ['name', 'photoUrls']);

  // 2. Build output with 6-field parity vs generated/model/Pet.ts
  const output: Pet = {
    name: raw.name,
    photoUrls: Array.isArray(raw.photoUrls) ? raw.photoUrls : [],
    id: int64ToString(raw.id),
    category: mapWith(toCategoryHelper, raw.category),
    tags: Array.isArray(raw.tags) ? raw.tags.map(toTagHelper) : undefined,
    status: toEnum(PetStatus, raw.status) as Pet['status'],
  };
  return output;
}

/**
 * Maps raw Category — 2 fields (id, name).
 */
export function toCategory(raw: any): Category {
  return toCategoryHelper(raw);
}

/**
 * Maps raw Tag — 2 fields (id, name).
 */
export function toTag(raw: any): Tag {
  return toTagHelper(raw);
}

/**
 * Maps raw Petstore /store/order response to Order — 6 fields parity with
 * Order.ts: id, petId, quantity, shipDate, status, complete.
 */
export function toOrder(raw: any): Order {
  const output: Order = {
    id: int64ToString(raw.id),
    petId: int64ToString(raw.petId),
    quantity: optional(raw.quantity),
    shipDate: raw.shipDate ? map(Date as any, raw.shipDate) : undefined,
    status: toEnum(Order.StatusEnum, raw.status) as Order.StatusEnumDef | undefined,
    complete: optional(raw.complete),
  };
  return output;
}

/**
 * Maps raw Petstore /store/inventory response to the typed status->count map.
 * Inventory is `additionalProperties: { type: integer }`; codegen represents it
 * inline as `{ [key: string]: number }` (no model class). This mapper passes
 * through with int coercion to enforce the typed contract.
 */
export function toInventory(raw: any): { [key: string]: number } {
  const out: { [key: string]: number } = {};
  for (const [k, v] of Object.entries(raw || {})) {
    out[k] = typeof v === 'number' ? v : Number(v);
  }
  return out;
}

/**
 * Maps raw Petstore /user response to PetstoreUser — 8 fields parity with
 * PetstoreUser.ts: id, username, firstName, lastName, email, password, phone,
 * userStatus.
 */
export function toPetstoreUser(raw: any): PetstoreUser {
  const output: PetstoreUser = {
    id: int64ToString(raw.id),
    username: optional(raw.username),
    firstName: optional(raw.firstName),
    lastName: optional(raw.lastName),
    email: optional(raw.email),
    password: optional(raw.password),
    phone: optional(raw.phone),
    userStatus: optional(raw.userStatus),
  };
  return output;
}
