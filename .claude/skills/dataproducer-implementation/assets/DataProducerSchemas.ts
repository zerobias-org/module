// Template for src/impl/dataproducer/DataProducerSchemas.ts.
// Replace acme.acme / Organization / User with the module's coordinates and entities.
import { Schema } from '../../../generated/model/index.js';
import { Organization, User } from '../../../generated/model/index.js';
import { buildSchema } from './SchemaBuilder.js';

// One entry per exposed entity. IDs follow the canonical form in the interface
// package's documentation/SchemaIds.md; consumers store them and getSchema
// round-trips them.
export const SCHEMA_IDS = {
  ORGANIZATION: 'schema:shared:acme.acme.organization',
  USER: 'schema:shared:acme.acme.user',
} as const;

export const ORGANIZATION_SCHEMA: Schema = buildSchema(Organization, {
  schemaId: SCHEMA_IDS.ORGANIZATION,
  primaryKeys: ['id'], // ['sha'], ['number'], ['path'], ['name']: whatever is truly primary
  references: { owner: { schemaId: SCHEMA_IDS.USER } }, // property -> other schema
});

export const USER_SCHEMA: Schema = buildSchema(User, {
  schemaId: SCHEMA_IDS.USER,
  primaryKeys: ['id'],
});

const schemaMap: Record<string, Schema> = {
  [SCHEMA_IDS.ORGANIZATION]: ORGANIZATION_SCHEMA,
  [SCHEMA_IDS.USER]: USER_SCHEMA,
};

export function getSchemaById(schemaId: string): Schema | undefined {
  return schemaMap[schemaId];
}
