import { buildSchema as dataUtilsBuildSchema } from '@zerobias-org/data-utils/schema';
import type { TypeScriptSchemaConfig } from '@zerobias-org/data-utils/types';
import { Schema } from '../../../generated/model/index.js';
import { findSpecPath } from './findSpec.js';

const SPEC_PATH = findSpecPath();

export type SchemaConfig = Omit<TypeScriptSchemaConfig, 'modelClass'>;

export function buildSchema(
  modelClass: TypeScriptSchemaConfig['modelClass'],
  config: SchemaConfig
): Schema {
  return dataUtilsBuildSchema(modelClass, { ...config, openApiSpec: SPEC_PATH }) as unknown as Schema;
}
