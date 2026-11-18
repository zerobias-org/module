// Template for src/impl/dataproducer/DataProducerMappers.ts.
// Mappers are free exported functions, imported by DataProducerImpl as
// `import * as Mappers from './DataProducerMappers.js'`.
//
// Three signature families:
//   1. object getter     getOrganizationObject(mod, objectId): Promise<DataProducerObject>
//   2. children / collection filler: mutates `results`, returns void
//                        getOrgChildren(mod, objectId, results): Promise<void>
//   3. pure builder      buildRootObject(): DataProducerObject   (static structure, no API call, not async)
import { PagedResults } from '@zerobias-org/types-core-js';
import type { DataProducerObject } from '../../../generated/model/index.js';
import { ObjectClass } from '../../../generated/model/index.js';
import type { AcmeImpl } from '../../AcmeImpl.js';
import { SCHEMA_IDS } from './DataProducerSchemas.js';

// Objects are typed literals with named fields, not positional constructor calls.
export function buildRootObject(): DataProducerObject {
  return {
    id: '/',
    name: '/', // the interface requires id == name == '/' on the root
    description: 'Acme resource hierarchy',
    path: ['/'],
    tags: ['acme'],
    objectClass: [ObjectClass.Container],
  };
}

export async function getOrganizationsChildren(
  mod: AcmeImpl,
  results: PagedResults<DataProducerObject>
): Promise<void> {
  // Pass the paging inputs straight to the module's own wrapped tag producer.
  const orgs = await mod.getOrganizationApi().listOrganizations(
    results.pageNumber,
    results.pageSize
  );

  results.items = orgs.items.map((org): DataProducerObject => ({
    id: `org_${org.code}`,
    name: org.name ?? '',
    description: org.description,
    path: ['/', 'organizations', org.code],
    tags: ['organization'],
    created: org.created,
    modified: org.modified,
    objectClass: [ObjectClass.Container, ObjectClass.Document],
    documentSchema: SCHEMA_IDS.ORGANIZATION,
  }));
}

// ---- Collections: one registry, everything else derived from it ----
// Child lists, ID parsing, element dispatch, schema lookup and sizes all read
// this array, so a collection is declared exactly once.
interface CollectionDef {
  type: string;     // token used inside collection_{type}_{scope} IDs
  name: string;     // display name
  schemaId: string; // element schema
  fetch: (mod: AcmeImpl, scope: string[], results: PagedResults<Record<string, unknown>>) => Promise<void>;
}

export const COLLECTIONS: readonly CollectionDef[] = [
  { type: 'members', name: 'Members', schemaId: SCHEMA_IDS.USER, fetch: fetchMembers },
  // ...
];

// Collection elements are plain literals shaped by the element schema, with the
// parent coordinates denormalized onto every element so that a flat element
// traces back to its parent.
async function fetchMembers(
  mod: AcmeImpl,
  scope: string[],
  results: PagedResults<Record<string, unknown>>
): Promise<void> {
  const [orgCode] = scope;
  const members = await mod.getMemberApi().listMembers(orgCode, results.pageNumber, results.pageSize);

  results.items = members.items.map((m) => ({
    organizationCode: orgCode, // denormalized parent coordinate(s)
    id: m.id,
    name: m.name,
    role: m.role,
  }));
}
